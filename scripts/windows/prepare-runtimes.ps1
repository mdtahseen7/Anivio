param(
    [string]$RuntimeDirectory = (Join-Path $PSScriptRoot '../../build/desktop-tools')
)

$ErrorActionPreference = 'Stop'
$runtimeRoot = [IO.Path]::GetFullPath($RuntimeDirectory)
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null

function Get-PinnedFile([string]$Url, [string]$Destination, [string]$Sha256) {
    if ((Test-Path -LiteralPath $Destination) -and
        (Get-FileHash -LiteralPath $Destination).Hash -eq $Sha256) { return }
    $partial = "$Destination.part"
    & curl.exe --fail --location --silent --show-error --retry 2 --max-time 900 --output $partial $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed for $Url" }
    if ((Get-FileHash -LiteralPath $partial).Hash -ne $Sha256) {
        throw "Checksum mismatch for $Url"
    }
    Move-Item -LiteralPath $partial -Destination $Destination -Force
}

$mpvArchive = Join-Path $runtimeRoot 'libmpv.zip'
Get-PinnedFile 'https://github.com/mpv-player/mpv/releases/download/v0.42.0/libmpv-v0.42.0-x86_64-w64-mingw32-gpl.zip' $mpvArchive 'b99e0d183024ae479d1b36a93fa262c0297dfb025b99715b98da377ffb6c2913'
$mpvOutput = Join-Path $runtimeRoot 'mpv'
Expand-Archive -LiteralPath $mpvArchive -DestinationPath $mpvOutput -Force
$mpvDll = @(Get-ChildItem -LiteralPath $mpvOutput -Filter 'libmpv-2.dll' -File -Recurse)
if ($mpvDll.Count -ne 1) { throw 'Expected one libmpv-2.dll in the runtime archive.' }

$webviewArchive = Join-Path $runtimeRoot 'webview2.zip'
Get-PinnedFile 'https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.4078.44/microsoft.web.webview2.1.0.4078.44.nupkg' $webviewArchive 'dc4d1d9168df26b830398303e50210b6e1729f6ce5a7ac69d2c766852f489962'
$webviewOutput = Join-Path $runtimeRoot 'webview2'
Expand-Archive -LiteralPath $webviewArchive -DestinationPath $webviewOutput -Force
if (!(Test-Path -LiteralPath (Join-Path $webviewOutput 'build/native/x64/WebView2Loader.dll.lib'))) {
    throw 'WebView2 SDK is missing its Windows x64 loader.'
}

$torrServer = Join-Path $runtimeRoot 'TorrServer.exe'
Get-PinnedFile 'https://github.com/YouROK/TorrServer/releases/download/MatriX.145/TorrServer-windows-amd64.exe' $torrServer '407532e5da6e7676ad274489b322d28f8f1ae7d2e12032e9d6acfadd9b945006'

@{
    LibMpvDirectory = $mpvDll[0].DirectoryName
    WebView2Directory = $webviewOutput
    TorrServerExecutable = $torrServer
}
