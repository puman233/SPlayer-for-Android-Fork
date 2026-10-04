# Android 自动发布工作流检查点

## 手机横屏参考布局修订（2026-10-04）

状态：VERIFIED（以下模拟器范围），等待用户视觉确认。按用户要求，先用 `b802f47d` 回退上一轮 `60f5f936`，保留历史，再只修改手机横屏；平板恢复并保留原 composition。当前 `dev`，未 push、未 Release、未进入下一 UI 阶段。

- 手机横屏采用参考图的分区：左封面/信息、右歌词，中央播放与进度、两侧操作组，取消整块底部控制面板。
- 初次显示控件；2 秒无操作后淡出，点击空白恢复并重新计时。隐藏保留布局，pointer-events + inert 防止误触，背景随操作组一起隐藏。
- 拖动与弹层保持显示；歌词工具收纳到顶部，次要播放设置收纳到更多快捷操作中，仍复用原来的播放与 seek 逻辑。
- 仅手机横屏提供新的弹层 hold key；共享组件参数默认关闭。FullPlayer 分支仍为原来的手机判断，未把平板纳入新布局。
- 手机 MuMu（density 480，横屏 WebView 640×360 CSS px）与平板 MuMu（density 280，1098×618 CSS px）各 2 项仪器测试通过。手机实际 MotionEvent、2 秒隐藏/恢复、菜单保持、拖动保持、原生 seek、横竖屏往返、两种歌词引擎、无歌词、长信息、WebView textZoom 100/130% 已验证。平板确认保留 `.player-content .content-left`，且不存在新手机横屏组件。
- pnpm format、lint（零错误/警告）、typecheck:web、build:android 成功；23 项 TS 回归通过。四 ABI debug APK 与仪器包构建成功，JVM 缓存报告 18 项无失败。
- Android Lint 全项目零错误：BLOCKED，历史 187 errors / 27 warnings 仍存在，任务正常退出不等于零错误通过。Vite/Gradle 的既有构建提示仍保留。
- 本轮证据：`C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/phone-only`。`phone/controls.png`、`phone/idle.png`、`tablet/landscape.png` 为本轮实际窗口截图；之前 `final-phone/`、`final-tablet/` 属于已回退方案。
- 验收 APK：证据目录中的 `SPlayer-phone-landscape-arm64-debug.apk`，64,743,482 字节，包名 `top.imsyy.splayer.android.phase1verify`，SHA-256 `ab9466bee195fc4102e55d83749ea6aa9ccf2d1e55985aa8d81a30ce981d7d05`。
- 未覆盖正式包或清除数据；临时静音 WAV 已在 finally 删除，系统字体、分辨率、密度不改。固定截图使用已有隐藏别名设置，避免启动异步详情影响素材；产品设置不改，截图不使用真实用户歌曲。
- 验证边界：MainActivity 原有 textZoom 固定 100%，显式 130% 压力测试不代表产品已跟随系统字体；真实硬件 cutout、Android 10、折叠屏、在线账号功能未扩展验证。超长元信息可滚动阅读。
- 当前停止等待用户确认，复现说明见 `docs/validation/phone-landscape.md`。

## 本次对话修改回退（2026-10-04）

状态：VERIFIED（以下范围）。按用户要求撤销本次对话的五个提交：920996ce、cf8aaa8d、1a65f64b、99c2c3ce、1090e1e0。使用新的回退提交保留历史，不强推、不移动 Tag。添加本回退记录前，暂存内容已与基线 `16e220977f8eaa971f6b5b531ce138c82e0f0732` 完全一致；本次自适应基础层、播放器/共享控件修改及同版本修补工作流均撤销。

- 公开 v3.0.11 四 ABI APK 已恢复原版备份，并重新下载确认全部字节数、SHA-256 与修补前一致。原发布正文已恢复；Release ID 402442837、正式/latest 状态、target_commitish 和 Tag 对象 b34c36013e063ce280334a261ed2c877609741e1 保持不变。版本仍为 3.0.11 / 30017，未新建版本或 Release。
- MuMu 手机和平板正式包均使用原 ARM64 APK 保留数据覆盖安装，安装和启动成功，当前进程启动日志未见 FATAL EXCEPTION，firstInstallTime 保持不变；未清除正式应用数据。
- 恢复后的源码 pnpm build:android、pnpm format、pnpm lint（零错误/警告）、pnpm typecheck:web 成功；22 项 TS、15 项发布保护、18 项 JVM 测试通过。普通四 ABI debug APK 和原有仪器包已重新构建，普通包 ZIP/版本检查通过。Android Lint 任务完成，仍为历史 187 errors / 27 warnings；已有 Vite 打包提示保留。
- 产品源码、README、CHANGELOG 与 CI 配置恢复基线；仅本文件追加回退记录。本次新旧包及测试证据保留在本地作为恢复材料，不再作为公开安装包。
- 回退证据目录：`C:/Users/ihyj/.codex/visualizations/2026/10/03/01a10146-a8d1-7132-a8a7-785cf718820d/patch-final/rollback`；原版备份在 `bundle/old/`，重新下载的原版正式 APK 在 `restored-public/`。后续回退提交同步 dev，远端 CI 的执行状态单独核对。

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
