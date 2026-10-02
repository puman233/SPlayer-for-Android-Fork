# 工作日志

## 2026-10-02 项目目录归并与签名包

- `VERIFIED`：主目录 `C:/Users/ihyj/SPlayer/SPlayer-for-Android-Fork` 的 `dev` 已快进到 `407635cf`，作为后续开发入口。
- `VERIFIED`：旧 Android 仓库已移入 `retained-assets/project-archive/SPlayer-for-Android`；未跟踪文档保存在 `retained-assets/SPlayer-for-Android`，JKS 与原签名配置保存在仓库外 `signing` 目录。
- `VERIFIED`：主项目依赖已按冻结锁文件重建，完整 Android 资源构建与四 ABI Release 打包完成，APK 签名验证通过。
- `VERIFIED`：Release 证书 SHA-256 为 `d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea`，与既有正式发布证书一致；包名 `top.imsyy.splayer.android`、版本 30014 / 3.0.8、四 ABI 均已核对。
- `BLOCKED`：重复工作树因 Windows 进程占用暂不能归档，原目录与 Git 注册均保留，主项目已包含全部代码和 Debug/Release 产物。
- `BLOCKED`：本次为同版本正式签名重构建，未执行正式包覆盖安装；递增版本升级验收尚需确定下一发布版本。

## 2026-10-01

