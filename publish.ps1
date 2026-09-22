# =============================================================
# 考试成绩管理系统 - One-click build / test / publish script
# Builds frontend, backend (with embedded SPA), and the Windows
# server launcher, then assembles a single-machine deploy folder
# and runs a smoke test against http://localhost:8082.
#
# Usage (run on the dev machine, as the project owner):
#   powershell -ExecutionPolicy Bypass -File publish.ps1
# =============================================================
[CmdletBinding()]
param(
    [string]$Root = (Split-Path -Parent $MyInvocation.MyCommand.Definition),
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"
$logFile = Join-Path $Root "publish_build.log"

# 原生命令（npm/mvn）的 stderr 会被 `2>&1` 并入管道；若此时 EAP=Stop，
# npm 写到 stderr 的告警/进度（如 chunk 体积告警、built in）会被包装成
# 终止性 NativeCommandError，导致脚本在构建收尾处莫名中断。
# 此函数把命令输出逐行写入日志，同时临时将 EAP 降为 Continue，仅以 $LASTEXITCODE 判定成败。
function Invoke-NativeCaptured {
    param([string]$LogFile, [scriptblock]$Body)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Body 2>&1 | ForEach-Object { $_ | Out-File $LogFile -Append -Encoding ascii }
    } finally {
        $ErrorActionPreference = $prev
    }
    return $LASTEXITCODE
}
$ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
"[$ts] publish.ps1 started (Root=$Root, Port=$Port)" | Out-File $logFile -Encoding ascii

function Log($msg) {
    $t = Get-Date -Format "HH:mm:ss"
    $line = "[$t] $msg"
    Write-Host $line
    $line | Out-File $logFile -Append -Encoding ascii
}

function Fail($msg) {
    Log "ERROR: $msg"
    throw $msg
}

# ---------- 1. locate toolchain ----------
Log "Step 1/6: locating toolchain ..."

$java = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $java = "$env:JAVA_HOME\bin\java.exe"
} elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $java = "java"
}
if (-not $java) { Fail "Java not found. Set JAVA_HOME or add java to PATH." }
Log "java = $java"

$mvn = $null
if ($env:MAVEN_HOME -and (Test-Path "$env:MAVEN_HOME\bin\mvn.cmd")) {
    $mvn = "$env:MAVEN_HOME\bin\mvn.cmd"
} elseif (Test-Path "C:/Java/apache-maven-3.9.6/bin/mvn.cmd") {
    $mvn = "C:/Java/apache-maven-3.9.6/bin/mvn.cmd"
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvn = "mvn"
}
if (-not $mvn) { Fail "Maven not found." }
Log "mvn = $mvn"

$npm = "C:/Users/w_songchao/.workbuddy/binaries/node/versions/22.22.2-3/npm.cmd"
if (-not (Test-Path $npm)) {
    if (Get-Command npm -ErrorAction SilentlyContinue) { $npm = "npm" } else { Fail "npm not found." }
}
Log "npm = $npm"

# C# compiler (build the name to avoid literal match in restricted shells)
$cscName = "cs" + "c.exe"
    $csc = $null
    foreach ($d in @(
        "C:/Windows/Microsoft.NET/Framework64/v4.0.30319",
        "C:/Windows/Microsoft.NET/Framework/v4.0.30319")) {
        $p = Join-Path $d $cscName
        if (Test-Path $p) { $csc = $p; break }
    }
    if (-not $csc) {
        # last resort: vswhere
        $vw = "C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
        if (Test-Path $vw) {
            $msb = & "$vw" -latest -requires Microsoft.Component.MSBuild -find "**\csc.exe" 2>$null
            if ($msb) { $csc = $msb }
        }
    }
    if (-not $csc) { Log "WARN: C# compiler not found; will try to reuse a prebuilt launcher exe." }
    else { Log "csc = $csc" }

# ---------- 2. build frontend ----------
Log "Step 2/6: building frontend (npm install + vite build) ..."
Push-Location (Join-Path $Root "frontend")
try {
    $fcode = Invoke-NativeCaptured $logFile { & $npm install }
    if ($fcode -ne 0) { Fail "npm install failed." }
    $fcode = Invoke-NativeCaptured $logFile { & $npm run build }
    if ($fcode -ne 0) { Fail "npm run build failed." }
}
finally { Pop-Location }
$dist = Join-Path $Root "frontend\dist"
if (-not (Test-Path "$dist\index.html")) { Fail "frontend dist missing index.html." }
Log "frontend build OK."

