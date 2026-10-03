# Android 自动发布工作流检查点

## v3.0.11 同版本修补交付（2026-10-04）

状态：VERIFIED（仅以下实际验收范围）。已通过工作流更新既有 v3.0.11 四 ABI APK；未创建新版本、Release 或 Tag，未增加 versionCode。完整自适应工程仍为 IMPLEMENTED，不能将本轮交付等同于所有页面和全部设备矩阵完成。

- 正式源码：`99c2c3ce3290668ca4d7123e71f551590d65e4de`；Android CI [37135395678](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37135395678) success。签名准备 [运行 37135681053](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37135681053)、附件替换 [运行 37137618497](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37137618497) 均 success。最终文档随后单独提交、同步 dev；APK 来源固定为上述源码提交。
- 既有 Release ID 402442837、公开正式/latest 状态及 target_commitish 保持不变；Tag 对象仍为 `b34c36013e063ce280334a261ed2c877609741e1`。最终恰好四个标准 ABI 附件，已核对新附件 ID、大小、GitHub digest，并重新下载四个公开 APK，完整 SHA-256 与候选清单一致，暂存/备份附件已清理。原正文保留并追加来源与哈希。
- 本地核对整个准备 artifact 的 GitHub SHA-256、ZIP 与路径；八个新旧 APK 均通过 ZIP/资源、包名 top.imsyy.splayer.android、3.0.11 / 30017、单一正确 ABI、非 debuggable、WebView debugging=false、正式证书检查。签名 SHA-256：`d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea`。
- MuMu 手机 SM-A5560 与平板 ALT-AL10（API 35）分别使用正式 ARM64 候选包执行 adb install -r，均 Success，实际启动 Status: ok、进程存在，启动后当前进程日志未出现 FATAL EXCEPTION。firstInstallTime 分别保持 2026-10-03 15:20:07、2026-10-01 18:11:04；未清除正式包数据。此项是覆盖升级/启动检查，不代表所有真实音源或用户数据语义均已验证。
- 补充双端导航/cutout 矩阵：每端真实启用手势与三键导航 overlay，各自启用模拟 corner cutout，四套完整仪器测试均 OK (7 tests)，合计 28 次测试执行。覆盖五档实际字体 × 横竖屏，合计 120 个播放器、40 个共享组件几何场景和 80 次列表中部/末尾检查；已检查 Activity PixelCopy 截图。测试使用隔离包，完成后卸载隔离包/仪器包并恢复原导航/cutout、fontScale=1.0、旋转设置，正式包保留。
- 最终文档提交前再次执行 pnpm build:android、pnpm format，均成功；pnpm lint 零错误/警告。32 项 TS、26 项 Python 发布保护、18 项 JVM 回归及远端源码 CI 通过。原生 Lint 历史 187 errors / 27 warnings，Vite 已有 ffmpeg.wasm URL/大 chunk 提示继续保留，不能称全项目零告警。
- 未完整验证：API 29、物理 cutout、真实系统分屏生命周期、AMLL 全内容矩阵、物理触摸拖拽/重排、真实音源全流程及所有页面大字号。极端字号下最近播放页仍依赖页面滚动，歌曲标题沿用省略/提示，其他页面仍可逐步迁移共享控件。
- 正式产物、旧包备份与本地证据：`C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10146-a8d1-7132-a8a7-785cf718820d/patch-final`；正式 APK 在 `bundle/new/`，旧包在 `bundle/old/`。准备 artifact 保留 90 天，本地备份独立保留。候选、双端覆盖升级、导航矩阵、最终公开附件的 JSON/log/截图均在该目录。

| APK                         | 字节数     | SHA-256                                                            |
| --------------------------- | ---------- | ------------------------------------------------------------------ |
| app-arm64-v8a-release.apk   | 60,023,212 | `4abf816fd226131f2ba97a7e58f1eec1e466d781d3c5483e2941e8d0dfa809a7` |
| app-armeabi-v7a-release.apk | 59,227,554 | `7e97e4d0fe79c954ee389993d1f1f31808a59e90e3ed7db708580f1ecd48d522` |
| app-x86-release.apk         | 62,329,770 | `7caf0a46533b615bb45eb4bd51299e99cd4317dcd83d02fdf1d4e1a4c2b5a51e` |
| app-x86_64-release.apk      | 65,576,859 | `7ff1ed76736ecfb02704f6f339be3de0472798be4d3e420b5712cb371477afb3` |

## v3.0.11 最终修补发布阶段（2026-10-03）

状态：IMPLEMENTED；同版本修补工作流及本地保护测试已实现，尚未更新公开附件。用户已授权更新现有 v3.0.11 APK，不创建新版本，并同步仓库。

- 新增 Android Same-Version Patch 的 prepare/apply 两步流程。准备阶段依赖源码完整提交及成功 Android CI，验证现有正式 Release 四 ABI，签名构建候选包；对比新旧包名、versionName、versionCode、证书、ABI，并验证 ZIP、非 debuggable、WebView debugging=false。
- 不可变 artifact 保存旧包、候选包、原正文和身份快照，保留 90 天。实际双端候选升级后 apply 重新验证 artifact 来源、Tag 引用、Release ID、正文及附件 digest；四个暂存包全部上传校验后才切换标准名称。失败恢复旧附件/本次正文；完成后清理旧附件备份，清理失败可重跑。未知附件与外部正文保守保留。
- 11 项新增修补保护、15 项既有发布保护测试通过；共享组件源码 1a65f64b 的 Android CI 37133555718 已确认 success。本地 pnpm build:android 成功；格式化及最终 ESLint 检查随后记录。版本保持 3.0.11 / 30017；原 Tag 对象 b34c36013e063ce280334a261ed2c877609741e1 未改变。
- 后续：提交并推送工作流，等待当前源码 CI，执行 prepare、下载验证正式候选包、MuMu 手机和平板同版本覆盖升级验收，再执行 apply、检查公开附件/哈希/Tag、更新最终文档和检查点。已有真实系统分屏、API 29、物理 cutout、AMLL 全矩阵及全页面大字限制继续保留，不声称完整真机验收。

