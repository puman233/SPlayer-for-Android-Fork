# 应用内更新下载与手机布局验收

日期：2026-10-04。开发分支：dev。基于 v3.0.12 / versionCode 30018；初次交付未提交；用户现已授权双端实网验收后提交、推送并发布 v3.0.13 / 30019，工作流保持现有方案。

## 原有架构与调用链

设置/启动检查 → `getUpdateLog` → Android 更新弹窗 → `fetchAndroidReleaseAssets` → `Build.SUPPORTED_ABIS` 选择附件 → `AndroidAppUpdate.downloadApk` → `ApkDownload.fetchSources` → 完整性和包兼容性校验 → 缓存 APK → `installApk` → FileProvider → Android 系统安装器。

复用现有版本比较、官方 GitHub API/官方附件页面兜底、更新弹窗、原生单线程下载器、SHA-256 摘要、包名/版本/签名检查和 FileProvider。没有另建通用歌曲下载系统。

## 下载策略

- `UpdateDownloadSources.java` 集中配置 `PROXY_ENABLED=true`、`PROXY_BASE_URL=https://gh.llkk.cc`。
- 保留附件原始 URL。下载候选为 `https://gh.llkk.cc/` + 原始完整 GitHub URL、原始 URL；不对完整 URL 做编码。
- 仅接受 HTTPS GitHub Release APK 源地址；版本 JSON、Release Notes、附件摘要继续从 GitHub 官方读取。
- 每个源一次尝试，共最多两次；连接、HTTP、读取、长度、摘要或 APK 验证失败均可回退。回退使用全新临时文件，不混合两个源的字节。
- 连接超时 15 秒、读取超时 60 秒、单源读取上限 20 分钟；64 KiB 缓冲，进度约 250 ms 更新一次。
- 只接受完整响应 HTTP 200。不请求 Range，因此拒绝 206；拒绝 HTML 响应，还检查实际长度、官方精确 size（有则使用）、SHA-256 和 APK ZIP 结构。
- PackageManager 检查包名、versionCode 必须更高、签名兼容。多签名要求当前签名集合相同；单签名轮换要求新包签名历史包含当前签名。
- 本仓库已要求摘要，因此继续拒绝无摘要 APK。网页展示的舍入大小不用于严格字节校验。
- 通过全部校验后才把 `.part` 重命名为 `.apk`。失败/取消清理临时文件；取消断开实际连接，不启动回退源。原生单任务原子标记阻止重复下载。
- 只持久保存已验证文件名和公开摘要；恢复缓存时重新检查摘要及安装兼容性。权限页导致进程重建后重新打开弹窗仍可直接安装。
- ABI 按设备顺序选择，找不到则匹配明确标注 universal 的附件；排除 debug、unsigned。保持现有 `app-<ABI>-release.apk` 命名支持。
- UI 显示百分比与已下载/总大小，自动选择附件；回退不弹额外错误。最终失败提供重试/关闭，保留浏览器下载入口。
- 未知来源未授权时打开系统授权页，返回且已授权时继续安装；拒绝授权仍可之后点击安装。使用 content URI 和读取授权，保留系统最终确认。

不需要新增 Secrets 或 Token。代理请求不发送 GitHub 登录凭据；没有把 PAT、CI 密钥或密码写入应用。

## 文件与布局

| 文件                                                             | 改动                                                                               |
| ---------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `AndroidAppUpdatePlugin.java`                                    | 下载编排、安装前验证、缓存恢复、安装权限查询、兼容签名检查                         |
| `ApkDownload.java`（新增）                                       | 可测试的实际网络/文件传输、有限回退、完整性检查                                    |
| `UpdateDownloadSources.java`（新增）                             | 统一公开代理配置与候选地址                                                         |
| `androidAppUpdate.ts`、`AndroidUpdateApp.vue`                    | 桥接参数、下载字节、重试、缓存恢复、授权返回处理                                   |
| `assets.ts`、`assets.test.ts`                                    | universal 兜底、debug 排除、精确长度处理及回归                                     |
| `FullPlayerMobileLandscape.vue`                                  | 隐藏时释放 84 px 控件行，顶栏真实高度、居中别名和控制组对齐                        |
| `ListDetail.vue`                                                 | 手机封面与信息两列，三项操作共用完整横排；长艺术家完整单行省略，避免半行裁切       |
| `useListDetail.ts`、`main.scss`、`views/List/playlist.vue`       | 详情列表预留高度和头部同步，简介按需增加高度；低高度横屏改为至少一行歌曲的滚动视口 |
| `usePlayerClearance.ts`（新增）、`AppLayout.vue`、`SongList.vue` | 按迷你播放器/底栏实际视口坐标计算浮层偏移，包含缩放和安全区                        |
| 原生仪器测试、README、CHANGELOG、checkpoint                      | 实际场景与验收记录                                                                 |

## 实际验证结果

**VERIFIED**：ESLint 0 错误/0 警告；Vue 类型检查；13 项更新相关 TS 测试；19 项 JVM 测试无失败；`pnpm build:android` 完成；Gradle Wrapper 构建四 ABI debug APK 和测试包完成。

**VERIFIED**：MuMu Android 15 / API 35 手机（1080×1920、480 dpi）实际播放器触摸/旋转测试通过；DefaultLyric、AMLL、100/130% 字体、控制层隐藏释放空间、进度拖动、菜单持有与旋转回归。截图已检查。

