// -*- coding: utf-8 -*-
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace ExamScoreServer
{
    /// <summary>
    /// 考试成绩管理系统 —— 单机部署服务器启动工具。
    /// 功能：以独立进程启动 Spring Boot 后端 jar（默认端口 8082），
    /// 实时捕获并显示启动日志与运行报错日志，支持最小化到系统托盘、
    /// 标题栏/状态栏/桌面图标，以及开机自动启动。
    /// 增强：启动时自动检查前后端运行环境是否完整（Java 运行时 / 前端内嵌 / jar / 端口），
    /// 环境缺失或不适配时自动发现并配置可用运行时，并输出结构化详细日志便于定位。
    /// 编译：csc /target:winexe /win32icon:app.ico /reference:System.Windows.Forms.dll
    ///       /reference:System.Drawing.dll /reference:System.dll /reference:System.Core.dll
    ///       /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll
    ///       /out:ExamScoreServerLauncher.exe ServerLauncher.cs
    /// </summary>
    internal static class Program
    {
        [STAThread]
        static void Main()
        {
            // 全局兜底：任何未捕获异常都写入 crash 日志并弹窗，避免“静默闪退”无法定位。
            // 此前启动器在窗体 handle 未创建时调用 BeginInvoke 导致构造期抛异常，而 Main 无
            // 任何兜底，进程直接闪退且不留日志。下面三路捕获保证异常可追溯、可提示。
            Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
            Application.ThreadException += (s, e) => Program.HandleCrash(e.Exception, "Application.ThreadException");
            AppDomain.CurrentDomain.UnhandledException += (s, e) => Program.HandleCrash(e.ExceptionObject as Exception, "AppDomain.UnhandledException");
            try
            {
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new MainForm());
            }
            catch (Exception ex)
            {
                Program.HandleCrash(ex, "Main");
            }
        }

        // 将未处理异常写入 exe 同级 logs/crash-*.log 并弹出 MessageBox（自身再用 try/catch 包裹，
        // 确保日志/弹窗过程本身不再次抛出）。
        internal static void HandleCrash(Exception ex, string source)
        {
            string detail = ex == null ? "(null exception)" : ex.ToString();
            string when = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            string crashLog = null;
            try
            {
                string dir = Path.Combine(Path.GetDirectoryName(Application.ExecutablePath), "logs");
                Directory.CreateDirectory(dir);
                crashLog = Path.Combine(dir, "crash-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".log");
                File.WriteAllText(crashLog,
                    "===== 启动器未处理异常 [" + source + "] " + when + " =====\r\n" +
                    detail + "\r\n\r\n" +
                    "【修复提示】若提示 InvalidOperationException 且发生在启动早期，说明窗体 handle 创建期间调用了跨线程/句柄敏感操作，请检查 MainForm 构造函数。" + "\r\n");
            }
            catch { }
            try
            {
                MessageBox.Show(
                    "启动器发生未处理异常（" + source + "）。\r\n\r\n" +
                    (ex == null ? "(null)" : ex.Message) +
                    (crashLog != null ? "\r\n\r\n详细堆栈已写入：\r\n" + crashLog : ""),
                    "考试成绩管理系统 · 启动器错误",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            catch { }
        }
    }

    public class MainForm : Form
    {
        private NotifyIcon trayIcon;
        private ContextMenuStrip trayMenu;
        private StatusStrip statusStrip;
        private ToolStripStatusLabel statusLabel;
        private ToolStripStatusLabel timeLabel;
        private ToolStripStatusLabel portLabel;
        private TextBox logBox;
        private Button btnStart, btnStop, btnOpen, btnExit;
        private CheckBox chkAuto;
        private Process serverProc;
        private Icon appIcon;
        private string exeDir;
        private int port = 8082;
        private string jarPath;
        private string jvmOpts = "-Xmx512m";
        private string logDir;
        private StreamWriter logWriter;
        private bool reallyExit = false;
        private bool autoStartRequested = false;
        // 是否跟随日志滚动到底部：仅当服务器在运行时自动跟读最新日志（AppendUi 据此判断），
        // 启动器刚打开、未启动服务时保持滚动在顶部，保证初始日志（顶部几行）完整可见。
        private bool followLogTail = false;
        // 早期日志缓冲：UI 尚未初始化时（InitComponents 之前）的日志先入队，待 UI 就绪后刷出
        private List<string> pendingLogs;
        // 全量日志显示缓冲（始终累加，含 [信息]/[错误] 前缀）。
        // 用于「未运行时整体重设 logBox.Text」：TextBox 赋文本后天然从第一行显示，
        // 完全不依赖 ScrollToCaret 向上滚动（该环境上滚不可靠，前几版在此栽跟头）。
        private readonly List<string> logLines = new List<string>();

        // ---------- 环境自检/自愈 增强项 ----------
        private string cfgJava;                     // launcher.ini: java=<绝对路径>（最高优先级运行时）
        private int minJavaVersion = 21;            // launcher.ini: java-min-version
        private bool autoDownloadJre = false;       // launcher.ini: auto-download-jre
        private string autoJreUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
        private bool portAutoFix = true;            // launcher.ini: port-auto-fix（默认开启：目标端口被占用时自动顺延到下一个空闲端口）
        private string cfgProfile = "";             // launcher.ini: profile=（激活的 Spring profile，如 prod/pg；留空用默认持久化 H2）
        private readonly List<KeyValuePair<string, string>> cfgEnv = new List<KeyValuePair<string, string>>(); // launcher.ini: env=KEY=VALUE（可多行，注入子进程环境变量，用于 DB_*/APP_* 接线）
        private string resolvedJava;                // 自检后选定的 java.exe 绝对路径
        private string resolvedJavaName;            // 用于展示的 java 来源说明
        private bool jarOk;                         // jar 存在且为合法 zip
        private bool frontendOk;                    // 前端已内嵌
        private bool javaOk;                        // 已定位可用 Java(>= minJavaVersion)
        private bool canStart;                      // jarOk && javaOk
        private System.Windows.Forms.Timer healthTimer; // 启动后就绪轮询
        private int healthTries;
        private const int HealthIntervalMs = 2000;
        private const int HealthMaxTries = 90;      // 最多 180s

        public MainForm()
        {
            exeDir = Path.GetDirectoryName(Application.ExecutablePath);
            pendingLogs = new List<string>();
            appIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            autoStartRequested = Array.Exists(
                Environment.GetCommandLineArgs(),
                a => a.Equals("/autostart", StringComparison.OrdinalIgnoreCase));

            LoadConfig();
            FindJar();
            EnsureLogDir();
            InitComponents();
            FlushPendingLogs();
            ApplyConfig();

            // 打开启动器不再自动执行环境自检——改为用户点击【启动服务器】时才自检并启动，
            // 避免无谓耗时与干扰。自检逻辑统一收敛到 StartServer() 内部（启动前先 RunSelfCheck）。
            // 仅开机自启(/autostart 或已勾选)场景，在本事件里自动触发启动。
            this.Shown += (s, e) =>
            {
                try
                {
                    if (autoStartRequested || IsAutoStartEnabled())
                    {
                        Log("[自动启动] 检测到开机自启，自动启动服务器（含环境自检）……", false);
                        StartServer();
                    }
                    else
                    {
                        Log("就绪。请点击【启动服务器】以自检环境并启动。", false);
                    }

                    // 最终定格显示：未跟读（未启动服务）时整体重设文本，TextBox 天然从第一行显示，
                    // 不再依赖 ScrollToCaret 向上滚动（该环境不可靠）。服务自动启动场景
                    // (followLogTail 已为 true) 则跳过，保持跟读最新日志。
                    BeginInvoke((Action)(() =>
                    {
                        try
                        {
                            if (logBox != null && !followLogTail)
                            {
                                ApplyLogDisplay();
                                logBox.Refresh();
                            }
                        }
                        catch { }
                    }));
                }
                catch (Exception ex)
                {
                    Log("[启动][异常] 自启动阶段失败: " + ex.Message, true);
                }
            };
        }

        // ---------- 配置加载 ----------
        private void LoadConfig()
        {
            string ini = Path.Combine(exeDir, "launcher.ini");
            if (!File.Exists(ini)) return;
            foreach (var raw in File.ReadAllLines(ini))
            {
                var line = raw.Trim();
                if (line.Length == 0 || line.StartsWith("#") || line.StartsWith(";")) continue;
                int eq = line.IndexOf('=');
                if (eq <= 0) continue;
                string key = line.Substring(0, eq).Trim().ToLowerInvariant();
                string val = line.Substring(eq + 1).Trim();
                if (key == "port") { int pp; if (int.TryParse(val, out pp)) port = pp; }
                else if (key == "jar") jarPath = val;
                else if (key == "jvm") jvmOpts = val;
                else if (key == "java") cfgJava = val;
                else if (key == "java-min-version") { int pp; if (int.TryParse(val, out pp)) minJavaVersion = pp; }
                else if (key == "auto-download-jre") autoDownloadJre = ParseBool(val, false);
                else if (key == "auto-jre-url" && val.Length > 0) autoJreUrl = val;
                else if (key == "port-auto-fix") portAutoFix = ParseBool(val, false);
                else if (key == "profile") cfgProfile = val;
                else if (key == "env")
                {
                    // P2-5 环境变量接线：env=KEY=VALUE（允许多行，注入后端子进程），用于 DB_*/APP_* 配置
                    int ee = val.IndexOf('=');
                    if (ee > 0)
                    {
                        string k = val.Substring(0, ee).Trim();
                        string v = val.Substring(ee + 1).Trim();
                        if (k.Length > 0) cfgEnv.Add(new KeyValuePair<string, string>(k, v));
                    }
                }
            }
        }

        private static bool ParseBool(string v, bool def)
        {
            if (string.IsNullOrWhiteSpace(v)) return def;
            v = v.Trim().ToLowerInvariant();
            if (v == "true" || v == "1" || v == "yes" || v == "on") return true;
            if (v == "false" || v == "0" || v == "no" || v == "off") return false;
            return def;
        }

        private void ApplyConfig()
        {
            Text = "考试成绩管理系统 · 服务器控制台 (:" + port + ")";
            chkAuto.Checked = IsAutoStartEnabled();
            if (!string.IsNullOrEmpty(jarPath) && !File.Exists(jarPath))
            {
                Log("[ENV][警告] 配置中的 jar 路径不存在: " + jarPath, true);
                jarPath = null;
            }
            if (string.IsNullOrEmpty(jarPath))
            {
                Log("未找到后端 jar 包，请确认发布目录结构（将 jar 放在启动器同目录或 server/ 子目录）。", true);
            }
            else
            {
                Log("已定位后端程序: " + jarPath, false);
            }
        }

        private void FindJar()
        {
            if (!string.IsNullOrEmpty(jarPath))
            {
                if (Path.IsPathRooted(jarPath)) { if (File.Exists(jarPath)) return; }
                else { string full = Path.Combine(exeDir, jarPath); if (File.Exists(full)) { jarPath = full; return; } }
            }
            string[] dirs = { exeDir, Path.Combine(exeDir, "server"), Path.Combine(exeDir, "backend") };
            foreach (var d in dirs)
            {
                if (!Directory.Exists(d)) continue;
                var jars = Directory.GetFiles(d, "*.jar");
                if (jars.Length > 0) { Array.Sort(jars); jarPath = jars[0]; return; }
            }
            jarPath = null;
        }

        private void EnsureLogDir()
        {
            logDir = Path.Combine(exeDir, "logs");
            try { Directory.CreateDirectory(logDir); } catch { logDir = exeDir; }
            string logFile = Path.Combine(logDir, "server-" + DateTime.Now.ToString("yyyyMMdd") + ".log");
            try
            {
                logWriter = new StreamWriter(new FileStream(logFile, FileMode.Append, FileAccess.Write, FileShare.Read), new UTF8Encoding(false));
                logWriter.WriteLine();
                logWriter.WriteLine("===== 启动器会话 " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " =====");
                logWriter.Flush();
                Log("日志文件: " + logFile, false);
            }
            catch (Exception ex) { Log("无法创建日志文件: " + ex.Message, true); }
        }

        // ---------- UI 构建 ----------
        private void InitComponents()
        {
            this.Icon = appIcon;
            this.Text = "考试成绩管理系统 · 服务器控制台";
            this.StartPosition = FormStartPosition.CenterScreen;
            this.Size = new Size(880, 560);
            this.MinimumSize = new Size(640, 420);
            this.FormBorderStyle = FormBorderStyle.Sizable;
            this.ShowInTaskbar = true;

            // 用 TableLayoutPanel 把 工具条/日志区/状态栏 分成固定三行。
            // 目的：彻底杜绝 dock 边缘控件(工具条/状态栏)与 Fill 控件因容器的 z-order 顺序
            // 重叠，导致日志区被上方控件遮挡或只剩一条（这正是“顶部行看不到”的另一种可能根因）。
            var layoutRoot = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                RowCount = 3,
                ColumnCount = 1,
                Margin = Padding.Empty,
                Padding = Padding.Empty
            };
            layoutRoot.RowStyles.Add(new RowStyle(SizeType.Absolute, 46)); // 顶部工具条
            layoutRoot.RowStyles.Add(new RowStyle(SizeType.Percent, 100)); // 日志区(占满剩余)
            layoutRoot.RowStyles.Add(new RowStyle(SizeType.Absolute, 24)); // 底部状态栏

            // 顶部工具条
            var toolBar = new FlowLayoutPanel
            {
                Dock = DockStyle.Top,
                Height = 46,
                Padding = new Padding(8, 6, 8, 6),
                BackColor = Color.FromArgb(245, 248, 252)
            };
            btnStart = new Button { Text = "启动服务器", Width = 110, Height = 30 };
            btnStop = new Button { Text = "停止", Width = 90, Height = 30 };
            btnOpen = new Button { Text = "打开浏览器", Width = 110, Height = 30 };
            btnExit = new Button { Text = "退出", Width = 80, Height = 30 };
            chkAuto = new CheckBox { Text = "开机自动启动", AutoSize = true, Anchor = AnchorStyles.Left, TextAlign = ContentAlignment.MiddleLeft };
            chkAuto.Margin = new Padding(12, 8, 0, 0);
            btnStart.Click += (s, e) => StartServer();
            btnStop.Click += (s, e) => StopServer();
            btnOpen.Click += (s, e) => OpenBrowser();
            btnExit.Click += (s, e) => { reallyExit = true; Close(); };
            chkAuto.CheckedChanged += (s, e) => SetAutoStart(chkAuto.Checked);
            toolBar.Controls.AddRange(new Control[] { btnStart, btnStop, btnOpen, chkAuto, btnExit });
            toolBar.Dock = DockStyle.Fill;
            layoutRoot.Controls.Add(toolBar, 0, 0);

            // 日志区 —— 改用多行 TextBox 替代 RichTextBox。
            // 原因：实测 RichTextBox 在该环境/系统下初始滚动定位不可靠，反复修复后
            // 首行仍被顶出视口（日志文件数据始终完整，纯视图问题）。多行只读 TextBox
            // 打开即从第一行显示、滚动定位稳定，彻底规避该问题。代价：失去按字符着色，
            // 改为用 [信息]/[错误] 文本前缀在 AppendUi 内区分。
            logBox = new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                WordWrap = true,
                BackColor = Color.FromArgb(20, 24, 28),
                ForeColor = Color.FromArgb(220, 230, 240),
                Font = new Font("Consolas", 10F)
            };
            layoutRoot.Controls.Add(logBox, 0, 1);

            // 状态栏
            statusStrip = new StatusStrip();
            statusLabel = new ToolStripStatusLabel { Text = "● 已停止", ForeColor = Color.Gray, Spring = true };
            timeLabel = new ToolStripStatusLabel { Text = "", BorderSides = ToolStripStatusLabelBorderSides.Left };
            statusStrip.Items.AddRange(new ToolStripItem[] { statusLabel, timeLabel });
            portLabel = new ToolStripStatusLabel { Text = "端口 " + port, BorderSides = ToolStripStatusLabelBorderSides.Left };
            statusStrip.Items.Add(portLabel);
            statusStrip.Dock = DockStyle.Fill;
            layoutRoot.Controls.Add(statusStrip, 0, 2);

            // 把整幅三行表格加入窗体：三个区域的行高由表格分配，日志区必然占据工具条与
            // 状态栏之间的全部剩余空间，不再可能被遮挡。
            this.Controls.Add(layoutRoot);

            // 托盘
            trayMenu = new ContextMenuStrip();
            trayMenu.Items.Add("显示窗口", null, (s, e) => ShowWindow());
            trayMenu.Items.Add("启动服务器", null, (s, e) => StartServer());
            trayMenu.Items.Add("停止服务器", null, (s, e) => StopServer());
            trayMenu.Items.Add(new ToolStripSeparator());
            trayMenu.Items.Add("退出", null, (s, e) => { reallyExit = true; Close(); });
            trayIcon = new NotifyIcon
            {
                Icon = appIcon,
                Text = "考试成绩管理系统服务器",
                ContextMenuStrip = trayMenu,
                Visible = false
            };
            trayIcon.DoubleClick += (s, e) => ShowWindow();

            this.Resize += (s, e) => { if (WindowState == FormWindowState.Minimized) { Hide(); trayIcon.Visible = true; } };
            this.FormClosing += (s, e) =>
            {
                if (!reallyExit)
                {
                    e.Cancel = true;
                    WindowState = FormWindowState.Minimized;
                    Hide();
                    trayIcon.Visible = true;
                }
                else
                {
                    // 真正退出前清理仍在运行的服务器子进程，避免残留 java 进程持续占用端口（如 8082），
                    // 否则下次启动会反复探测到端口被占用而顺延，端口号不断漂移。
                    try
                    {
                        if (healthTimer != null) { healthTimer.Stop(); healthTimer.Dispose(); healthTimer = null; }
                    }
                    catch { }
                    try
                    {
                        if (serverProc != null && !serverProc.HasExited)
                        {
                            serverProc.Kill();
                            serverProc.WaitForExit(3000);
                        }
                    }
                    catch { }
                    try { trayIcon.Visible = false; } catch { }
                }
            };

            // 状态栏时钟
            var timer = new System.Windows.Forms.Timer { Interval = 1000 };
            timer.Tick += (s, e) => { if (timeLabel != null) timeLabel.Text = DateTime.Now.ToString("HH:mm:ss"); };
            timer.Start();
        }

        // =====================================================================
        //                环境自检与自愈（核心增强）
        // =====================================================================
        private void RunSelfCheck()
        {
            Log("---------- 运行环境自检开始 ----------", false);

            // 1) 应用包检查
            CheckJar();

            // 2) 前端内嵌检查（单 jar 全栈：前端在 BOOT-INF/classes/static/）
            CheckFrontendBundled();

            // 3) 后端运行时（Java）多源探测 + 自愈
            ResolveJava();

            // 4) 端口占用预检
            CheckPort();

            // 5) 汇总
            canStart = jarOk && javaOk;

            // 5.1) 安全姿态自检：默认 H2 单机模式下若未注入密钥，仍使用内置可预测演示密钥，
            // 一旦该部署对外暴露（公网/局域网非授信），可被伪造 admin JWT / 解密身份证。
            // 此处给出醒目告警，强制要求生产部署注入 APP_SECURITY_TOKEN_SECRET 与 APP_CRYPTO_KEY。
            bool tokenInjected = HasEnv("APP_SECURITY_TOKEN_SECRET");
            bool aesInjected = HasEnv("APP_CRYPTO_KEY");
            bool prodLike = !string.IsNullOrWhiteSpace(cfgProfile)
                && (cfgProfile.IndexOf("prod", StringComparison.OrdinalIgnoreCase) >= 0
                    || cfgProfile.IndexOf("pg", StringComparison.OrdinalIgnoreCase) >= 0);
            if (!prodLike && (!tokenInjected || !aesInjected))
            {
                Log("[SEC][警告] 当前未注入密钥，使用内置演示密钥（JWT/AES）。"
                    + (tokenInjected ? "" : " 缺少 APP_SECURITY_TOKEN_SECRET")
                    + (aesInjected ? "" : " 缺少 APP_CRYPTO_KEY")
                    + "。演示密钥可被逆向/伪造，严禁用于公网或局域网非授信部署；生产请以环境变量注入强随机密钥。", true);
            }

            Log("---------- 运行环境自检结束 ----------", false);
            if (canStart)
                Log("[ENV][通过] 环境完整，可启动服务器。", false);
            else
                Log("[ENV][失败] 环境不完整，请根据上方提示处理（或开启 auto-download-jre 自动下载运行时）。", true);

            BeginInvoke((Action)(() => { btnStart.Enabled = canStart; }));
        }

        // ---------- 启动参数注入防护辅助 ----------
        // 对 launcher.ini 外部可控值做白名单/合法性校验，防止恶意值逃逸引号注入 JVM 参数
        //（如 jarPath 含 " 触发 -javaagent: RCE、cfgProfile 注入额外程序参数）。
        private static bool IsSafeProfile(string s)
        {
            if (string.IsNullOrWhiteSpace(s)) return true; // 空=默认 profile，允许
            if (s.Length > 64) return false;
            foreach (char c in s)
                if (!(char.IsLetterOrDigit(c) || c == '_' || c == '-')) return false;
            return true;
        }

        private static bool IsSafeJarPath(string s)
        {
            if (string.IsNullOrWhiteSpace(s)) return false;
            // Windows 合法文件路径不含双引号、重定向/管道与控制字符；含之即可逃逸启动参数或破坏命令行
            foreach (char c in s)
                if (c == '"' || c == '<' || c == '>' || c == '|' || char.IsControl(c)) return false;
            return true;
        }

        // 是否注入了指定环境变量：优先看 launcher.ini 的 env= 注入，再看进程环境变量
        private bool HasEnv(string name)
        {
            foreach (var kv in cfgEnv)
                if (kv.Key == name) return true;
            return !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(name));
        }

        private void CheckJar()
        {
            if (string.IsNullOrEmpty(jarPath) || !File.Exists(jarPath))
            {
                jarOk = false;
                Log("[ENV][失败] 未找到后端 jar 包。", true);
                return;
            }
            var fi = new FileInfo(jarPath);
            if (fi.Length == 0)
            {
                jarOk = false;
                Log("[ENV][失败] jar 为空文件: " + jarPath, true);
                return;
            }
            // zip 魔数校验
            bool isZip = false;
            try
            {
                using (var fs = File.OpenRead(jarPath))
                {
                    byte[] head = new byte[2];
                    if (fs.Read(head, 0, 2) == 2) isZip = head[0] == 0x50 && head[1] == 0x4B; // "PK"
                }
            }
            catch { }
            if (!isZip)
            {
                jarOk = false;
                Log("[ENV][失败] 文件不是合法 jar(zip) 包: " + jarPath, true);
                return;
            }
            // P1-2 完整性（纵深可选）：若发布目录存在同名 .sha256（期望哈希），则比对；
            // 缺失 `.sha256` 时仅 WARN 不阻断，保证单机免配分发仍可直接启动。
            // VerifyJarChecksumOptional 为纯函数（仅返回强校验是否通过，不写 jarOk）；
            // 校验不通过时由本调用方统一置 jarOk=false 并终止，避免原实现的双重赋值混淆
            // 以及被下方无条件 `jarOk=true` 覆盖导致强校验失效。
            if (!VerifyJarChecksumOptional())
            {
                jarOk = false;
                Log("[ENV][失败] 应用包完整性校验未通过: " + jarPath, true);
                return;
            }
            jarOk = true;
            Log("[ENV][通过] 应用包有效: " + jarPath + " (" + (fi.Length / 1024 / 1024) + " MB)", false);
        }

        /// <summary>可选 SHA-256 校验：存在 &lt;jar&gt;.sha256 则强制比对（不匹配则返回 false 阻断），缺失则仅告警放行。</summary>
        /// <returns>true=强校验通过或未提供校验文件（放行）；false=校验不一致（须阻断启动）。</returns>
        private bool VerifyJarChecksumOptional()
        {
            try
            {
                string shaFile = jarPath + ".sha256";
                if (!File.Exists(shaFile))
                {
                    Log("[ENV][提示] 未提供 " + Path.GetFileName(shaFile) + "，跳过完整性校验（如需强校验请随发布生成该文件）。", false);
                    return true;
                }
                string expect = File.ReadAllText(shaFile).Trim();
                int sep = expect.IndexOf(' ');
                if (sep > 0) expect = expect.Substring(0, sep); // 兼容 "hash  path" 格式
                if (expect.Length != 64)
                {
                    Log("[ENV][警告] " + Path.GetFileName(shaFile) + " 内容不是合法 SHA-256，忽略该校验。", true);
                    return true;
                }
                string actual;
                using (var fs = File.OpenRead(jarPath))
                using (var sha = SHA256.Create())
                {
                    byte[] h = sha.ComputeHash(fs);
                    actual = BitConverter.ToString(h).Replace("-", "").ToLowerInvariant();
                }
                if (string.Equals(actual, expect, StringComparison.OrdinalIgnoreCase))
                {
                    Log("[ENV][通过] jar SHA-256 校验一致（" + Path.GetFileName(shaFile) + "）。", false);
                    return true;
                }
                else
                {
                    // 注意：本方法保持「纯函数」——仅返回强校验结果，不修改 jarOk 字段；
                    // jarOk 的置位统一由调用方 CheckJar 依据返回值处理（修复复评发现的双重赋值混淆）。
                    Log("[ENV][失败] jar SHA-256 与 " + Path.GetFileName(shaFile) + " 不一致，可能被篡改或版本不符！", true);
                    return false;
                }
            }
            catch (Exception ex)
            {
                Log("[ENV][警告] SHA-256 校验异常，跳过（" + ex.Message + "）。", true);
                return true;
            }
        }

        private void CheckFrontendBundled()
        {
            if (!jarOk) { frontendOk = false; return; }
            try
            {
                bool found = false;
                using (var zip = ZipFile.OpenRead(jarPath))
                {
                    foreach (var e in zip.Entries)
                    {
                        if (e.FullName.Equals("BOOT-INF/classes/static/index.html", StringComparison.OrdinalIgnoreCase))
                        { found = true; break; }
                    }
                }
                frontendOk = found;
                if (found)
                    Log("[ENV][通过] 前端已内嵌（jar 内含 BOOT-INF/classes/static/index.html）。", false);
                else
                    Log("[ENV][警告] jar 未内嵌前端页面（可能用 -Dfrontend.bundle.skip=true 打包）。可访问后端 API，但无前端界面；请重新用全栈方式打包。", true);
            }
            catch (Exception ex)
            {
                frontendOk = false;
                Log("[ENV][警告] 读取 jar 校验前端失败: " + ex.Message, true);
            }
        }

        private void ResolveJava()
        {
            javaOk = false;
            resolvedJava = null;
            resolvedJavaName = "";
            Log("[ENV] 开始探测 Java 运行时（要求主版本 >= " + minJavaVersion + "）……", false);

            var candidates = new List<JavaCandidate>();

            // a) launcher.ini 显式 java=
            if (!string.IsNullOrEmpty(cfgJava))
            {
                string full = Path.IsPathRooted(cfgJava) ? cfgJava : Path.Combine(exeDir, cfgJava);
                if (File.Exists(full)) candidates.Add(new JavaCandidate(full, "launcher.ini java="));
                else Log("[ENV][跳过] launcher.ini 指定 java 不存在: " + full, true);
            }

            // b) 便携运行时（随包携带）：runtime\ / jre\
            foreach (var sub in new[] { "runtime", "jre", "jdk" })
            {
                foreach (var rel in new[] { Path.Combine(sub, "bin", "java.exe"),
                                            Path.Combine(sub, "java.exe") })
                {
                    string full = Path.Combine(exeDir, rel);
                    if (File.Exists(full)) candidates.Add(new JavaCandidate(full, "便携运行时 " + rel));
                }
                // runtime 下可能再嵌一层（如 runtime\jdk-21\bin\java.exe）
                string baseDir = Path.Combine(exeDir, sub);
                if (Directory.Exists(baseDir))
                    AddJavaCandidatesUnder(candidates, baseDir, "便携运行时 " + sub + "\\");
            }

            // c) JAVA_HOME
            string jh = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(jh))
            {
                string p = Path.Combine(jh, "bin", "java.exe");
                if (File.Exists(p)) candidates.Add(new JavaCandidate(p, "JAVA_HOME"));
                else Log("[ENV][跳过] JAVA_HOME 下无 bin\\java.exe: " + jh, true);
            }

            // d) PATH 中的 java
            string pathJava = Which("java.exe");
            if (pathJava != null) candidates.Add(new JavaCandidate(pathJava, "PATH"));

            // e) 常见安装目录
            foreach (var root in new[] {
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86) })
            {
                if (string.IsNullOrEmpty(root) || !Directory.Exists(root)) continue;
                foreach (var dir in new[] { "Java", "Eclipse Adoptium", "Zulu", "Amazon Corretto", "BellSoft", "Microsoft" })
                {
                    string d0 = Path.Combine(root, dir);
                    if (Directory.Exists(d0)) AddJavaCandidatesUnder(candidates, d0, dir + "\\");
                }
                // ProgramFiles\Microsoft\jdk-* 直接扫描
                foreach (var d1 in SafeDirs(root))
                {
                    if (d1.ToLowerInvariant().StartsWith("jdk-") || d1.ToLowerInvariant().StartsWith("jre-"))
                    {
                        string p = Path.Combine(root, d1, "bin", "java.exe");
                        if (File.Exists(p)) candidates.Add(new JavaCandidate(p, d1));
                    }
                }
            }

            // 去重 + 版本探测
            var seen = new HashSet<string>();
            var usable = new List<JavaCandidate>();
            Log("[ENV] 找到 " + candidates.Count + " 个 java 候选，逐一代码版本探测……", false);
            foreach (var c in candidates)
            {
                string key = c.Path.ToLowerInvariant();
                if (!seen.Add(key)) continue;
                if (!File.Exists(c.Path)) { Log("[ENV][跳过] 不存在: " + c.Path, true); continue; }
                int maj; string vtxt;
                GetJavaVersion(c.Path, out maj, out vtxt);
                c.Major = maj; c.VersionText = vtxt;
                if (maj == 0)
                    Log("[ENV][跳过] 无法解析版本: " + c.Path + "（" + c.Source + "）", true);
                else if (maj >= minJavaVersion)
                {
                    usable.Add(c);
                    Log("[ENV][候选] " + c.Source + " → " + c.Path + "（Java " + vtxt + "）满足要求", false);
                }
                else
                    Log("[ENV][警告] " + c.Source + " 版本过低（" + vtxt + " < " + minJavaVersion + "）: " + c.Path, true);
            }

            // 择优：版本降序，同版本优先 PATH/JAVA_HOME/便携（更可能无依赖）
            if (usable.Count > 0)
            {
                usable.Sort((a, b) =>
                {
                    int r = b.Major.CompareTo(a.Major);
                    if (r != 0) return r;
                    int pa = RankSource(a.Source), pb = RankSource(b.Source);
                    return pa.CompareTo(pb);
                });
                var chosen = usable[0];
                resolvedJava = chosen.Path;
                resolvedJavaName = chosen.Source + " · Java " + chosen.VersionText;
                javaOk = true;
                Log("[ENV][通过] 采用运行环境: " + resolvedJavaName + " → " + resolvedJava, false);
                return;
            }

            // 自愈：联网下载便携 JRE（可选）
            if (autoDownloadJre)
            {
                Log("[ENV] 未找到可用 Java，auto-download-jre=true，开始联网下载便携 JRE……", false);
                if (TryDownloadJre())
                {
                    Log("[ENV][通过] 下载并解压便携 JRE 完成，重新定位 java……", false);
                    ResolveJavaAfterDownload();
                    return;
                }
            }

            Log("[ENV][失败] 未找到满足要求的 Java 运行时（>= " + minJavaVersion + "）。", true);
            Log("[ENV][修复建议] ① 设置 JAVA_HOME 或加入 PATH；② 在启动器同级放置 runtime\\bin\\java.exe（或 jre/jdk）便携运行时；③ 在 launcher.ini 设 auto-download-jre=true 联网自动下载。", true);
        }

        // 简化：占位避免编译期长表达式；下方实现真正逻辑
        private void ResolveJavaAfterDownload()
        {
            foreach (var sub in new[] { "runtime", "jre", "jdk" })
            {
                string baseDir = Path.Combine(exeDir, sub);
                if (!Directory.Exists(baseDir)) continue;
                string found = FindJavaRecursive(baseDir, 3);
                if (found != null)
                {
                    resolvedJava = found;
                    resolvedJavaName = "便携运行时(自动下载) " + sub;
                    javaOk = true;
                    Log("[ENV][通过] 采用自动下载运行环境: " + found, false);
                    return;
                }
            }
            Log("[ENV][失败] 已下载便携 JRE 但仍未定位到 java.exe（可能在子目录较深）。", true);
        }

        private static int RankSource(string src)
        {
            string s = src == null ? "" : src;
            if (s.StartsWith("launcher.ini")) return 0;
            if (s.StartsWith("便携运行时")) return 1;
            if (s.StartsWith("JAVA_HOME")) return 2;
            if (s.StartsWith("PATH")) return 3;
            return 4;
        }

        private bool TryDownloadJre()
        {
            // 安全：仅允许经过 TLS 的下载源，防止明文 http 被中间人替换 JRE 植入恶意代码
            if (string.IsNullOrWhiteSpace(autoJreUrl)
                || !autoJreUrl.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                Log("[SEC][失败] auto-jre-url 必须以 https:// 开头（禁止明文 http 下载 JRE，防中间人篡改）。当前=" + autoJreUrl, true);
                return false;
            }
            string runtimeDir = Path.Combine(exeDir, "runtime");
            try { Directory.CreateDirectory(runtimeDir); } catch { }
            string zip = Path.Combine(runtimeDir, "jre.zip");
            try
            {
                // [修复] .NET Framework 4.x 的 WebClient/HttpWebRequest 默认安全协议过旧（仅 SSL3/TLS1.0），
                // 连接现代 HTTPS 端点（Adoptium API 强制 TLS1.2+）时报“未能创建 SSL/TLS 安全通道”，
                // 使 auto-download-jre 自愈失效。显式开启 TLS1.2/TLS1.1 是该下载成功的前提。
                // 数值 768=Tls11, 3072=Tls12（避免直接写枚举名在老框架不可编译）。
                try { ServicePointManager.SecurityProtocol = ServicePointManager.SecurityProtocol | (SecurityProtocolType)768 | (SecurityProtocolType)3072; }
                catch { try { ServicePointManager.SecurityProtocol = (SecurityProtocolType)768 | (SecurityProtocolType)3072; } catch { } }
                Log("[ENV][下载] 目标: " + autoJreUrl, false);
                Log("[ENV][下载] 保存到: " + zip, false);
                using (var wc = new WebClient())
                {
                    wc.Proxy = null;
                    wc.Headers.Add("User-Agent", "ExamScoreServerLauncher/1.0");
                    wc.DownloadFile(autoJreUrl, zip);
                }
                // 可选完整性校验：若用户随发布在 runtime/ 放置 jre.zip.sha256，则比对；缺失则跳过
                if (!VerifyDownloadChecksumOptional(zip))
                {
                    try { File.Delete(zip); } catch { }
                    return false;
                }
                Log("[ENV][下载] 下载完成 (" + new FileInfo(zip).Length / 1024 / 1024 + " MB)。", false);
                Log("[ENV][解压] 解压到 " + runtimeDir + " ……", false);
                ZipFile.ExtractToDirectory(zip, runtimeDir);
                try { File.Delete(zip); } catch { }
                Log("[ENV][解压] 解压完成。", false);
                return true;
            }
            catch (Exception ex)
            {
                Log("[ENV][失败] 自动下载/解压 JRE 失败: " + ex.Message, true);
                return false;
            }
        }

        // 下载 JRE 的辅助完整性校验：存在 runtime/jre.zip.sha256 则强制比对（不一致删除并返回 false），缺失则放行
        private bool VerifyDownloadChecksumOptional(string zip)
        {
            string shaFile = zip + ".sha256";
            if (!File.Exists(shaFile)) return true;
            try
            {
                string expect = File.ReadAllText(shaFile).Trim();
                int sep = expect.IndexOf(' ');
                if (sep > 0) expect = expect.Substring(0, sep);
                if (expect.Length != 64) { Log("[SEC][警告] jre.zip.sha256 内容非法，忽略该校验。", true); return true; }
                using (var fs = File.OpenRead(zip))
                using (var sha = SHA256.Create())
                {
                    byte[] h = sha.ComputeHash(fs);
                    string actual = BitConverter.ToString(h).Replace("-", "").ToLowerInvariant();
                    if (string.Equals(actual, expect, StringComparison.OrdinalIgnoreCase)) return true;
                    Log("[SEC][失败] JRE 下载文件 SHA-256 与 jre.zip.sha256 不一致，可能已被篡改，已删除该文件。", true);
                    return false;
                }
            }
            catch (Exception ex)
            {
                Log("[SEC][警告] JRE 校验异常，跳过（" + ex.Message + "）。", true);
                return true;
            }
        }

        private void CheckPort()
        {
            if (openPortFree(port))
            {
                Log("[ENV][通过] 端口 " + port + " 空闲，可直接监听。", false);
                return;
            }
            Log("[ENV][警告] 端口 " + port + " 已被占用（可能残留旧进程或他程序）。", true);
            if (portAutoFix)
            {
                int next = port;
                for (int i = 1; i <= 100 && !openPortFree(next); i++) next = port + i;
                Log("[ENV] port-auto-fix=true，顺延至空闲端口: " + next + "（原 " + port + "）", false);
                port = next;
                BeginInvoke((Action)(() =>
                {
                    Text = "考试成绩管理系统 · 服务器控制台 (:" + port + ")";
                    if (portLabel != null) portLabel.Text = "端口 " + port;
                }));
                Log("[ENV][通过] 现用端口 " + port + " 空闲。", false);
            }
        }

        private static bool openPortFree(int p)
        {
            try
            {
                var l = new TcpListener(IPAddress.Any, p);
                l.Start();
                l.Stop();
                return true;
            }
            catch { return false; }
        }

        // ---------- Java 工具 ----------
        private class JavaCandidate
        {
            public string Path; public string Source; public int Major; public string VersionText;
            public JavaCandidate(string p, string s) { Path = p; Source = s; Major = 0; VersionText = ""; }
        }

        private static void GetJavaVersion(string javaExe, out int major, out string versionText)
        {
            major = 0; versionText = "";
            try
            {
                var psi = new ProcessStartInfo(javaExe, "-version")
                {
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                using (var p = Process.Start(psi))
                {
                    if (p == null) return;
                    string all = p.StandardError.ReadToEnd();
                    if (string.IsNullOrEmpty(all)) all = p.StandardOutput.ReadToEnd();
                    p.WaitForExit(5000);
                    versionText = all.Trim();
                    // 匹配形如: openjdk version "21.0.3" 或 java version "1.8.0_xxx"
                    int qi = all.IndexOf('"');
                    if (qi < 0) return;
                    int qj = all.IndexOf('"', qi + 1);
                    if (qj < 0) return;
                    string v = all.Substring(qi + 1, qj - qi - 1);
                    int dot = v.IndexOf('.');
                    string first = dot < 0 ? v : v.Substring(0, dot);
                    int n;
                    if (int.TryParse(first, out n))
                    {
                        if (n == 1)
                        {
                            string rest = v.Substring(2);
                            int d2 = rest.IndexOf('.');
                            string second = d2 < 0 ? rest : rest.Substring(0, d2);
                            int m; if (int.TryParse(second, out m)) { major = m; return; }
                        }
                        major = n;
                    }
                }
            }
            catch { }
        }

        private static string Which(string filename)
        {
            string path = Environment.GetEnvironmentVariable("PATH");
            if (string.IsNullOrEmpty(path)) return null;
            foreach (var dir in path.Split(';'))
            {
                if (string.IsNullOrEmpty(dir)) continue;
                string full = Path.Combine(dir.Trim('"'), filename);
                if (File.Exists(full)) return full;
            }
            return null;
        }

        /// <summary>#25 日志脱敏：对启动命令中疑似密钥/口令的 jvmOpts 打码，防止把密钥写进日志被泄露。</summary>
        private static string SanitizeArgs(string args)
        {
            if (string.IsNullOrEmpty(args)) return args;
            // 识别并打码常见密钥类参数（APP_CRYPTO_KEY / APP_SECURITY_TOKEN_SECRET / password= / -Dxxx.key= / secret= 等）
            var result = System.Text.RegularExpressions.Regex.Replace(
                args,
                @"(?i)(APP_CRYPTO_KEY|APP_SECURITY_TOKEN_SECRET|password|secret|token)\s*=\s*[^\s]+",
                "$1=***");
            return result;
        }

        private static void AddJavaCandidatesUnder(List<JavaCandidate> list, string dir, string srcPrefix)
        {
            try
            {
                string direct = Path.Combine(dir, "bin", "java.exe");
                if (File.Exists(direct)) { list.Add(new JavaCandidate(direct, srcPrefix + "bin\\java.exe")); return; }
                foreach (var sub in SafeDirs(dir))
                {
                    string p = Path.Combine(dir, sub, "bin", "java.exe");
                    if (File.Exists(p)) list.Add(new JavaCandidate(p, srcPrefix + sub + "\\bin\\java.exe"));
                }
            }
            catch { }
        }

        private static string[] SafeDirs(string dir)
        {
            try { return Directory.GetDirectories(dir); }
            catch { return new string[0]; }
        }

        private static string FindJavaRecursive(string dir, int depth)
        {
            try
            {
                string direct = Path.Combine(dir, "bin", "java.exe");
                if (File.Exists(direct)) return direct;
                if (depth <= 0) return null;
                foreach (var sub in SafeDirs(dir))
                {
                    string r = FindJavaRecursive(sub, depth - 1);
                    if (r != null) return r;
                }
            }
            catch { }
            return null;
        }

        // =====================================================================
        //                     服务器控制
        // =====================================================================
        private void StartServer()
        {
            if (serverProc != null && !serverProc.HasExited) { Log("服务器已在运行。", false); return; }

            // 启动前执行环境自检（含自愈）。canStart 初始为 false，首启必走一次自检；
            // 若用户中途改过环境（如装了 Java），再次点击也会复检。自检通过后继续启动。
            if (!canStart)
            {
                Log("---------- 点击【启动服务器】→ 开始环境自检 ----------", false);
                RunSelfCheck();
                if (!canStart)
                {
                    Log("环境自检未通过，未启动服务器。请根据上方 [ENV] 提示处理后重试。", true);
                    return;
                }
            }
            if (string.IsNullOrEmpty(jarPath) || !File.Exists(jarPath)) { Log("未找到后端 jar，无法启动。", true); return; }

            // 选定 java：resolvedJava 或回退到原生查找
            string javaExe = resolvedJava;
            if (string.IsNullOrEmpty(javaExe))
            {
                var jh = Environment.GetEnvironmentVariable("JAVA_HOME");
                if (!string.IsNullOrEmpty(jh))
                {
                    var cand = Path.Combine(jh, "bin", "java.exe");
                    if (File.Exists(cand)) javaExe = cand;
                }
                if (string.IsNullOrEmpty(javaExe) || !File.Exists(javaExe)) javaExe = "java";
            }

            // === 安全：启动参数注入防护（防 launcher.ini 外部可控值逃逸引号注入 JVM 参数）===
            // jarPath 仅以双引号包裹，若路径含 " 可逃逸引号注入额外 JVM 参数（如 -javaagent: 触发 RCE）；
            // cfgProfile 直接拼接到 --spring.profiles.active= 后，若含空格/特殊字符可注入额外程序参数。
            // port 为 int 类型（仅数字）天然安全；此处仍做范围合理性校验。
            if (!IsSafeJarPath(jarPath))
            {
                Log("[SEC][失败] 后端 jar 路径含非法字符（如 \"、<、> 或控制字符），疑似被篡改，已拒绝启动。", true);
                return;
            }
            if (port < 1 || port > 65535)
            {
                Log("[SEC][失败] 端口号越界(" + port + ")，已拒绝启动。", true);
                return;
            }
            if (!IsSafeProfile(cfgProfile))
            {
                Log("[SEC][警告] launcher.ini 的 profile 含非法字符（仅允许字母数字 _ -），已退回默认 profile，避免参数注入。", true);
                cfgProfile = "";
            }

            // 强制 Java 以 UTF-8 输出 stdout/stderr（JDK 18+ 默认 UTF-8，但显式声明更稳妥），
            // 与下方 psi 的 StandardOutputEncoding/StandardErrorEncoding=UTF8 对齐，避免日志乱码。
            string encOpts = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8";
            // profile：launcher.ini 可设 profile=prod/pg 以激活 PostgreSQL 等生产配置；留空则用默认持久化 H2（单机免装库）。
            // #26 说明：jvmOpts 来自 launcher.ini（本机可写文件）。此处为简单拼接，
            // 本机持有 ini 写权限者可注入额外 JVM 参数——属本机风险（需物理/账号隔离方能防护），
            // 不引入复杂 CLI 解析器以避免破坏既有配置格式；生产建议审计 launcher.ini 写入权限。
            // JVM 参数必须位于 -jar 前（-jar 之后均为程序参数）；此处修正 #26 之前的顺序错误，
            // 确保 launcher.ini jvm=-Xmx512m 等内存参数真正生效。
            string javaArgs = encOpts +
                (string.IsNullOrWhiteSpace(jvmOpts) ? "" : " " + jvmOpts) +
                " -jar \"" + jarPath + "\"" +
                (string.IsNullOrWhiteSpace(cfgProfile) ? "" : " --spring.profiles.active=" + cfgProfile) +
                " --server.port=" + port;
            var psi = new ProcessStartInfo(javaExe, javaArgs)
            {
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                WorkingDirectory = Path.GetDirectoryName(jarPath),
                StandardOutputEncoding = System.Text.Encoding.UTF8,
                StandardErrorEncoding = System.Text.Encoding.UTF8
            };
            // P2-5 环境变量接线：注入 launcher.ini 的 env=KEY=VALUE（DB_*/APP_* 等），供后端 prod/pg 配置读取
            foreach (var kv in cfgEnv)
            {
                try { psi.EnvironmentVariables[kv.Key] = kv.Value; }
                catch { /* 无效变量名忽略 */ }
            }
            try
            {
                serverProc = Process.Start(psi);
            }
            catch (Exception ex)
            {
                Log("启动失败: " + ex.Message, true);
                return;
            }
            serverProc.EnableRaisingEvents = true;
            // 服务已启动：此后日志自动跟读到底部，让最新运行日志始终可见
            followLogTail = true;
            serverProc.Exited += OnServerExited;
            serverProc.OutputDataReceived += (s, e) => LogLine(e.Data, false);
            serverProc.ErrorDataReceived += (s, e) => LogLine(e.Data, true);
            serverProc.BeginOutputReadLine();
            serverProc.BeginErrorReadLine();

            SetStatus("● 运行中", Color.LimeGreen);
            Log("启动命令: " + javaExe + " " + SanitizeArgs(javaArgs), false);   // #25 脱敏：对含口令/密钥的 jvmOpts 打码
            Log("运行环境: " + resolvedJavaName, false);
            Log("服务器进程 PID: " + serverProc.Id + "，启动中，开始健康轮询……", false);
            btnStart.Enabled = false;
            btnStop.Enabled = true;

            // 启动就绪轮询
            healthTries = 0;
            healthTimer = new System.Windows.Forms.Timer { Interval = HealthIntervalMs };
            healthTimer.Tick += OnHealthTick;
            healthTimer.Start();
        }

        private void OnHealthTick(object sender, EventArgs e)
        {
            healthTries++;
            bool ready = ProbeHttp();
            if (ready)
            {
                if (healthTimer != null) healthTimer.Stop();
                Log("[ENV][通过] ✔ 服务器已就绪，端口 " + port + "（健康检查第 " + healthTries + " 次命中）。", false);
                SetStatus("● 运行中 · 已就绪", Color.LimeGreen);
                return;
            }
            if (healthTries % 5 == 0)
                Log("[启动] 健康检查第 " + healthTries + " 次，尚未就绪……", false);
            if (healthTries >= HealthMaxTries)
            {
                if (healthTimer != null) healthTimer.Stop();
                Log("[启动][警告] 超过 " + (HealthMaxTries * HealthIntervalMs / 1000) + "s 未收到 HTTP 响应，可能仍在初始化或启动异常。", true);
                SetStatus("● 运行中 · 未就绪", Color.Orange);
            }
        }

        // 健康检查：请求本应用首页，校验 HTTP 可达且返回体含本应用特征（标题“考试成绩管理系统”），
        // 从根本上杜绝“端口被无关/残留进程占用并返回 200 即误判就绪”的问题。
        private bool ProbeHttp()
        {
            string body;
            HttpStatusCode code;
            if (!TryFetchRoot(out body, out code))
            {
                // 连接被拒或异常：该端口尚无本应用在监听
                return false;
            }
            // 仅当返回体含本应用首页标题，才认定是本应用已就绪；
            // 否则（如别的 Web 服务、残留进程、或初始化中的 404 页）一律视为未就绪，继续轮询。
            return body != null && body.IndexOf("考试成绩管理系统", StringComparison.Ordinal) >= 0;
        }

        // 请求 http://localhost:port/ 并读取返回体。连接被拒（无响应）返回 false；
        // 端口有任意响应（含非 2xx，如初始化中的 404 页）均返回 true，交由调用方按返回体特征判定。
        private bool TryFetchRoot(out string body, out HttpStatusCode code)
        {
            body = null;
            code = 0;
            try
            {
                var req = (HttpWebRequest)WebRequest.Create("http://localhost:" + port + "/");
                req.Timeout = 2000;
                req.Proxy = null;
                req.AllowAutoRedirect = true;
                req.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
                using (var resp = (HttpWebResponse)req.GetResponse())
                {
                    code = resp.StatusCode;
                    body = ReadBody(resp);
                    return true;
                }
            }
            catch (WebException we)
            {
                if (we.Response == null) return false; // 连接被拒：端口无监听
                try
                {
                    using (var resp = (HttpWebResponse)we.Response)
                    {
                        code = resp.StatusCode;
                        body = ReadBody(resp);
                    }
                }
                catch { }
                return true; // 有响应（哪怕非 2xx），交由调用方按返回体特征判定
            }
            catch { return false; }
        }

        private static string ReadBody(HttpWebResponse resp)
        {
            try
            {
                using (var sr = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                    return sr.ReadToEnd() ?? "";
            }
            catch { return ""; }
        }

        private void StopServer()
        {
            StopHealthTimer();
            if (serverProc == null || serverProc.HasExited) { Log("服务器未运行。", false); return; }
            Log("正在停止服务器……", false);
            try { serverProc.Kill(); }
            catch (Exception ex) { Log("停止异常: " + ex.Message, true); }
        }

        private void StopHealthTimer()
        {
            if (healthTimer != null) { try { healthTimer.Stop(); healthTimer.Dispose(); } catch { } healthTimer = null; }
        }

        private void OnServerExited(object sender, EventArgs e)
        {
            StopHealthTimer();
            int code = 0;
            try { code = serverProc.ExitCode; } catch { }
            Log("服务器进程已退出 (退出码 " + code + ")。", code != 0);
            if (logWriter != null) { try { logWriter.Flush(); } catch { } }
            // 服务已停止：恢复顶部定位，便于用户回看完整日志
            followLogTail = false;
            SetStatus("● 已停止", Color.Gray);
            BeginInvoke((Action)(() => { btnStart.Enabled = canStart; btnStop.Enabled = false; }));
        }

        private void OpenBrowser()
        {
            try
            {
                Process.Start(new ProcessStartInfo("http://localhost:" + port) { UseShellExecute = true });
            }
            catch (Exception ex) { Log("打开浏览器失败: " + ex.Message, true); }
        }

        // ---------- 日志 ----------
        private void Log(string text, bool isError)
        {
            if (text == null) return;
            string line = DateTime.Now.ToString("HH:mm:ss") + "  " + text;
            if (logWriter != null) { try { logWriter.WriteLine(line); logWriter.Flush(); } catch { } }
            AppendUi(line, isError);
        }

        // 支持链式调用占位（保留原有调用）；本项目直接使用 Log
        private void LogLine(string text, bool isError)
        {
            if (text == null) return;
            Log(text, isError);
        }

        private void AppendUi(string line, bool isError)
        {
            // UI 尚未就绪（InitComponents 之前）时先缓冲，避免访问 null 控件导致整个进程崩溃
            if (logBox == null)
            {
                if (pendingLogs != null) pendingLogs.Add(line);
                return;
            }
            if (logBox.InvokeRequired)
            {
                BeginInvoke((Action)(() => AppendUi(line, isError)));
                return;
            }
            // 多行 TextBox 不支持按字符着色，改用 [信息]/[错误] 文本前缀区分级别
            string prefix = isError ? "[错误] " : "[信息] ";
            logLines.Add(prefix + line);
            ApplyLogDisplay();
        }

        // 将 InitComponents 之前的缓冲日志刷入已就绪的日志控件。
        // 注意：此处不调用 ScrollToCaret()——本方法在窗口 handle 尚未就绪时执行，
        // 对滚动定位无效且易留下异常滚动位置（导致首行显示不完整）；滚动定位统一
        // 放在 Shown 事件（handle 已就绪）与 AppendUi（追加新行时）里处理。
        private void FlushPendingLogs()
        {
            if (pendingLogs == null || logBox == null) return;
            foreach (var l in pendingLogs) logLines.Add(l);
            pendingLogs.Clear();
            ApplyLogDisplay();
        }

        // 把全量日志缓冲渲染到日志控件。核心思路：**不再依赖 ScrollToCaret 向上滚动**。
        // 未运行(followLogTail=false)：整体重设 logBox.Text —— TextBox 赋文本后天然从第一行
        //   显示（内建行为），顶部首行必然完整可见，从根上绕开“该环境上滚定位不可靠”的历史问题；
        // 运行中(followLogTail=true)：增量追加最后一行 + 滚到底，保证最新日志可见（跟读，性能好）。
        private void ApplyLogDisplay()
        {
            if (logBox == null) return;
            try
            {
                if (followLogTail)
                {
                    if (logLines.Count == 0) return;
                    logBox.SelectionStart = logBox.TextLength;
                    logBox.AppendText(logLines[logLines.Count - 1] + "\r\n");
                    logBox.ScrollToCaret();
                }
                else
                {
                    var sb = new StringBuilder();
                    foreach (var l in logLines) sb.Append(l).Append("\r\n");
                    logBox.Text = sb.ToString();
                    logBox.SelectionStart = 0;
                    logBox.SelectionLength = 0;
                }
            }
            catch { }
        }

        private void SetStatus(string text, Color color)
        {
            if (this.InvokeRequired) { BeginInvoke((Action)(() => SetStatus(text, color))); return; }
            statusLabel.Text = text;
            statusLabel.ForeColor = color;
        }

        // ---------- 托盘/窗口 ----------
        private void ShowWindow()
        {
            BeginInvoke((Action)(() =>
            {
                Show();
                WindowState = FormWindowState.Normal;
                BringToFront();
                trayIcon.Visible = false;
            }));
        }

        // ---------- 开机自启 ----------
        private const string RUN_KEY = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string RUN_VAL = "ExamScoreServer";

        private bool IsAutoStartEnabled()
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RUN_KEY, false))
                {
                    if (key == null) return false;
                    var v = key.GetValue(RUN_VAL);
                    return v != null && v.ToString().IndexOf(Application.ExecutablePath, StringComparison.OrdinalIgnoreCase) >= 0;
                }
            }
            catch { return false; }
        }

        private void SetAutoStart(bool enable)
        {
            // 幂等：当前注册表状态与目标一致则跳过，避免代码初始化/重复触发时冗余写注册表
            if (IsAutoStartEnabled() == enable) return;
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RUN_KEY, true))
                {
                    if (key == null) { Log("无法访问注册表 Run 键。", true); return; }
                    if (enable) key.SetValue(RUN_VAL, "\"" + Application.ExecutablePath + "\" /autostart");
                    else key.DeleteValue(RUN_VAL, false);
                }
                Log(enable ? "已启用开机自动启动。" : "已取消开机自动启动。", false);
            }
            catch (Exception ex) { Log("设置开机自启失败: " + ex.Message, true); }
        }
    }
}
