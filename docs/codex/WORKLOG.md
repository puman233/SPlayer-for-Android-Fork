# 工作日志

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