## v3.0.11 共享控件与歌曲列表修补（2026-10-03）

本轮阶段：共享控件、迷你播放栏、歌曲列表与播放队列。状态：VERIFIED（仅下述范围）；完整自适应任务仍为 IMPLEMENTED。正式版本保持 3.0.11 / 30017，未创建版本、Tag、Release，也未替换公开 APK。

- 新增 Naive UI AdaptiveButton/AdaptiveTag，按钮支持内容换行，图标尺寸独立约束；交互区域下限 48 CSS px。接入 MainPlayer、SongCard 和播放队列，其他页面未全面迁移。
- 迷你播放栏按实际容器及字号重排，紧凑布局保留前后曲和播放，随机/循环进入明确的更多菜单；宽度足够的矮窗口使用单行。歌词行自然增高，AppLayout 使用实际栏高预留空间。队列数量进入流式布局，通过 Naive UI 徽标值插槽显示完整文字，避免固定数字动画尺寸的大字裁切与重叠；播放器其他既有数量徽标保留原行为。
- 歌曲标签与歌手分行，行、表头、队列头尾按内容增高。歌曲标题保留原有单行省略及提示行为，不声称所有长标题完整铺开。队列根据窗口选择底部/侧边布局，短窗口增加文案横向空间。
- VirtualScroll 按真实 data-index 测量，修复 keyed refs 顺序错配；shallowRef 累积位置显式触发更新，字体变化刷新估计并保留行锚点。动态定位取消动画，末尾旧偏移钳制到最后一行，避免回退第 0 行并渲染全部 200 首歌曲。相关 4 项边界测试进入 Android CI。
- 已执行 pnpm format、pnpm build:android、pnpm lint（零错误/警告）、pnpm typecheck:web；32 项 TypeScript、15 项发布保护、18 项 JVM 测试通过。普通及隔离 debug APK、仪器包构建成功。原生 Lint 报告仍为历史 187 errors / 27 warnings，不能视为全项目零错误。
- 前端构建仍包含已有 ffmpeg.wasm 运行时 URL 与大 chunk 提示，构建成功不代表没有打包提示。最终普通 ARM64 APK 为 64,279,899 字节，SHA-256：b3f0f29d500880d16d8793ae81496fe0de27874288dc0189658784c5722c7f2a；四 ABI ZIP/资源、包名 top.imsyy.splayer.android.debug、3.0.11-debug / 30017 均验收通过。隔离测试包使用 .lyricsverify。
- 双端专项矩阵通过：MuMu 手机 SM-A5560 与平板 ALT-AL10，API 35；ADB 重连后使用 127.0.0.1:16384 / 16416。每端五档实际系统字号 1.0/1.15/1.3/1.5/2.0 × 横竖屏共 10 场景，覆盖长中英日/emoji、标签、48px 核心操作、200 首列表首部/中部/末尾、100 首队列长文案与移除。已检查实际 Activity PixelCopy 截图，未使用模拟器桌面截图冒充应用结果。
- 失败与修正：测试 fixture 改用真实 history 数据入口；排除 display:none 节点后验证可见按钮。设备发现动态定位动画重叠、末尾范围回退、队列固定 footer 裁切和徽标行盒裁切，均修复并加入实际回归。旧桌面歌词测试失败现场 playing=false，确认网页恢复的暂停输入干扰原生绘制；仅在该隔离测试卸载网页并保留 Activity，诊断记录显示 progress 302→2503、playing=true，定点测试通过。较长英文样本保证平板宽窗口也溢出；曾直接关闭 Activity 导致旋转不变，改为保留原生窗口。失败日志保留，未计入通过。
- 产物与证据目录：C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10146-a8d1-7132-a8a7-785cf718820d/shared-stage。包括四 ABI 普通 debug APK、隔离 ARM64/仪器 APK、双端套件日志、截图、ZIP/包名/版本/哈希检查及 JVM/Lint 报告；正式包未清数据。
- 最终交付 APK 双端完整仪器套件各 OK (7 tests)，日志 phone-shared-final.log / tablet-shared-final.log；每端 10 个共享场景、30 个播放器场景和 20 次中部/末尾滚动检查，数量文字本体边界及队列移除通过。实际截图确认完整数量 100。两端隔离包及仪器包已卸载；fontScale=1.0、user_rotation=0、accelerometer_rotation=1，正式包及原有平板 debug 包保留。应用 display 策略由测试 finally 恢复；Activity 结束后 display 7 不可查询，清理记录明确该限制。
- 剩余：真实系统分屏、API 29、物理 cutout、AMLL 内容矩阵、触摸拖拽/重排及所有页面大字号仍未完成完整验收。极端字号下最近播放页仍依赖页面滚动访问内容，不声称整个页面无需滚动。最终阶段准备同版本修补工作流，替换现有 v3.0.11 四 ABI 正式 APK并记录源码/签名/哈希，不移动 Tag、不创建新版本。本阶段验证、提交并同步 dev 后停止。上一阶段 Android CI 37119748685 已确认成功；本轮推送后单独核对远端 CI。