- `VERIFIED`：目标仓库 remote 为 `https://github.com/puman233/SPlayer-for-Android-Fork.git`。
- `VERIFIED`：`dev` 已与 `origin/dev` 同步，基线为 `d834171d`，初始工作区干净。
- `VERIFIED`：创建隔离分支 `codex/android-stability-update`。
- `VERIFIED`：两台 MuMu 终端在线，均为 API 35、支持 `x86_64,arm64-v8a,x86`。
- `VERIFIED`：手机终端 `emulator-5554` 为 1080×1920 / 480 dpi；平板终端 `emulator-5556` 为 1080×1920 / 280 dpi；字体比例均为 1.0。
- `VERIFIED`：JDK 21 位于 `C:\Program Files\Zulu\zulu-21\`，Android SDK 位于 `D:\Scoop\apps\android-clt\current`。
- `IMPLEMENTED`：建立持久化计划、测试矩阵和决策记录。
- `VERIFIED`：`pnpm typecheck` 与 `pnpm lint` 通过。
- `VERIFIED`：`pnpm build:android`、Android `testDebugUnitTest` 与四 ABI `assembleDebug` 通过。
- `VERIFIED`：手机成功安装 x86_64 Debug APK 并完成协议、首页、全局设置和关于页截图。
- `VERIFIED`：Debug 构建使用 `top.imsyy.splayer.android.debug`，手机与平板均可安装并冷启动，且不覆盖原正式版或清除用户数据。
- `VERIFIED`：手机 480 dpi 进入 Debug 首页；平板 280 dpi 在 MuMu 多显示面中完成许可协议并进入 Debug 首页，截图已留存于工作区外的测试证据目录。
- `IMPLEMENTED`：同步 Capacitor 生成的 Gradle 依赖路径，使其与锁文件安装的 Capacitor 8.4.0 / Status Bar 8.0.2 一致。
- `VERIFIED`：构建工具在 Windows 上引入 989 个已跟踪文件的换行/生成噪音；已仅还原本次构建产生的跟踪文件修改，保留计划文档。
- `IMPLEMENTED`：更新仓库统一为 `puman233/SPlayer-for-Android-Fork`，手动检查强制绕过 10 分钟缓存，并使用完整 SemVer 比较。
- `VERIFIED`：新增 4 个版本比较测试，覆盖 `v` 前缀、构建元数据、稳定版/预发布版及非法标签；类型检查和完整 lint 通过。
- `VERIFIED`：GitHub API 真实返回匿名限流 403 时，Android 原生 HTTP 自动回退 Releases Atom（200），关于页显示最新 v3.0.8，手动检查提示“已是最新版本”。
- `VERIFIED`：修复后的 Web 构建、Capacitor 同步和四 ABI Debug APK 构建通过，x86_64 APK 已在手机覆盖安装验证。
- 待验证：双端播放页、短暂停顿、更新下载与系统安装器链路。

## 2026-10-02

- `IMPLEMENTED`：新增 Android 原生更新插件，使用隔离缓存目录流式下载 APK，提供节流进度事件、取消、失败清理和 `.part` 原子落盘。
- `IMPLEMENTED`：下载完成后强制校验 Release 提供的 SHA-256，并校验 APK 可解析、包名一致、版本更高及签名证书历史相交；校验失败不进入安装流程。
- `IMPLEMENTED`：按 `Build.SUPPORTED_ABIS` 选择签名 APK，避免将 `x86` 误匹配为 `x86_64`，并由系统安装器保留最终用户确认。
- `IMPLEMENTED`：关于页在检测到 Android 新版本后打开应用内下载弹层，展示目标 ABI、文件大小、下载进度和可操作错误提示。
- `VERIFIED`：7 个更新相关自动化测试通过，覆盖 4 个 SemVer 场景和 3 个 ABI/资源选择场景；`pnpm typecheck`、`pnpm lint`、`pnpm build:android` 均通过。
- `VERIFIED`：Android `compileDebugJavaWithJavac`、`testDebugUnitTest` 和四 ABI `assembleDebug` 通过。
- `VERIFIED`：手机与平板均以 3.0.7 前端测试版本检测到 v3.0.8，并自动选择 `app-x86_64-release.apk`；平板横屏弹层完整可见且无控件重叠。
- `VERIFIED`：手机端下载进度持续增长；两次取消均返回 `UPDATE_DOWNLOAD_CANCELLED`，缓存目录中未残留 `.part` 文件，重新下载可再次启动并继续增长。
- `BLOCKED`：模拟器访问 GitHub Release 约 40–50 KB/s，未在本阶段等待完整 62.6 MB 下载，因此远端整包 SHA-256 与系统安装器唤起尚未做端到端验证；未授予未知来源安装权限，也未执行静默安装。
- `VERIFIED`：在 1080×1920 / 480 dpi 手机基线复现完整播放页控制区被矮屏裁切；在 1920×1080 / 280 dpi 平板确认主内容宽度边界和播放控制层均需纳入回归。
- `IMPLEMENTED`：新增可测试的可用视口/缩放计算核心；布局形态继续使用稳定的 layout viewport，实际 CSS 容器尺寸优先使用 `visualViewport`，并将页面缩放变量换算为精确 CSS 像素。
- `IMPLEMENTED`：为 360×640 级矮屏压缩封面、信息区、进度区和播放控制间距；为主布局和 Pad 内容区补充 `min-width: 0` 与横向溢出约束。
- `VERIFIED`：4 个视口边界测试和 3 个更新资源测试通过；`pnpm typecheck`、`pnpm lint`、`pnpm build:android`、Android `testDebugUnitTest` 与 `assembleDebug` 通过。
- `VERIFIED`：新 x86_64 Debug APK 已覆盖安装到手机和平板且保留数据。手机 480 dpi 竖屏的封面、元数据、进度条、五个控制按钮和分页点完整可见；歌词分页无重叠。
- `VERIFIED`：手机字体比例 1.3 与临时 420 dpi 下完整播放页仍无裁切；测试结束后已恢复 480 dpi / 字体 1.0。
- `VERIFIED`：平板调试实例位于 MuMu `displayId=5`；在 1920×1080 / 280 dpi 下首页无非预期横向滚动，完整播放页控制层无重叠或越界。
- `IMPLEMENTED`：记录后续 Release 仅通过已忽略的 `android/key.properties` 引用用户提供的外部 `my-release-key.jks`；本轮未读取密钥内容、未生成密码配置、未提交签名材料。
- `IMPLEMENTED`：为 Teleport 弹层新增未缩放的实际可见视口变量，全屏设置同步使用 Android 顶部/底部安全区。
- `VERIFIED`：手机竖→横→竖、平板横→竖→横过程中，全屏设置尺寸正确、内容可继续滚动；关闭后手机底部导航与滑动指示器恢复。
- `VERIFIED`：手机横屏完整播放页与平板竖屏完整播放页无裁切、重叠或越界；测试后双终端自动旋转与备用方向值均恢复基线。
- `VERIFIED`：视口测试 4/4、`pnpm typecheck`、`pnpm lint`、`pnpm format`、`pnpm build:android`、Android `testDebugUnitTest` 与四 ABI `assembleDebug` 通过。
- `IMPLEMENTED`：Media3 加载错误采用 5 次内部重试；终态网络错误再按 750ms、2s、5s 三次换 URL，保留当前播放位置和用户播放/暂停意图。
- `IMPLEMENTED`：恢复预算不再在 URL 解析成功时立即清零，只有连续稳定播放 10 秒才重置，防止坏链路形成无限恢复循环。
- `IMPLEMENTED`：新增去敏播放时间线，仅记录状态、播放意图、位置、缓冲位置、恢复次数和错误码；移除 PlaybackManager 与低磁盘预取日志中的完整 URL。
- `VERIFIED`：3 项恢复策略单测和 Android Java 编译通过；`pnpm format`、`pnpm build:android` 与四 ABI `assembleDebug` 通过，新 x86_64 APK 已覆盖安装到手机和平板。
- `VERIFIED`：手机短时断网并 seek 到未缓存区后由 BUFFERING 回到 READY/PLAYING；恢复歌曲结束后原生队列继续切歌。
- `VERIFIED`：长断网触发 Media3 2001 后，原生恢复实际执行三档退避；前两次 URL 解析失败，第三次在网络恢复后从 114730ms 断点重载并继续播放。
- `VERIFIED`：缓冲期间主动暂停后，网络恢复保持 `requested=false` 和 MediaSession `PAUSED`，位置保持 146290ms，未擅自自播。
- `BLOCKED`：手机剩余空间低于 1GB，SimpleCache 按既有低磁盘策略只读，无法新建完整缓存验证离线重播；未清理用户数据。MuMu 无 root/tc 或可控代理，精确限速场景未运行。
- `VERIFIED`：GitHub v3.0.8 x86_64 官方 APK（65,677,430 字节）完整下载，SHA-256 为 `aabd1eaecb4790c999aa4230be02673d307f29de6f6213c3a3f2ade42200b60d`，与 Release API digest 一致。
- `IMPLEMENTED`：附件读取优先使用 GitHub Release API 的 digest/size，限流或异常时回退官方 expanded assets；更新弹层显示实际包体大小。
- `IMPLEMENTED`：原生下载强制要求合法 SHA-256，拒绝并发重复任务；取消会断开活动连接，所有失败会清理部分文件和旧目标文件，无效 APK 校验失败后立即删除。
- `VERIFIED`：官方 APK 包名/版本为 `top.imsyy.splayer.android` / 30014；同签名测试升级夹具为 `.debug` / 30015，证书 SHA-256 与已装 30014 Debug 包一致。
- `VERIFIED`：手机使用实际 FileProvider URI 成功进入 `com.android.packageinstaller/.PackageInstallerActivity`；未知来源权限保持默认，未授予权限、未确认安装、未覆盖正式包。
- `VERIFIED`：新增 2 项 Release API 附件/大小测试（更新资源测试共 5 项）通过，TypeScript 类型检查和 Android Debug 编译通过。
- `VERIFIED`：更新相关 12 项测试、`pnpm typecheck`、`pnpm lint`、`pnpm format`、`pnpm build:android`、Android `testDebugUnitTest` 与四 ABI `assembleDebug` 全部通过；最终 x86_64 Debug APK 已覆盖安装并冷启动于手机和平板，版本均为 30014 / 3.0.8-debug。
- `IMPLEMENTED`：默认稳定更新通道过滤 draft、prerelease 和非法 SemVer 标签；断网、超时、限流、404 与未知错误提示提取为可测试纯函数。
- `VERIFIED`：M4 完整质量门通过：格式化、类型检查、零警告 lint、8 个 suite / 42 项 TypeScript 测试、`pnpm build:android`、Android `testDebugUnitTest` 与四 ABI `assembleDebug`。
- `VERIFIED`：最终 x86_64 Debug APK 覆盖安装到手机和平板，版本均为 30014 / 3.0.8-debug；手机冷启动 2220 ms，平板冷启动 3152 ms，最终竖屏/横屏布局无裁切或越界。
- `VERIFIED`：手机可用空间恢复到 2.7 GB 后，先完整在线播放形成缓存，再在飞行模式下从 0 ms 连续播放至 194037 ms 并自然切换下一首；测试结束后网络已恢复。
- `VERIFIED`：分支已推送并跟踪 `origin/codex/android-stability-update`；README、CHANGELOG、最终报告、APK 摘要和回滚说明完成。
- `BLOCKED`：正式 Release 签名升级包仍需工作区外 JKS、别名和密码；未用 Debug 签名产物冒充正式发布包。精确限速仍需可控代理或有 root 的测试终端。
