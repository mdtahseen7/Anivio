$ErrorActionPreference = 'Stop'

if ($env:ANILIST_CLIENT_ID -notmatch '^[0-9]+$') {
    throw 'Configure ANILIST_CLIENT_ID for the Windows build.'
}
$address = $null
if (![Uri]::TryCreate($env:ANIVIO_SUPABASE_URL, [UriKind]::Absolute, [ref]$address) -or
    $address.Scheme -ne 'https' -or $address.UserInfo) {
    throw 'Configure the HTTPS Anivio Supabase endpoint.'
}
$publicKey = $env:ANIVIO_SUPABASE_ANON_KEY
if ($publicKey -notmatch '^sb_publishable_[A-Za-z0-9_-]+$') {
    if ($publicKey -notmatch '^eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$') {
        throw 'Expected a Supabase client key.'
    }
    $payload = $publicKey.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $payload = $payload.PadRight([int]([Math]::Ceiling($payload.Length / 4.0) * 4), '=')
    $role = ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json).role
    if ($role -ne 'anon') { throw 'Use a Supabase anon or publishable key for this build.' }
}

$properties = Get-Content composeApp/Configuration/DesktopVersion.properties -Raw | ConvertFrom-StringData
$version = $properties.VERSION_NAME
$msiVersion = $version.Split('-')[0] -replace '^0\.', '1.'
$packageArguments = @()
if ($env:RELEASE_VERSION) {
    $version = $env:RELEASE_VERSION
    $versionCode = [int]$env:RELEASE_VERSION_CODE
    if ($version -notmatch '^\d+\.\d+\.\d+(?:-beta\.\d+)?$' -or $versionCode -le 0) {
        throw 'Invalid shared release version or code.'
    }
    # Windows Installer compares three numeric fields. Use the monotonic release
    # code so consecutive betas and their stable promotion also upgrade in place.
    $msiVersion = "3.$($versionCode -shr 16).$($versionCode -band 65535)"
    if (($versionCode -shr 16) -gt 255) {
        throw 'The shared release code exceeds the Windows Installer version range.'
    }
    $packageArguments = @(
        "-Pnuvio.desktop.versionName=$version"
        "-Pnuvio.desktop.versionCode=$versionCode"
        "-Pnuvio.desktop.windowsPackageVersion=$msiVersion"
    )
}

git lfs pull --include="composeApp/src/desktopMain/native/windows/runtime/**,composeApp/src/desktopMain/resources/torrserver/windows-amd64/TorrServer.exe" --exclude=""
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$mpv = Get-Item composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll
if ($mpv.Length -lt 1000000) { throw 'libmpv is still a Git LFS pointer.' }
if (!(Test-Path local.properties)) { New-Item -ItemType File -Path local.properties | Out-Null }
.\gradlew.bat ':composeApp:packageMsi' "-Pnuvio.webview2.dir=$env:WEBVIEW2_SDK" '-Pcompose.desktop.packaging.checkJdkVendor=false' @packageArguments --no-configuration-cache --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$msi = @(Get-ChildItem composeApp/build/compose/release-msis -Filter '*.msi' -File)
if ($msi.Count -ne 1) { throw 'Expected exactly one Windows MSI.' }
$msi = $msi[0]
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($msi.FullName, 0)
$query = $database.OpenView('SELECT `Property`, `Value` FROM `Property`')
$query.Execute()
$identity = @{}
while ($record = $query.Fetch()) { $identity[$record.StringData(1)] = $record.StringData(2) }
if ($identity.ProductName -ne 'Anivio' -or $identity.ProductVersion -ne $msiVersion -or
    $identity.UpgradeCode -ne '{206BCB4C-0FA5-57D9-9415-A8B1270B96B8}') {
    throw 'The MSI version or upgrade identity does not match Anivio.'
}
$query.Close()

$output = (New-Item -ItemType Directory -Path build/windows-release -Force).FullName
$compiler = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Inno Setup 6/ISCC.exe'
& $compiler "/DPayload=$($msi.FullName)" "/DOutputPath=$output" "/DAppVersion=$version" "/DPackageVersion=$msiVersion" composeApp/installer/windows/Anivio.iss
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$asset = 'Anivio-Windows-x64.exe'
$checksum = (Get-FileHash (Join-Path $output $asset) -Algorithm SHA256).Hash.ToLowerInvariant()
"$checksum  $asset" | Set-Content (Join-Path $output 'SHA256SUMS.txt')
if ($env:GITHUB_STEP_SUMMARY) {
    "Anivio for Windows $version · SHA-256: $checksum" >> $env:GITHUB_STEP_SUMMARY
}