## v3.0.11 播放器与独立歌词入口修补（2026-10-03）

本轮阶段：播放器布局与独立桌面歌词入口。状态：VERIFIED（仅下述范围）；完整自适应任务仍为 IMPLEMENTED。版本仍为 3.0.11 / 30017，未创建版本、Tag、Release，也未替换公开 APK。

- 宽屏播放器改为实际顶栏、内容、底栏流式预算；封面测量剩余容器，长歌曲信息局部滚动。控制栏按内容增高，窄窗口或大字号时重排次要操作；核心触控下限为 48 CSS px，SVG 尺寸独立约束。手机竖屏的控件规格依据当前容器宽度。
- 手机和平板全屏播放页共享独立 Naive UI 桌面歌词按钮与 DesktopLyric2 图标，紧凑布局保留图标，宽裕布局显示标签。按原生权限及实际窗口确定 OFF/ON/PERMISSION_REQUIRED，初始化与操作期间禁用；窗口附着后才提示开启，服务停止和前台恢复校准状态。
- 权限流程先注册前台恢复监听再打开设置，返回后重新检查并继续开启；开关串行保护。不修改音频引擎、用户数据库或版本配置。
- pnpm build:android、pnpm format、pnpm lint、pnpm typecheck:web 通过；28 项 TS、15 项 Python、18 项 JVM 回归通过。Gradle 隔离包、仪器包与普通四 ABI debug 构建通过。
- MuMu 手机 emulator-5554、平板 emulator-5556 最终完整仪器套件各 OK (6 tests)。新增测试涵盖 1.0/1.15/1.3/1.5/2.0 五档真实系统字号、纵横屏、cover/record/fullscreen 共 60 组布局几何，使用长标题、长歌手/专辑及中英日/emoji 文本；检查封面与信息、核心控件、窗口边界和图标盒。手机紧凑页沿用自身封面呈现策略，三种设置不能视为三种独立渲染实现。
- 实际悬浮窗口开启/关闭、外部停止服务后 OFF、缺权限状态、点击确认进入 com.android.settings、返回后自动 ON 均通过。测试用 shell 改变隔离包 appops 授权，再实际返回应用；不是人工操作系统授权开关，也不是物理触摸测试。
- 首次启动测试因包名漏写 .android 而失败，检查 APK 与注册信息后纠正。首轮几何与权限通过；补强实际授权页断言后完整套件通过。最终新增 PixelCopy 从被测 Activity 窗口取双端四张 2.0 字号截图，避免 MuMu 默认 display 截图抓到桌面；最终完整套件再次通过。
- 新状态查询最初增加两项 Media3 opt-in Lint 错误，已在该方法明确 opt-in；最终 Lint XML 恢复历史 187 errors / 27 warnings，不宣称全项目零错误。此前基础层 dev CI 37117629027 已实际成功。
- 隔离验收与普通 debug APK、最终 Lint 报告和实际窗口截图保存于 C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10146-a8d1-7132-a8a7-785cf718820d/player-stage；本轮仪器、构建和格式日志位于其父目录 player-\*.log。
- 普通 ARM64 debug：android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk，64,277,827 字节，top.imsyy.splayer.android.debug / 3.0.11-debug / 30017；SHA-256 fd9edecbbf2bf8400dfeb3b2cde593bb609d59f15c549daf80004a7f9d8b029e。这是测试安装包，不是正式修补 Release。
- 双端字号恢复 1.0、user_rotation=0、accelerometer_rotation=1。本阶段结束清理自己创建的 .lyricsverify 与仪器包，保留正式应用及其数据。
- 本轮不代表播放真实音源、进度拖动、评论所有模式、AMLL、快速开关/拒绝返回、完整导航/cutout、系统分屏、API 29 与物理设备已验收。共享 Button/Chip、MiniPlayer、SongItem 和其余页面迁移仍待后续阶段；最终按已授权工作流更新现有 v3.0.11 四 ABI APK，保持 Tag、不创建新版本。
- 本阶段完成后提交并同步 dev，再停止；下一阶段为共享组件迁移及相关双端回归。

## v3.0.11 自适应基础层修补（2026-10-03）

本轮阶段：自适应基础层。状态：VERIFIED（仅下述已实际检查的范围）；完整自适应任务仍为 IMPLEMENTED。方案 ADAPTIVE_UI_PLAN.md 已获用户批准。用户要求最终更新现有 v3.0.11 APK 并同步仓库，不创建新版本；本阶段没有修改版本、Tag 或 Release 附件。

### 实现

- 新增 src/core/layout/adaptive.ts：宽度等级 600/840、高度等级 480/900、双栏内容预算和有限语义 token。新增 useAdaptiveLayout 容器 ResizeObserver 接口，复用 useDevice 的窗口与字号单例，没有新增逐帧窗口计算。
- useDevice 的硬件身份继续供方向控制使用；UI 双栏依据可用窗口决定，自动模式需至少 840×480 CSS px。强制手机保留单栏；强制平板也要求至少 600×480，窄窗口不再硬塞双栏。
- App 将语义间距、字号和触控下限写入 document 根节点，让 Teleport 播放器和弹层可继承；窗口等级与系统字号作为内部 data 属性可用于诊断。
- MainActivity 不再固定 WebView textZoom=100，统一按系统 fontScale 设置；字体配置变更保留 Activity，启动/恢复/配置变化刷新。AndroidNativePlayback 提供配置查询与变化事件，前端初始化先订阅再查询，卸载移除监听。网页 token 保持基准字号，避免双重放大。
- 未修改音频服务、数据库、歌词引擎与用户设置 schema。useMobile 的历史断点、播放器/列表具体内容布局与页面缩放安全区兜底留待后续阶段迁移。

