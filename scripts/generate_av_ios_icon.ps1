$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$assetRoot = Join-Path $repoRoot 'iosApp/iosApp/Assets.xcassets'
$original = Join-Path $assetRoot 'AppIcon.appiconset'
$legacy = Join-Path $assetRoot 'AppIconOriginal.appiconset'
New-Item -ItemType Directory -Path $legacy -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $original 'Contents.json') -Destination (Join-Path $legacy 'Contents.json')
Copy-Item -LiteralPath (Join-Path $original 'app-icon-1024.png') -Destination (Join-Path $legacy 'app-icon-1024.png')
$newIcon = Join-Path $assetRoot 'AppIconAV.appiconset'
New-Item -ItemType Directory -Path $newIcon -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $original 'Contents.json') -Destination (Join-Path $newIcon 'Contents.json')
$source = [System.Drawing.Image]::FromFile((Join-Path $repoRoot 'composeApp/src/commonMain/composeResources/drawable/app_icon_av.jpg'))
$output = New-Object System.Drawing.Bitmap(1024,1024)
$graphics = [System.Drawing.Graphics]::FromImage($output)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(2,2,0))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $attributes = New-Object System.Drawing.Imaging.ImageAttributes
    $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    try {
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0,0,1024,1024), 0, 0, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
    } finally { $attributes.Dispose() }
    $output.Save((Join-Path $newIcon 'app-icon-1024.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $output.Dispose(); $source.Dispose() }
