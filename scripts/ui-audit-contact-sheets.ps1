param(
    [Parameter(Mandatory = $true)][string]$InputDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$PathPattern = '*',
    [ValidateSet('all', 'portrait', 'landscape')][string]$Orientation = 'all'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$files = @(Get-ChildItem -LiteralPath $InputDirectory -Recurse -Filter '*.png' |
    Where-Object FullName -Like $PathPattern | Sort-Object FullName)
if ($Orientation -ne 'all') {
    $files = @($files | Where-Object {
        $probe = [Drawing.Image]::FromFile($_.FullName)
        try { if ($Orientation -eq 'landscape') { $probe.Width -gt $probe.Height } else { $probe.Height -ge $probe.Width } }
        finally { $probe.Dispose() }
    })
}
$perSheet = if ($Orientation -eq 'landscape') { 4 } else { 6 }
$columns = if ($Orientation -eq 'landscape') { 2 } else { 3 }
$cellWidth = if ($Orientation -eq 'landscape') { 864 } else { 432 }
$imageHeight = if ($Orientation -eq 'landscape') { 389 } else { 960 }
$cellHeight = $imageHeight + 45
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$files.FullName | Set-Content (Join-Path $OutputDirectory 'index.txt') -Encoding utf8
for ($offset = 0; $offset -lt $files.Count; $offset += $perSheet) {
    $canvas = [Drawing.Bitmap]::new($columns * $cellWidth, 2 * $cellHeight)
    $graphics = [Drawing.Graphics]::FromImage($canvas)
    $font = [Drawing.Font]::new('Arial', 10)
    try {
        $graphics.Clear([Drawing.Color]::LightGray)
        for ($j = 0; $j -lt $perSheet -and $offset + $j -lt $files.Count; $j++) {
            $file = $files[$offset + $j]
            $x = ($j % $columns) * $cellWidth
            $y = [math]::Floor($j / $columns) * $cellHeight
            $graphics.DrawString("$($offset + $j) $($file.BaseName)", $font,
                [Drawing.Brushes]::Black, [Drawing.RectangleF]::new($x + 4, $y + 2, $cellWidth - 8, 43))
            $original = [Drawing.Image]::FromFile($file.FullName)
            try {
                $ratio = [math]::Min($cellWidth / $original.Width, $imageHeight / $original.Height)
                $graphics.DrawImage($original, [int]$x, [int]($y + 45),
                    [int]($original.Width * $ratio), [int]($original.Height * $ratio))
            } finally { $original.Dispose() }
        }
        $canvas.Save((Join-Path $OutputDirectory ('sheet-{0:D3}.png' -f ($offset / $perSheet))), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $graphics.Dispose(); $canvas.Dispose() }
}
Write-Output "$($files.Count) originais indexados. Pranchas são auxiliares; conferir originais para texto/contraste."