### 验证

- pnpm build:android、pnpm format、pnpm lint、pnpm typecheck:web 均通过；清理构建生成与全库格式化带来的无关差异。28 项 TS 回归、15 项 Python 发布保护、18 项 JVM 测试通过。
- Gradle assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 执行完成。Lint XML 仍是历史 187 errors / 27 warnings，不能称全项目零错误。
- MuMu 手机 emulator-5554、平板 emulator-5556 的最终完整仪器套件分别 OK (4 tests)：根节点 token 继承、真实字体、真实窄窗口、播放器旋转边界和桌面歌词回归。使用 .lyricsverify 隔离包，未清除正式应用数据。
- 系统字号 1.0/1.15/1.3/1.5/2.0 实测：20px 基准文字的 computed font-size 分别为 20/23/26/30/40px，两端均一致；实际文字高度随之增大，WebView textZoom 分别为 100/115/130/150/200。
- 最终实际窗口：手机 640×360 缩至 400×360，保持 phone 单栏；平板 1098×618 缩至 400×618，从 pad 双栏回退 phone 单栏。Activity 窗口缩小不等同于系统分屏生命周期完整验收。
- 首次原生命令因 PowerShell 将未引用的 -PverificationSuffix=.lyricsverify 拆成任务而失败；完整引用参数后成功。首轮完整套件通过，但早期字体 logcat 被环形缓冲覆盖，因此加强测试将测量直接写入仪器输出，并补充“实际宽度必须缩小”断言；最终套件已双端重跑通过。
- 最终隔离包与测试包已移除；两端 font_scale=1.0、user_rotation=0、accelerometer_rotation=1，显示仍为 1080×1920、手机 480dpi/平板 280dpi，正式包保留。

### 产物与后续

- 最终普通 debug APK：android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk，64,275,419 字节；另含其余三 ABI。ARM64 SHA-256：664b6fb6890f1dfb976c29b78cb9b098965e3b06f4d8b329ef443dd65889fb99。普通 debug 为测试用途，不属于正式修补 Release。
- 日志、最终隔离验收包、常规 debug 包、仪器输出、恢复状态与原生报告：C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10146-a8d1-7132-a8a7-785cf718820d/phase1。
- 完整大字内容排版、中英日按钮文案、导航/cutout 新改动完整矩阵、真实系统分屏、API 29 与物理硬件仍待后续阶段验收；本次字体探针测量不能替代这些验收。
- 本阶段完成后提交并同步 dev；不自动执行下一阶段或发布。下一阶段：播放器按容器预算迁移、共享控件与独立桌面歌词按钮；最后再实施现有 v3.0.11 的显式修补工作流。

日期：2026-10-03；分支：dev。

## 工作流（VERIFIED）

- Android CI：dev push/PR，JDK 21、pnpm 10.28.1、Gradle 缓存，资源构建、发布保护测试、ESLint、debug 编译/单元测试/Android Lint。
- Tag Release：v\* 触发，检出 Tag，正式签名 Secrets 注入，Gradle 版本校验，所有 release ABI/flavor 元数据收集，真实 APK 清单与证书校验。
- Release 幂等处理：同 Tag 串行，已公开 Release 跳过，草稿上传完成才公开；已有附件校验 SHA-256，不覆盖。
- 已替换原手动 android-release.yml；配置说明在 .github/ANDROID_RELEASE_SECRETS.md。

## VERIFIED

- 三个 Actions YAML 语法、触发条件、CI 只读/发布写权限及格式检查。
- Python 语法与 12 项发布保护测试，包括草稿 API 回退、分页、权限错误及恢复时的 Tag 提交检查。
- pnpm lint：通过；pnpm build:android：通过；pnpm format：通过。清理构建生成及全库格式化带来的无关已跟踪文件变化。
- 实际 Gradle 命令 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug：通过（首次 offline 因测试依赖缺缓存失败，联网解析后通过）。
- 临时测试证书的 :app:verifyReleaseConfiguration :app:assembleRelease：通过，四个 ABI 均生成 APK。
- 实际收集脚本：四个 APK 清单、版本与签名验证通过（本机验证只适配了 Windows 工具路径，CI 原脚本使用 Linux 工具）。
- Gradle 负向验证：v1.3.0 与 versionName=3.0.8 不一致时明确失败。
- 没有已跟踪 keystore/key.properties；临时测试签名材料与测试复制附件已清理。

## GitHub 实际发布（VERIFIED）

- 已确认四项 Secrets 已配置并由 runner 实际用于正式签名，没有读取或导出 Secrets 内容。
- dev CI：37095215774、修复后的 37096060939 均实际通过。首次环境配置失败为 setup-android 默认请求已移除 tools 包，已显式配置 platform-tools 修复。
- v3.0.9 Tag 保持指向 d6cf53c2；版本为 3.0.9 / 30015。首次 Release 37095509169 因按 Tag API 未返回新草稿而失败，留下空草稿。修复发布工具位于 dev 提交 a97c56f1。
- 恢复发布 37096228847（workflow_dispatch）成功，原草稿转为正式公开 Release：draft=false、prerelease=false、标题 v3.0.9，自动生成 Release Notes。
- 正式附件只有四个 APK：SFA-3.0.9-release-abi-arm64-v8a.apk（60001030 字节）、armeabi-v7a（59205372）、x86（62307588）、x86_64（65554677）。不含 debug 或中间文件。
- 下载正式 arm64 APK 并检查 SHA-256 与 GitHub 附件 digest 相同，真实清单为 versionName=3.0.9、versionCode=30015、非 debuggable；证书与 v3.0.8 相同，SHA-256 为 d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea。
- 同一发布运行的 attempt 2 实际成功，构建和上传均跳过；四个附件 ID、数量、大小及哈希均未变化，确认没有重复上传。
- 本机 build 目录中的先前 release APK 使用临时测试证书，不属于正式发布产物；正式安装包请从下方 Release 下载。下载核验使用的临时文件已清理。

