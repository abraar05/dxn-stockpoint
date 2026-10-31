# Generates the PNG app icons from the same geometry as docs/icons/icon.svg.
# Requires Windows PowerShell (System.Drawing). Run from the repo root:
#
#   powershell -ExecutionPolicy Bypass -File scripts/make-icons.ps1
#
param(
    [string]$OutDir = "docs/icons"
)

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$target = Join-Path $root $OutDir
if (-not (Test-Path $target)) { New-Item -ItemType Directory -Path $target | Out-Null }

$dark = [System.Drawing.ColorTranslator]::FromHtml("#054A29")
$light = [System.Drawing.ColorTranslator]::FromHtml("#0A723D")
$gold = [System.Drawing.ColorTranslator]::FromHtml("#D4AF37")
$goldLight = [System.Drawing.ColorTranslator]::FromHtml("#F2DFA0")
$goldDeep = [System.Drawing.ColorTranslator]::FromHtml("#A9790A")

function New-StockpointIcon {
    param([int]$Size, [string]$Path, [double]$Scale = 1.0)

    $bmp = New-Object System.Drawing.Bitmap($Size, $Size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear($dark)

    # Background: diagonal forest-green gradient.
    $rect = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $light, $dark, 45.0)
    $g.FillRectangle($bg, $rect)

    $c = $Size / 2.0
    $ring = $Size * 0.297 * $Scale
    $stroke = [Math]::Max(2.0, $Size * 0.0234 * $Scale)

    # Gold ring.
    $ringPen = New-Object System.Drawing.Pen($gold, $stroke)
    $g.DrawEllipse($ringPen, [single]($c - $ring), [single]($c - $ring), [single]($ring * 2), [single]($ring * 2))

    # Gold diamond (rotated square).
    $d = $Size * 0.258 * $Scale
    $diamond = @(
        (New-Object System.Drawing.PointF([single]$c, [single]($c - $d))),
        (New-Object System.Drawing.PointF([single]($c + $d), [single]$c)),
        (New-Object System.Drawing.PointF([single]$c, [single]($c + $d))),
        (New-Object System.Drawing.PointF([single]($c - $d), [single]$c))
    )
    $diamondPen = New-Object System.Drawing.Pen($gold, [float]([Math]::Max(2.0, $Size * 0.035 * $Scale)))
    $diamondPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawPolygon($diamondPen, $diamond)

    # Inner translucent diamond.
    $inner = $Size * 0.1365 * $Scale
    $innerDiamond = @(
        (New-Object System.Drawing.PointF([single]$c, [single]($c - $inner))),
        (New-Object System.Drawing.PointF([single]($c + $inner), [single]$c)),
        (New-Object System.Drawing.PointF([single]$c, [single]($c + $inner))),
        (New-Object System.Drawing.PointF([single]($c - $inner), [single]$c))
    )
    $innerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, $goldLight))
    $g.FillPolygon($innerBrush, $innerDiamond)

    # Centre pearl.
    $pearl = $Size * 0.0586 * $Scale
    $pearlBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#F7EDC9"))
    $g.FillEllipse($pearlBrush, [single]($c - $pearl), [single]($c - $pearl), [single]($pearl * 2), [single]($pearl * 2))

    $maskBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

    $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    $bg.Dispose(); $ringPen.Dispose(); $diamondPen.Dispose(); $innerBrush.Dispose(); $pearlBrush.Dispose(); $maskBrush.Dispose()

    Write-Host ("  wrote {0} ({1}x{2})" -f $Path, $Size, $Size)
}

Write-Host "Generating DXN Stockpoint icons in $target"
New-StockpointIcon -Size 192 -Path (Join-Path $target "icon-192.png")
New-StockpointIcon -Size 512 -Path (Join-Path $target "icon-512.png")
# Maskable icons live inside the safe zone, so the mark is drawn smaller.
New-StockpointIcon -Size 512 -Path (Join-Path $target "icon-maskable-512.png") -Scale 0.78
New-StockpointIcon -Size 180 -Path (Join-Path $target "apple-touch-icon.png")
Write-Host "Done."