**VERIFIED**：手机真实专辑页用现有原生缓存注入隔离数据，100/130% 字体、普通/收缩头部、三个按钮同一横排、元信息完整行、首行歌曲不重叠、定位按钮避开播放栏均通过。实际窗口 PixelCopy 截图已检查。测试中曾发现 inline-flex 长文本绕过行截断，已修复后重测。

**VERIFIED**：真实 Android 本地 HTTP 服务制造 403、500、异常 206、HTML、截断、摘要错误、篡改响应，确认有限回退、原地址字节独立重下、失败清理、下载中取消不继续回退，以及同版本包拒绝。最终手机 5 项专辑/下载测试通过。

**VERIFIED**：最终平板（280 dpi）6 项播放器/专辑/下载仪器测试通过，包含带简介的详情头部预留高度检查。平板原播放器分支保留。

**VERIFIED**：系统安装器专项使用版本更高、签名相同的隔离 debug APK（仅测试 versionCode 30019），缓存摘要/包兼容性恢复、未知来源授权引导、授权后同一文件交给实际 PackageInstaller 均通过，1 项测试 4.275 秒。没有确认覆盖安装，没有触及正式包数据。测试授权最终恢复 default，临时 APK 删除。

首次安装器测试在 finally 内恢复权限时，Android 以 `REQUEST_INSTALL_PACKAGES changed` 原因杀掉测试进程，不能把该次运行视为成功；随后将撤销操作移到运行器结束后，完整重测通过。手机模拟器中途断开，因此安装器最终完整验收在仍在线的平板完成。

**BLOCKED**：公开 gh.llkk.cc 实网请求在本机被远端关闭连接，尚未完成真实代理 APK 的吞吐与官方摘要核对。匿名 GitHub API 同时出现 403 限流；仅本机验证改用现有 CLI 登录读取官方公开元数据，凭据未传给代理或写入源码。受控 HTTP 测试证明回退行为，不能代替第三方代理服务可用性验证。

**IMPLEMENTED**：弹窗授权返回自动继续安装和失败重试；原生授权/安装/缓存恢复已实际验证，但真实远端新正式版本从更新提示到覆盖安装仍需后续发布时验证。API 29–34/36 及物理手机没有新增实机验收。

构建仍有第三方 Rollup/Gradle/本机 SDK 路径等历史警告。历史 Android Lint 报告 187 errors / 27 warnings，本次没有声称全项目 Android Lint 为零。

## 产物与证据

最终 APK 在 `android/app/build/outputs/apk/debug/`，为隔离测试包 `top.imsyy.splayer.android.phase1verify`，版本仍为 `3.0.12-debug` / 30018，不用于正式发布。

| APK                       |     字节 | SHA-256                                                          |
| ------------------------- | -------: | ---------------------------------------------------------------- |
| app-armeabi-v7a-debug.apk | 63925783 | 6542b7d8a9b4f16e4aa0b7b7f55618aaada375c0b19d0faa714eabdc86deec52 |
| app-x86-debug.apk         | 67056179 | a2b85148b0a0e94fa26149d91c2b3bfd0acc36109c35cc03ccdc9090c463048d |
| app-x86_64-debug.apk      | 70299424 | faf7fb48b36746b9008cb33dea0685a4c8a7c3c3234f19c77f7f84f8065adeff |
| app-arm64-v8a-debug.apk   | 64747673 | 6d9ebb55ca5ba025a4cdcac29b69464e92bd740adc3cc47dbdef0276368cf5b1 |

四包 ZIP 与 Web 资源检查通过，arm64 APK 的 apksigner 校验通过。

手机截图：`C:/Users/ihyj/.codex/visualizations/2026/10/03/01a0ffc3-11ce-7f32-b0fa-990a6f8b289e/update-layout-phone/`。
专辑截图：同目录 `album-layout/`，含 100/130% 字体的普通与收缩头部。

按用户附件最后要求，本次修改和产物保留供审阅；用户确认后才进行提交、同步与正式发布。

平台接口参考：[Android SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo)、[Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)。

## 2026-10-04 双端实网补充验收与发布授权

手机和平板重新连接后，使用生产下载引擎下载官方 v3.0.12 的 x86_64 正式 APK，65,575,255 字节；官方 SHA-256 `8986787a974784cb6909818049aca816d4a5b18f23f429556e2e004337a67b64`。每次真实下载均验证长度、摘要、ZIP、包名、版本、存在签名和进度事件，临时文件全部删除，未覆盖正式应用数据。

| MuMu         | gh.llkk.cc 耗时 | GitHub 原地址耗时 | 结果                       |
| ------------ | --------------: | ----------------: | -------------------------- |
| 手机 480 dpi |        8.483 秒 |          4.048 秒 | 两个来源完整下载与校验通过 |
| 平板 280 dpi |        6.330 秒 |          4.682 秒 | 两个来源完整下载与校验通过 |

**VERIFIED**：代理服务在双端真实可用，解除先前宿主机连接失败导致的服务可用性阻塞。本网络原地址更快，不能据此宣称代理提高速度。两端各 5 项下载回退/取消/详情布局测试再次通过（6.190 秒 / 6.593 秒）；实网测试为 `LiveReleaseDownloadTest`，仅显式提供公开 Release 参数时执行，常规测试不会下载。此阶段测量正式 v3.0.12 的传输路径，未执行用户正式包覆盖安装；v3.0.13 发布后再验收新附件。

用户最新指令已授权提交、推送和发布，无需再等待此前附件中的审阅步骤。