发布地址：https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.9

CI：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37096060939

发布/重跑：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37096228847

## 下一步

本轮发布完成。下次修改 Android 与前端版本并递增 versionCode，推送 dev 等待 CI，通过后推送新 Tag。若旧 Tag 发布工具需要修复，使用 dev 的 Android Release 手动恢复入口，保留旧 Tag 源码。

## 发布格式修正（2026-10-03，VERIFIED）

- 已读取 v3.0.8、v3.0.7 的真实发布正文及附件名称；v3.0.9 现在沿用下载与安装表格、分类更新日志、Full Changelog 链接，并保留自动生成的版本对比链接。
- v3.0.9 四个附件已原地改名为 app-arm64-v8a-release.apk、app-armeabi-v7a-release.apk、app-x86-release.apk、app-x86_64-release.apk；Release ID、附件 ID、字节大小、SHA-256、正式状态及 Tag 提交均保持不变，没有重建或重新上传 APK。
- 后续工作流保留 AGP 最终 APK 文件名；发布模板为 .github/RELEASE_TEMPLATE.md，从 CHANGELOG.md 读取当前版本分类日志，并继续调用 GitHub 自动生成发布记录。缺少该版本日志或 APK 名称重复时停止发布。
- Python 15 项保护测试通过，新增历史文案格式、当前版本日志提取、缺失日志拒绝以及 flavor 文件名保留检查。
- YAML 语法检查通过；HEAD 加本次发布改动的隔离副本执行 pnpm lint、pnpm build:android、pnpm format 均通过。主工作区 lint 被另一路临时 .verify-player.cjs 的 require 规则错误影响，因此没有删除或修改该脚本。
- 并行播放器改动及临时验证脚本保留；本次提交仅包含发布脚本、模板、对应测试和文档。

## 手机播放页面 UI 修复（2026-10-03）

状态：IMPLEMENTED；下列构建、状态测试与浏览器场景为 VERIFIED，真机矩阵仍未验证。分支 dev。

- FullPlayerMobile：顶栏、内容与底栏都参与纵向布局；评论页不挂载底栏和频谱；安全区统一沿用项目变量。封面根据真实剩余区域 ResizeObserver 测量缩小，Metadata 最大占内容区域 60%，过多时独立滚动。
- useMobilePlayerControls：复用 useTimeoutFn 和 PlayerMetaHold 接口，页面隔离的 4 秒计时；多指拖动、菜单 hold 不隐藏；切页恢复；点击捕获不消费事件，隐藏时不在 pointerdown/pointerup 阶段移动命中目标。程序自动滚词不重置计时。
- PlayerComment：可单独淡出歌曲卡片，评论列表一直存在；卡片长标题不挤压操作按钮。FullPlayer：手机竖屏不使用父级点击拦截层。
- 状态回归 6 项通过：隐藏/恢复/重置、多指拖动、嵌套 hold、切页、失焦、卸载、隐藏时点击命中保护，以及浏览器接管触摸滚动后 pointercancel 不提前结束保护。
- 浏览器实际 Vue 页面：360×640、360×800、480×1067 CSS 视口（对应需求中常见 DPR 下四组物理分辨率），分别模拟 16/48px 导航安全区、30px 顶部和左右 8px cutout；长标题、多歌手、长专辑和多个标签的封面、信息、底栏无重叠。320×480 极小视口中 Metadata 可滚动到专辑，仍不进入底栏。
- 浏览器交互：歌词初始显示、4 秒淡出、点击恢复、长时间按住进度区域不隐藏、抬起后计时正常；评论无底栏、卡片淡出/恢复、评论可用高度增加。
- 普通歌曲的普通封面与唱片封面模式均已浏览器验证，封面、歌曲信息和专辑不进入底栏。
- 已执行 pnpm build:android、pnpm typecheck:web、pnpm lint、pnpm format；Android :app:assembleDebug 与 :app:testDebugUnitTest 通过；:app:lintDebug 任务返回成功，但 XML 报告仍有 205 errors / 33 warnings（203 UnsafeOptInUsageError、2 WrongConstant，涉及本轮未修改的原生播放代码），不视为零错误通过。格式与 ESLint 排除生成资源及临时验证目录。
- 未验证：真实手机手势/三键导航、系统 cutout inset 注入、真实后台音频播放、歌词长按/跳转在 Android WebView 的硬件交互。未修改 Service、队列、MediaSession 或音频引擎。
- 普通按钮、图标、顶栏最小触摸尺寸和封面首选上限仍使用固定 CSS px；没有固定底部占位高度或针对单机型的遮挡补丁。

- 最终 debug APK：`android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`（64,723,584 字节），另含 armeabi-v7a、x86、x86_64；仅用于测试，未执行正式发布或真机安装。

## v3.0.10 发布验证（2026-10-03）

状态：VERIFIED；版本 3.0.10 / versionCode 30016 已正式发布。