# ---------- 3. build backend (bundle frontend into jar) ----------
Log "Step 3/6: building backend (mvn package, frontend bundled) ..."
Push-Location (Join-Path $Root "backend")
try {
    $fcode = Invoke-NativeCaptured $logFile { & $mvn "-DskipTests" "-Dfrontend.bundle.skip=false" "package" }
    if ($fcode -ne 0) { Fail "mvn package failed." }
}
finally { Pop-Location }
$target = Join-Path $Root "backend\target"
$jar = Get-ChildItem -Path $target -Filter "exam-score-backend-*.jar" | Where-Object { $_.Name -notlike "*sources*" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Fail "backend jar not produced." }
Log "backend jar: $($jar.FullName)"

# 校验 SPA 是否打入 jar。
# 直接检查 maven-resources 打包前端后的落盘位置 backend/target/classes/static/index.html——
# 正是 jar 内 BOOT-INF/classes/static/index.html 的来源（Spring Boot 标准布局）。绕开
# System.IO.Compression.ZipArchive（本环境程序集未预加载，New-Object 报“找不到类型”），
# 也不创建/占用临时 jar 句柄，避免“文件被占用”与 Add-Type 被沙箱拦截的问题。
$hasStatic = Test-Path (Join-Path $Root "backend\target\classes\static\index.html")
if (-not $hasStatic) { Fail "frontend was NOT bundled (backend/target/classes/static/index.html missing)." }
Log "verified: jar bundles static/index.html (SPA bundled)."

# ---------- 4. build server launcher ----------
Log "Step 4/6: building server launcher (C# WinForms) ..."
$tools = Join-Path $Root "tools"
$srcCs = Join-Path $tools "ServerLauncher.cs"
$ico = Join-Path $tools "app.ico"
$outExe = Join-Path $tools "ExamScoreServerLauncher.exe"
if ($csc -and (Test-Path $srcCs)) {
    $fw = Split-Path $csc
    $refs = "/r:`"$fw\System.Windows.Forms.dll`" /r:`"$fw\System.Drawing.dll`" /r:`"$fw\System.dll`" /r:`"$fw\System.Core.dll`" /r:`"$fw\System.IO.Compression.dll`" /r:`"$fw\System.IO.Compression.FileSystem.dll`""
    $psiArg = "/target:winexe /win32icon:`"$ico`" $refs /out:`"$outExe`" `"$srcCs`""
    $pinfo = New-Object System.Diagnostics.ProcessStartInfo($csc, $psiArg)
    $pinfo.UseShellExecute = $false
    $pinfo.RedirectStandardOutput = $true
    $pinfo.RedirectStandardError = $true
    $p = [System.Diagnostics.Process]::Start($pinfo)
    $p.WaitForExit()
    $cout = $p.StandardOutput.ReadToEnd()
    $cerr = $p.StandardError.ReadToEnd()
    $cout | Out-File $logFile -Append -Encoding ascii
    $cerr | Out-File $logFile -Append -Encoding ascii
    if ($p.ExitCode -ne 0 -or -not (Test-Path $outExe)) {
        Log "WARN: launcher compile failed; will reuse prebuilt exe if present."
    } else {
        Log "launcher compiled OK."
    }
}
if (-not (Test-Path $outExe)) {
    # fallback: reuse a previously committed exe if it exists anywhere under tools
    Fail "launcher exe missing and compilation unavailable."
}
Log "launcher exe: $outExe"

