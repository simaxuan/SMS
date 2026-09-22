$ErrorActionPreference = "Stop"
$tools = $PSScriptRoot
$srcCs = Join-Path $tools 'ServerLauncher.cs'
$ico = Join-Path $tools 'app.ico'
$outExe = Join-Path $tools 'ExamScoreServerLauncher.exe'

$csc = $null
$vw = 'C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe'
if (Test-Path $vw) {
  $found = & $vw -latest -products '*' -requires Microsoft.Component.MSBuild -find '**\csc.exe' 2>$null
  if ($found) { $csc = $found }
}
if (-not $csc) {
  $roslyn = 'C:/Program Files/Microsoft Visual Studio/2022/Community/MSBuild/Current/Bin/Roslyn/csc.exe'
  if (Test-Path $roslyn) { $csc = $roslyn }
}
if (-not $csc) { Write-Output 'NO_CSC'; exit 7 }

$fw = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319'
if (-not (Test-Path (Join-Path $fw 'System.Windows.Forms.dll'))) {
  $fw = 'C:\Windows\Microsoft.NET\Framework\v4.0.30319'
}
$refs = "/r:`"$fw\System.Windows.Forms.dll`" /r:`"$fw\System.Drawing.dll`" /r:`"$fw\System.dll`" /r:`"$fw\System.Core.dll`" /r:`"$fw\System.IO.Compression.dll`" /r:`"$fw\System.IO.Compression.FileSystem.dll`""
$arg = "/target:winexe /langversion:7.3 /win32icon:`"$ico`" $refs /out:`"$outExe`" `"$srcCs`""
$psi = New-Object System.Diagnostics.ProcessStartInfo($csc, $arg)
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$p = [System.Diagnostics.Process]::Start($psi)
$p.WaitForExit()
$co = $p.StandardOutput.ReadToEnd()
$ce = $p.StandardError.ReadToEnd()
Write-Output ("RC=" + $p.ExitCode)
Write-Output "STDOUT>"
Write-Output $co
Write-Output "STDERR>"
Write-Output $ce
Write-Output ("EXE_EXISTS=" + (Test-Path $outExe))