- 追加修复：底栏 grid 使用 minmax(0, 1fr)，按钮根据剩余宽度收缩，240px 极窄 WebView 下分页区域仍位于屏幕内。
- 独立验证包 top.imsyy.splayer.android.uiverify，与模拟器现有正式包和 debug 包并存，没有清除已有应用数据。仅验证包生成资源启用 CDP；正式配置保持 WebView debugging=false。
- 实际 Android APK：720×1280、1080×1920、1080×2400、1440×3200；手势导航与三键导航各一轮，封面、Metadata、底栏均无重叠，分页横向边界正确。
- 真实触摸：歌词初始显示，闲置淡出，点击恢复；纵向拖动超过 4 秒仍显示，松手后再次隐藏。Android 合成点击有延迟，验收在末次事件与动画完成后进行。
- 评论用固定 API/健康检查响应隔离独立包内嵌服务环境：评论文字、时间、点赞计数持续显示，歌曲卡片可隐藏/恢复，完整底栏不挂载。
- 真实原生播放：私有目录的静音 WAV 测试文件通过现有 Native Playback 插件播放；评论页和歌词 UI 隐藏时仍播放；返回桌面 6 秒后回到 APK，positionMs 从 20135 增至 27189，playing=true。
- 测试后恢复模拟器物理分辨率、密度与原导航 overlay；停止测试音频；验证包、临时音频和验证脚本均已清理，截图与日志保存在仓库外的本轮验证目录。
- 本地播放页状态测试 6 项、发布保护测试 15 项、Vue 类型检查与 ESLint 均通过；Android CI 增加播放页类型与状态测试。
- Android Lint 的历史原生代码 205 errors / 33 warnings 仍保留，不修改播放服务来消除报告。

### 正式发布结果

- 发布源码提交：c8ec525c54d2d3480c10247ab30820dbb57ab13a；Tag v3.0.10，未移动或覆盖已有 Tag。
- Android CI 成功：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37100263662。
- 首次发布在创建草稿后读取不到草稿而停止（37100489935）；检查确认草稿指向正确提交且无附件，使用既有 workflow_dispatch 恢复入口，恢复流程成功：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37100748373。
- 正式 Release：https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.10；isDraft=false、isPrerelease=false，四种 ABI 的正式签名 APK 均已上传。
- 下载后的 ARM64 附件：包名 top.imsyy.splayer.android、versionName 3.0.10、versionCode 30016、非 debuggable；WebView debugging=false。
- ARM64 文件 SHA-256：291cbe52a888a725993ca4abd484311080d5a2fe06f4431125c3df902f0517ab，与 GitHub 附件摘要一致；签名证书 SHA-256：d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea，与上一正式版本一致。
- 下载验收产物：C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10006-c2d5-7883-814d-cb84b0841a46/app-arm64-v8a-release.apk。
- 剩余验证边界：Android 验收使用模拟器；真实硬件 cutout 注入、歌词长按的全场景回归仍未验证。评论内容使用固定响应，未声称真实评论接口验收成功。

## 桌面歌词与手机横屏专项（2026-10-03）

状态：IMPLEMENTED；下列实际执行的检查为 VERIFIED。目标分支 dev；用户已批准专项方案。本专项完成后停止，不自动发布。

- 桌面歌词拆分交互状态、布局/滚动策略和完整文本塑形缓存；唯一状态为 IDLE、CONTROLS_VISIBLE、DRAGGING、LOCKED。锁定立即移除控制背景，独立解锁按钮保持可用；4 秒无操作渐隐，销毁取消计时与动画。
- 默认自动字号综合安全区域、窗口宽高与系统 fontScale，范围 16–32sp；手动字号保持设置数值，不因歌词长短缩放。首次默认颜色 #6BB2FF；历史字号和颜色保守保留，统一配置入口与原生恢复快照，未改变数据库。
- 长句完整 StaticLayout 塑形后裁剪平移，650ms 起始停留；滚动结合溢出宽度和行时间，速度上限 90dp/s。短句不滚动，pause 冻结，seek/切歌重置；缓存测量与渐变矩阵，支持中文、英文、日文、emoji、RTL。
- 位置保存为安全可移动区域中的归一化锚点，重新测量 metrics/insets 后投影及钳制；旧像素坐标按当前屏幕迁移。旧坐标缺少历史屏幕尺寸，不能精确恢复旧比例。
- 手机横屏使用独立流式布局，顶栏/底栏常驻，左右内容使用 minmax(0, …)，封面按真实剩余高度测量，元信息可滚动。复用队列、音量、更多、歌词设置与播放按钮；窄窗口换行/局部滚动。修复封面类名误命中歌曲信息组件的问题。
- pnpm format、pnpm lint、pnpm typecheck:web、pnpm build:android 已执行成功。相关 JavaScript 22 项与 Android JVM 18 项测试通过；隔离包原生仪器测试 OK (1 test)，覆盖语言样本、固定字号、实际绘制滚动/暂停/seek、锁定/解锁/超时及设置重开恢复、旋转调用后的边界与实例检查。
- Android assembleDebug、testDebugUnitTest、lintDebug 任务执行成功；Lint 实际 XML 报告仍为 187 errors / 27 warnings（历史原生代码），专项 FloatingLyric 文件无报告问题。不能视为全项目 Lint 零错误通过。
- 浏览器九种视口覆盖横屏、竖屏和强制手机模式，并注入组件安全区；封面/元信息/底栏无重叠。扩展检查验证普通/唱片封面、元信息可滚动到专辑、队列、更多菜单、Default/AMLL 与平板共享控件。
- 实际 APK 隔离包 top.imsyy.splayer.android.lyricsverify 在 MuMu display 7 上实测五种 WebView CSS 视口：640×360、960×540、800×360、1067×480、640×400；封面/信息/控制栏边界检查与队列 WebView 触摸注入通过。后台静音 WAV 播放进度从 1122ms 增至 4815ms，playing=true；暂停/继续检查通过。只清理隔离包与测试音频，不清除现有用户应用数据。
- 验证失败与修正：早期 wm 命令修改 display 0，而应用在 display 7，五次实际上都是同一视口，不能计入矩阵；两次改脚本后因全局旋转干扰落入竖屏失败。针对 display 7 并统一旋转后矩阵通过。密度应用有模拟器延迟，因此验收按实际 CSS 尺寸记录，未声称精确覆盖指定 DPR。浏览器复测曾因 Vite 被构建资源热更新干扰而导航超时；重启独立服务后复测。
- 验证截图/JSON：C:/Users/ihyj/.codex/visualizations/2026/10/03/01a100b3-4db1-74d0-aef6-d68615bc299e。测试后恢复显示 size/density、旋转/字体设置；临时脚本与音频清理。
- 交付 debug APK：android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk（64,258,167 字节）；另含 armeabi-v7a、x86、x86_64。包名 top.imsyy.splayer.android.debug，versionName 3.0.10-debug / code 30016；四个包 ZIP 完整且 WebView debugging=false。ARM64 SHA-256：f3a270fde8f1655ce3a98cf22e08aca401bc3f04839eefca38e8d2100173aa83。未签发正式版本、推送或打 Tag。
- 剩余验证：真实硬件 cutout/手势及三键导航矩阵、Android 10 inset 兼容路径、极端系统字号与逐字 RTL 高亮视觉质量未完成实际验收；超长且持续时间很短的歌词受可读速度上限约束，无法保证离行前展示完整尾部。远端 CI 未运行。完整真机矩阵标记 BLOCKED（当前只有模拟器环境）。