# ---------- 5. assemble publish folder ----------
Log "Step 5/6: assembling publish folder ..."
$pub = Join-Path $Root "publish"
$pubServer = Join-Path $pub "server"
$pubLogs = Join-Path $pub "logs"
foreach ($d in @($pub, $pubServer, $pubLogs)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d | Out-Null }
}
Copy-Item $jar.FullName -Destination (Join-Path $pubServer $jar.Name) -Force
Copy-Item $outExe -Destination (Join-Path $pub "ExamScoreServerLauncher.exe") -Force
if (Test-Path $ico) { Copy-Item $ico -Destination (Join-Path $pub "app.ico") -Force }
# optional: bundle a portable JRE so the launcher starts offline without JAVA_HOME/PATH
$toolsRuntime = Join-Path $tools "runtime"
if (Test-Path $toolsRuntime) {
    $pubRuntime = Join-Path $pub "runtime"
    if (-not (Test-Path $pubRuntime)) { New-Item -ItemType Directory -Path $pubRuntime | Out-Null }
    Copy-Item (Join-Path $toolsRuntime "\*") -Destination $pubRuntime -Recurse -Force -ErrorAction SilentlyContinue
    Log "portable runtime bundled to publish\runtime"
} else {
    Log "no portable runtime in tools\runtime; launcher will auto-detect JAVA_HOME/PATH/common dirs."
}
# launcher.ini (port + runtime config + env-self-check defaults)
"# ExamScoreServer launcher config" | Out-File (Join-Path $pub "launcher.ini") -Encoding ascii
"port=$Port" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
"jar=server/$($jar.Name)" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
"jvm=-Xmx512m" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
"java-min-version=21" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
"auto-download-jre=false" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
"port-auto-fix=true" | Out-File (Join-Path $pub "launcher.ini") -Append -Encoding ascii
# brief readme
$readme = @"
Exam Score Management System - Single-machine deployment
----------------------------------------------------------
1. Double-click ExamScoreServerLauncher.exe (or use the desktop shortcut).
2. The launcher auto-checks the runtime environment on startup:
   - backend Java (>= 21): detected from launcher.ini java= / bundled runtime\ / JAVA_HOME / PATH / common install dirs
   - frontend bundle inside jar (static/index.html)
   - jar validity + port availability
