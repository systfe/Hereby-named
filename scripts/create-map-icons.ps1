# Reproducible, original 16px pixel icons. No Xaero assets are copied.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$bitmap = [Drawing.Bitmap]::new(256,256)
function Pixel([int]$icon,[int]$x,[int]$y) { $bitmap.SetPixel($icon*16+$x,$y,[Drawing.Color]::FromArgb(255,245,245,245)) }
function DrawH([int]$i,[int]$x,[int]$y,[int]$w) { for($n=0;$n -lt $w;$n++) { Pixel $i ($x+$n) $y } }
function DrawV([int]$i,[int]$x,[int]$y,[int]$height) { for($n=0;$n -lt $height;$n++) { Pixel $i $x ($y+$n) } }
function Box([int]$i,[int]$x,[int]$y,[int]$w,[int]$height) { DrawH $i $x $y $w; DrawH $i $x ($y+$height-1) $w; DrawV $i $x $y $height; DrawV $i ($x+$w-1) $y $height }
function Region([int]$i) { DrawH $i 2 2 5; DrawV $i 2 2 11; DrawH $i 2 12 5; DrawH $i 9 2 4; DrawV $i 12 2 5; DrawH $i 9 6 4 }
function Layers([int]$i) { DrawH $i 3 2 9; DrawH $i 3 6 9; DrawH $i 3 10 5; Pixel $i 2 3; Pixel $i 12 3; DrawH $i 3 4 9; Pixel $i 2 7; Pixel $i 12 7; DrawH $i 3 8 5; Pixel $i 2 11; DrawH $i 3 12 4 }
function Plus([int]$i) { DrawH $i 8 11 7; DrawV $i 11 8 7 }
function Minus([int]$i) { DrawH $i 8 11 7; DrawH $i 8 12 7 }
# Boundary with pencil: enter/leave edit mode.
Region 0
for($k=0;$k -lt 7;$k++) { Pixel 0 (6+$k) (12-$k); Pixel 0 (7+$k) (12-$k) }
Pixel 0 5 14; Pixel 0 6 13
Layers 1
# Visibility: stacked all / single layer / crossed out.
Layers 2
Box 3 3 4 10 8
DrawH 3 5 6 6
Layers 4
for($k=0;$k -lt 12;$k++) { Pixel 4 (2+$k) (13-$k) }
Region 5; Plus 5
# Pencil with two-pixel stroke and nib.
for($k=0;$k -lt 9;$k++) { Pixel 6 (3+$k) (11-$k); Pixel 6 (4+$k) (11-$k); Pixel 6 (4+$k) (12-$k) }
Pixel 6 2 14; Pixel 6 3 13; Pixel 6 12 1; Pixel 6 13 2
Region 7; Minus 7
Layers 8; Plus 8
Layers 9; Minus 9
# Help circle, pixel question mark.
DrawH 10 5 1 6; DrawH 10 5 14 6; DrawV 10 1 5 6; DrawV 10 14 5 6
Pixel 10 3 2; Pixel 10 2 3; Pixel 10 12 2; Pixel 10 13 3
Pixel 10 2 12; Pixel 10 3 13; Pixel 10 13 12; Pixel 10 12 13
DrawH 10 6 4 4; Pixel 10 5 5; DrawV 10 10 5 2; DrawH 10 8 7 2; DrawV 10 7 8 2; Pixel 10 7 12
# Small gear: automatic-region settings.
Box 11 4 4 8 8
Box 11 6 6 4 4
DrawH 11 6 2 4; DrawH 11 6 13 4
DrawV 11 2 6 4; DrawV 11 13 6 4
Pixel 11 3 3; Pixel 11 12 3; Pixel 11 3 12; Pixel 11 12 12
$target = Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources/assets/hereby_named/textures/gui/map_tools.png'
[IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
try { $bitmap.Save($target,[Drawing.Imaging.ImageFormat]::Png) } finally { $bitmap.Dispose() }