## MuMu 手机与平板补充验收（2026-10-03）

状态：IMPLEMENTED；下列已完成的设备测试为 VERIFIED。用户授权继续使用现有两台 MuMu 调试，未访问或清除正式应用数据。

- 设备：emulator-5554（SM-A5560，1080×1920 / 480dpi）与 emulator-5556（ALT-AL10，1080×1920 / 280dpi）；均为 API 35，127.0.0.1:16416 为平板连接别名，不计第三台设备。ADB 会话为 shell 权限且 su 不可用；本轮不需要 root，也未改动 root 配置。
- 独立包 top.imsyy.splayer.android.lyricsverify 与测试包安装到两台设备；无产品代码改动，新增/加强原生回归测试。
- 桌面歌词矩阵共 12 组通过：两设备 × 手势/三键导航 × fontScale 1.0/1.5/2.0，均启用 tall cutout 系统模拟 overlay，逐组核实 overlay 实际启用。测试覆盖多语言、稳定字号、实际画布滚动/暂停/seek、锁定/解锁/自动渐隐、手动字号/颜色重开恢复。
- 加强旋转断言：连续 4 次旋转必须改变实际 safeArea；view.getLocationOnScreen 实际窗口四边均在安全区域内，不能只断言命令执行。日志记录模拟 cutout 从顶部移至左侧，phone/pad 都通过；同一 Service 实例保持。
- 新 PlayerDeviceLayoutTest 使用真实 APK 中的 WebView，通过原生 evaluateJavascript 读取实际 Vue 几何，未启用 WebView 调试。旋转按 Activity 实际 displayId 操作并恢复原策略，检查窗口宽度实际改变、底栏保持在窗口内、手机横屏封面/元信息/底栏无重叠。手机 360×640 ↔ 640×360；平板 618×1098 ↔ 1098×618，自动判型选择竖屏/平板双栏。
- 播放页首次测试两设备失败：测试只查找共享 player-control，漏掉竖屏 mobile-player-bottom-controls，得到 null；修正选择器后两设备基线测试通过。这是测试定位错误，未据此修改产品。
- 日志与矩阵 JSON 保存在 C:/Users/ihyj/.codex/visualizations/2026/10/03/01a100b3-4db1-74d0-aef6-d68615bc299e；本轮只保留日志证据，临时驱动脚本与隔离包结束后清理。系统字体、导航/cutout overlay、旋转模式恢复原值。
- 原专项“模拟器导航/cutout/极端字号矩阵”阻塞已解除；仍未验证 Android 10 路径、物理硬件 cutout、RTL 逐字高亮视觉质量及硬件输入全场景。两台设备都是 API 35，不能据此声称覆盖 API 29。全项目 Android Lint 历史问题未改变。

- 最终播放页矩阵也为 12/12 通过（两设备 × 两种导航 × 三种字体缩放，均启用模拟 tall cutout）。合计桌面歌词与播放页矩阵 24/24 通过，另两台设备播放页基线通过。系统模拟 cutout 测试不等同于物理硬件验收。
- 提交前 pnpm build:android、pnpm format、pnpm lint 通过；构建仍有既有 chunk/ffmpeg.wasm/Gradle 环境警告，全库格式化的无关差异已恢复。未声称构建零警告或 Android Lint 历史问题已解决。

- 最终普通 debug APK 已恢复：android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk（64,258,191 字节），SHA-256：b733927817259c0d45f5b66415c060539b4f10fb41501bc92477d0bc590158df。包名 .debug，WebView debugging=false。另有其余三种 ABI；没有发布或推送。
- 清理核查修正：wm user-rotation free 只恢复策略，不恢复最后一次全局角度偏好；两设备已恢复原始 user_rotation=0、accelerometer_rotation=1、font_scale=1.0，测试 finally 增加对应恢复。最终恢复逻辑已重新编译，未重复整套矩阵。