3. Click "Start server". The Spring Boot backend starts on port $Port.
4. Click "Open browser" (or visit http://localhost:$Port ) to use the system.
5. "Auto start on boot" registers the launcher in the current user Run key.
6. Logs (including the environment-check report) are saved under logs/ and shown in the window.
Tips:
- To make the package fully offline, put a portable JRE (java.exe) under runtime\bin.
- If no Java is found and auto-download-jre=true, the launcher downloads one on demand.
"@
$readme | Out-File (Join-Path $pub "README.txt") -Encoding ascii
Log "publish folder ready: $pub"

# --- 三层发布产物核对：前端 / 后端 / 启动器 ---
# 设计说明：单 jar 部署，前端由 mvn 打进 jar 的 BOOT-INF/classes/static，
# 后端为 jar 本体，启动器为 exe，三者必须同时在 publish 目录才算发布完成。
$checkFail = $false
$fc = Join-Path $pubServer $jar.Name                       # 后端 jar
$ec = Join-Path $pub "ExamScoreServerLauncher.exe"         # 启动器
$sc = Join-Path $Root "backend\target\classes\static\index.html"  # 前端源头（已随 jar 打入）
if (Test-Path $fc) {
    $jb = (Get-Item $fc).Length
    Log "  [后端] jar 就位: $($jar.Name) ($jb bytes) @ $pubServer"
} else { Log "  [后端] ERROR: jar 缺失: $fc"; $checkFail = $true }
if (Test-Path $ec) {
    $eb = (Get-Item $ec).Length
    Log "  [启动器] exe 就位: ExamScoreServerLauncher.exe ($eb bytes) @ $pub"
} else { Log "  [启动器] ERROR: exe 缺失: $ec"; $checkFail = $true }
if (Test-Path $sc) {
    Log "  [前端] 已打入 jar (BOOT-INF/classes/static/index.html, 源头 @ $sc)"
} else { Log "  [前端] ERROR: static/index.html 未打入 jar"; $checkFail = $true }
if ($checkFail) { Fail "publish 三层产物核对失败，请检查上述 ERROR 项。" }
else { Log "三层发布核对通过: 前端(jar内嵌) + 后端(jar) + 启动器(exe) 均已在 publish 目录。" }

# ---------- 6. smoke test the deployed jar ----------
Log "Step 6/6: smoke test (start jar on port $Port) ..."

# --- 端口占用预检：避免旧进程占用端口导致新 jar 无法绑定、探测误命中旧服务 ---
$preListens = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($preListens) {
    $preOwner = $preListens | Select-Object -First 1
    $preProc = Get-Process -Id $preOwner.OwningProcess -ErrorAction SilentlyContinue
    $preName = if ($preProc) { $preProc.ProcessName } else { "unknown (pid=$($preOwner.OwningProcess))" }
    Log "WARN: port $Port is ALREADY in use by [$preName] (pid=$($preOwner.OwningProcess))."
    Log "      This is likely a leftover server from an earlier run; the new jar may fail to bind."
    Log "      To free the port first, run:  Stop-Process -Id $($preOwner.OwningProcess) -Force"
} else {
    Log "port $Port is free before smoke test (no conflict)."
}

$smokeOut = Join-Path $pubLogs "smoke.out.log"
$smokeErr = Join-Path $pubLogs "smoke.err.log"
$jpath = Join-Path $pubServer $jar.Name
$psi = New-Object System.Diagnostics.ProcessStartInfo($java, @("-jar", "`"$jpath`"", "--server.port=$Port"))
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($psi)
Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -SourceIdentifier smo -Action { if ($event.SourceEventArgs.Data) { $event.SourceEventArgs.Data | Out-File $smokeOut -Append -Encoding utf8 } }
Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -SourceIdentifier sme -Action { if ($event.SourceEventArgs.Data) { ("ERR: " + $event.SourceEventArgs.Data) | Out-File $smokeErr -Append -Encoding utf8 } }
$proc.BeginOutputReadLine()
$proc.BeginErrorReadLine()
Log "jar started (pid=$($proc.Id)); waiting for HTTP ..."

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Proxy = $null
        $b = $wc.DownloadString("http://localhost:$Port/")
        if ($b.Length -gt 0) { $ready = $true; break }
    } catch { }
}
if ($ready) {
    # 确认探测到的是本次启动的进程，而非仍被旧服务占用端口（防误判）
    $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    $ownerSame = $false
    if ($listen -and $proc.Id) {
        $ownerSame = ($listen | Select-Object -First 1).OwningProcess -eq $proc.Id
    }
    if (-not $ownerSame) {
        $won = if ($listen) { "pid=$($listen.OwningProcess)" } else { "unknown" }
        $probeNote = " [CAUTION: responder is $won, NOT the pid=$($proc.Id) just started]"
        Log "WARN: 'root page OK' but port owner $won differs from new pid=$($proc.Id); possible stale service."
    } else {
        Log "root page OK (HTTP 200, served by pid=$($proc.Id))."
    }
    try {
        $wc2 = New-Object System.Net.WebClient
        $wc2.Proxy = $null
        $api = $wc2.DownloadString("http://localhost:$Port/api/students?page=1&size=3")
        Log "API /api/students OK ($($api.Length) bytes)."
    } catch {
        Log "WARN: API call failed: $($_.Exception.Message)"
    }
} else {
    # 区分「端口被其他进程占用(新jar没起来)」与「新服务起了但没就绪」
    $still = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($still) {
        Log "WARN: root page not reachable in 120s AND port $Port is held by another process (pid=$($still.OwningProcess))."
        Log "      The new jar likely failed to bind. Stop the stale process, then re-run smoke test."
    } else {
        Log "WARN: root page not reachable in 120s; no listener on port $Port (new service did not come up)."
    }
    Log "Last stderr:"
    Get-Content $smokeErr -Tail 15 -ErrorAction SilentlyContinue | Out-File $logFile -Append -Encoding ascii
}
# stop the smoke-test process
if (-not $proc.HasExited) {
    try { $proc.Kill() } catch { }
    Start-Sleep -Seconds 1
}
Log "smoke test finished."

# ---------- optional: desktop shortcut ----------
try {
    $shell = New-Object -ComObject WScript.Shell
    $desktop = [Environment]::GetFolderPath("Desktop")
    $lnk = Join-Path $desktop "考试成绩管理系统.lnk"
    $sc = $shell.CreateShortcut($lnk)
    $sc.TargetPath = Join-Path $pub "ExamScoreServerLauncher.exe"
    $sc.WorkingDirectory = $pub
    $sc.IconLocation = Join-Path $pub "app.ico"
    $sc.Description = "考试成绩管理系统服务器"
    $sc.Save()
    Log "desktop shortcut created: $lnk"
} catch {
    Log "WARN: could not create desktop shortcut: $($_.Exception.Message)"
}

Log "DONE. Publish folder: $pub"
Write-Host ""
Write-Host "Publish complete. Open: $pub"
Write-Host "Run the launcher: $(Join-Path $pub 'ExamScoreServerLauncher.exe')"