## v3.0.11 发布准备（2026-10-03）

状态：IMPLEMENTED。用户明确授权同步 Git 仓库并发布 Release；沿用现有自动签名发布流程，不覆盖已有版本。

- 源码为已通过 MuMu 手机/平板 24 组矩阵的桌面歌词与横屏专项，版本更新为 3.0.11 / versionCode 30017。旧设置保守迁移，不修改数据库 schema，正式签名由现有 CI 注入。
- 本地 dev 相对 origin/dev 无分叉，发布前领先两个已验证专项提交。发布前重新执行相关测试、发布保护、类型检查、构建与 lint；远端 CI 成功后再创建新 Tag。
- 保留实际验证边界：Android Lint 历史报告仍有 187 errors / 27 warnings；API 29 与物理 cutout 未验证，不能声称零风险或全项目 Lint 零错误。

- 本轮发布前本地验证：22 项 JavaScript、15 项发布保护、18 项 Android JVM 测试通过；类型检查、ESLint、pnpm build:android、pnpm format 与 Gradle assembleDebug/testDebugUnitTest/lintDebug 执行成功。Lint XML 仍为 187 errors / 27 warnings，专项 FloatingLyric 无报告问题。无关格式变更已恢复。

- 发布前兼容复查发现平板悬浮歌词控制栏旧版歌名/歌手显示遗漏，已恢复为独立信息行，按系统字号计算高度；保留六个按钮及收藏操作。新增仅比较信息行截图的断言，确保实际内容变化。该修复将重新验证后提交，原 fb7ed870 的 CI 已成功，但不能替代新修复的 CI。

- 完整仪器套件首次运行发现历史 Capacitor ExampleInstrumentedTest 仍断言 com.getcapacitor.app，两台专项测试各自通过但套件失败；改为验证 SPlayer 包名与构建后缀。尝试引用 BuildConfig 时因本项目未生成该类编译失败，改为显式包名校验后重新编译成功。失败日志保留，未把首次套件运行计为通过。

- 补充恢复平板歌曲信息后最终矩阵：12/12 场景通过，每场景完整仪器套件 OK (3 tests)，合计 36 次测试；覆盖两设备 × 两导航 × 三字体，并启用模拟 cutout。首次平板 2.0 字体因固定 350ms 等待得到 controlsAlpha=0.37059042，断言失败；改为主线程确认且最长 2 秒等待动画终态后重跑全矩阵通过，未修改产品动画时长。失败日志保留。
- 最终补充修复的 pnpm build:android、pnpm format、ESLint、原生 assembleDebug/testDebugUnitTest/lintDebug 与仪器包编译均成功；Lint 仍 187 errors / 27 warnings。没有清除现有正式包数据。

## v3.0.11 正式发布验收（2026-10-03）

状态：VERIFIED；下列发布、产物及模拟器升级已实际完成。此前未验证的 API 29/物理 cutout/RTL 视觉边界继续保留。

- 发布源码提交：3afeffb37fafc0115931d280d8d7310d3329df52；Tag v3.0.11 已推送，未移动或覆盖已有 Tag。dev 已同步专项实现、设备回归、版本准备及平板歌曲信息兼容修复。
- 最终源码 Android CI 成功：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37113891150。发布前完整隔离仪器矩阵 12/12 场景，每场景 3 项测试通过；最终字体 2.0 与模拟 cutout/导航均通过。
- 首次发布构建、签名和版本检查成功，但创建草稿后立即读取草稿失败，流程停止：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37114137760。人工核实草稿正确提交且无附件后使用既有 workflow_dispatch 恢复入口，恢复流程成功：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37114387575。没有重复创建版本、改 Tag 或覆盖旧附件。
- 正式 Release / Latest：https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.11；isDraft=false、isPrerelease=false，下载与安装 ABI 表格及分类 CHANGELOG 沿用原格式。
- 四种正式 ABI 附件均下载验收：app-arm64-v8a-release.apk、app-armeabi-v7a-release.apk、app-x86-release.apk、app-x86_64-release.apk。每个文件 ZIP 完整、字节数及 SHA-256 与 GitHub asset digest 一致；包名 top.imsyy.splayer.android、versionName 3.0.11、versionCode 30017、非 debuggable、WebView debugging=false。
- 正式签名证书 SHA-256：d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea，与上一版本相同。ARM64 SHA-256：dd828bfd606e1e53c4ccce19dfd6089b72c064df172860e2d38892d066ea1e3e（60,018,956 字节）。
- 产物保存目录：C:/Users/ihyj/.codex/visualizations/2026/10/03/01a100b3-4db1-74d0-aef6-d68615bc299e/v3.0.11，含四个正式 APK、release-metadata.json、artifact-verification.json 与 upgrade-verification.json。下载首轮因连接 EOF 中断，改为逐 ABI 顺序下载后全部校验通过。
- 实际原位升级：emulator-5554 由 3.0.10 / 30016 升至 3.0.11 / 30017；emulator-5556 由 3.0.8 / 30014 升至新版本。使用 adb install -r，未清除正式包数据；两台首次安装时间分别保持 2026-10-03 15:20:07、2026-10-01 18:11:04，启动 Status=ok、进程存活、未发现 AndroidRuntime FATAL EXCEPTION。没有据此声称所有用户数据字段逐一验收。
- 最终模拟器保留最新正式版本；隔离包与临时驱动脚本已移除，字体/导航/cutout/旋转设置恢复。历史 Android Lint 187 errors / 27 warnings 仍存在，不视为全项目零错误；原有功能通过相关回归与兼容检查，未声称绝对无风险。
