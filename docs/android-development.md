# Android 开发、测试与发布

## 使用方式

在 `dev` 提出功能或修复要求，Codex 按 `AGENTS.md` 完成定位、最小修改、检查和设备验收，提供结果后按用户授权提交和同步。测试构建、正式发布是独立授权。无需重复粘贴执行规则。

测试使用 `3.0.16-beta.N`，N 与 Android 构建号逐次递增。默认正式源码版本保持原样；本地或手动 Preview 通过 Gradle 属性覆盖测试版本。例如 `./gradlew :app:assembleDebug -PpreviewVersion=3.0.16-beta.1 -PpreviewVersionCode=30024`。CI 使用 `.debug` 包名隔离真实数据。用户明确要求覆盖 MuMu 原版时，需原 applicationId 和原签名，执行 `adb install -r`；禁止卸载或清空应用数据。

## 工作流触发与保护

| 工作流 | 触发 | 行为与权限 |
| --- | --- | --- |
| Android CI | dev push、针对 dev 的 PR、手动、Reusable Workflow | 变更门禁、actionlint、完整 TS 类型/单元测试、ESLint、Gradle 单元测试/Lint、一次 Debug 与测试 runner 构建；contents read |
| Android Preview | dev 手动，指定测试版本与构建号 | 复用 Android CI，7 天 APK Artifacts，含 SHA、UTC 时间、变体、ABI、SHA256；不创建 Tag/Release、不更改官网 |
| Android UI Test | 成功的 dev push Android CI，或手动指定成功 Run ID | 验证产物来源后复用 CI APK；API35 手机、平板、小屏真实后台歌词、悬浮窗和旋转布局测试；actions read、contents read |
| Android Release | 保留版本 Tag 事件、手动指定已有 Tag | Tag 事件缺少明确授权会失败；正式发布必须手动勾选授权、通过 exact commit 的 dev push CI 和 android-release Environment。仅发布 job 获得 contents write |
| Website (GitHub Pages) | website dev 修改、正式 Release 成功、人工 Release 编辑、手动 | 保持独立 concurrency、静态 metadata/fallback；失败 Release 不更新，Preview 不触发 |

受保护的 README、LICENSE、AGENTS、工作流、签名/版本 Gradle 配置、package、锁文件及大量业务删除进入 `change-review` Environment。普通源码修改自动检查；官网单独修改不重新构建 Android。新增检查器首次引入用当前版本，后续 PR 门禁优先执行基准分支检查器。密钥、本地配置和 APK 直接阻断。门禁不会输出疑似凭据内容。

## 管理员必须配置

1. 建立 `change-review` 和 `android-release` Environments，设置 required reviewers、禁止自审/管理员绕过；将允许部署分支限定为 dev 和授权版本 Tag。`development` 不需要审批。未配置 required reviewers 的 Environment 不会自动提供人工保护，不能视为完整启用。
2. 将四项现有 ANDROID 签名 Secrets 迁入受保护 `android-release` Environment。添加该 Environment 的 `ANDROID_RELEASE_CERT_SHA256` variable（可信原发布证书的 64 位 SHA256，不是新生成的证书）。保持原密钥。
3. dev Ruleset 禁止强制推送，要求 PR/审核与 Android CI 审核、构建检查；用 CODEOWNERS/规则保护 `.github/**`、AGENTS、LICENSE、版本及签名配置。不要把源码可修改的门禁当作管理员权限隔离。
4. 如 UI 验收是发布必需项，在受保护 Environment 配置 `ANDROID_UI_REQUIRED=true`。发布门禁会要求当前提交手机、平板、小屏对应的有效 UI Artifacts，且其所属 Android UI Test 整体成功；失败或过期需要重新验收。workflow_run 从默认分支加载定义，仓库默认分支为 dev，可自动触发；不操作其他分支。
5. Actions 默认 token 设 read-only，启用所用 Actions；官网继续使用现有 github-pages Environment。真实账号/网络、ROM 省电策略、长期后台和真实音源可用性另行人工验收。

## 正式发布契约

正式签名缺失、调试证书、预期指纹不一致、源码/Tag/APK 版本不一致、四种 ABI 缺失、未经授权或当前提交 CI 未成功均阻断发布。本地验证产物的 SHA256SUMS 作为 30 天 Artifact 保存；发布后校验四附件大小和 API 摘要。保持既有草稿恢复、重复 Tag 跳过、旧附件备份和明确 Release ID/repair SHA 校验；取消/重试不删除历史 Release，不移动 Tag。官网只在整个 Release job 成功后同步。

本地检查：`python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'`；actionlint 检查 Actions YAML；`pnpm lint`、`pnpm typecheck`、`python3 -B .github/scripts/development-checks.py tests`、`pnpm build:android` 后在 android 执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。设备测试必须断言实际行为，不把模拟器启动当验收；相关截图与报告仅临时保存。

后台歌词通过原生 ExoPlayer 进度和状态直接驱动，WebView 延迟消息不得覆盖原生时钟；远程 JS 播放模式仍使用既有回传。冷启动和回前台查询实际窗口状态，恢复已开启但丢失的悬浮窗；按钮在窗口实际附着后才显示开启。设备测试使用生成的测试音频与同目录 LRC，连续五分钟留在后台，验证两次自动切歌、进度与歌词数据，无需真实账号。

手机详情头部使用自然高度和实测列表偏移，覆盖长标题、放大字体、隐藏封面、折叠与滚动。UI 工作流执行八项测试，保存详情页和播放器截图；歌手专辑网格同时检查实际滚动与卡片宽度。当前测试迭代为 `3.0.16-beta.5`，构建号 `30028`。

## 在线播放与后台歌词的排查边界

- 音频链路：`SongManager.getAudioSource` → `PlayerController.loadAndPlay` → `AudioManager` → `AndroidNativeAudioPlayer` / WebView。原生 `PlaybackManager` 持有实际 Media3 媒体、MediaSession、队列与播放位置。
- 歌词链路：`setupSongUI` → `LyricManager.handleLyric` → 在线/本地/远程来源 → TTML/YRC/LRC 解析 → `musicStore.songLyric` → 主歌词与 Android 悬浮窗。请求代次拒绝旧结果；预载按歌曲类型、ID、本地路径及远程服务器身份区分。
- 后台链路：原生 ENDED/通知/媒体按键 → 原生队列切歌 → `trackChanged` → 前端按歌曲 ID 校验并更新索引与歌词。原生切歌先清空旧悬浮时间轴；桥接数据携带歌曲 ID，不能覆盖另一首原生歌曲。
- 悬浮进度由原生 `player.isPlaying()`、实际位置和速度驱动，缓冲或暂停不继续插值。WebView 恢复与悬浮服务重建沿用既有同步入口，不增加歌词轮询器。
- 自动歌词可先展示已就绪 LRC/YRC，再升级 TTML；可选歌词来源分别隔离异常并限定等待时间。显式来源优先级保留，TTML 失败允许回退。新用户沿用既有自动模式，不迁移覆盖保存的手动偏好。
- 原生错误仅由原生恢复流程处理，最终错误不再触发第二轮网页重试或自动跳歌。临时 HTTP 错误与权限/缺失资源分别处理；暂停取消待执行恢复。缓冲阈值未凭经验调大。
- WebView 恢复最多三次，等待按 1/2/4 秒退避；仅在连续实际时间轴推进 10 秒后重置预算。缓冲、暂停和 Seek 重新累计，格式或本地文件错误及重试耗尽均停止当前曲目，不自动更换歌曲。

相关确定性测试位于 `lyricConsistency.test.ts`、`playbackSourceRace.test.ts`、`PlaybackRecoveryPolicyTest.java`。设备测试 `PlaybackTransitionTest` 包含 20 次后台自然 HTTP 切歌、实际 Media3 媒体与 MediaSession 元信息校验、旧歌词桥接拒绝，以及低吞吐和断网后暂停场景。

受控 HTTP 测试只替换网络端点，并将测试服务器的 HTTPS 地址映射回其本机 HTTP 端口；播放器、队列、解析器、Store 与悬浮窗使用实际实现。此测试不能替代真实在线提供方、蓝牙硬件、跨厂商省电策略或修改前后的弱网性能对比。只在隔离 Debug 包运行，保留首次失败记录，不清理用户数据。

真实在线验收使用 `FloatingLyricLongPlaybackTest`。默认用例继承已保存的音源偏好：关闭备用源且官方未返回地址时，应停在当前歌曲失败，不能为让测试通过而永久启用备用源。`explicitAutomaticSourceBackgroundNaturalEndKeepsRealTrackAndLyrics` 和 `fiveMinutesExplicitAutomaticSourceKeepsRealTrackAndLyrics` 是显式自动来源场景，临时只启用项目已配置的按歌曲 ID 取源入口，结束恢复完整设置、队列及播放速度；不增加外部服务或替换真实音频、歌词响应。

前一用例以三倍速自然结束，后一用例以原速连续在后台观察五分钟，期间不打开页面、不 Seek、不发送下一首命令。两者同时校验实际音频时长与公开元数据、下一曲 ID、MediaSession 标题及独立歌词响应；音源不可用仍判失败，不跳过测试。诊断仅记录取源路由、耗时、业务状态与是否有地址，不保存媒体 URL、Cookie 或认证信息。真实提供方可用性会波动，因此单次通过不能证明所有歌曲或长期可用性。

`WeakNetworkPlaybackTest` 通过真实 HTTP 和 Media3 验证正常吞吐对照、首包延迟与分块抖动、一次 TCP 重置、超过生产读超时的停发、503 后恢复、暂停取消待定恢复及持续 503 耗尽。恢复场景同时将原生取源解析器指向测试服务器，避免初次播放受控而恢复访问真实 API；原上下文只保存在内存，结束恢复设置、队列和传输状态，不修改设备网络。后台测量只采样原生状态，结束后回前台完成恢复。

恢复耗尽同时约束三次应用恢复、四次取源、Media3 的加载错误次数以及全部媒体请求数；既有缓存晋升任务的一次请求与播放重试分别计数。指标保存在隔离应用外部文件目录的 `detail-regression/network-*.json`，不包含 URL、Cookie 或用户元信息。正常网络与延迟抖动是同版本的网络条件对照，不能称为修复前后性能提升；受控分块延迟、TCP 重置及 HTTP503 也不等同真实无线丢包、移动网络或跨 ROM 后台验收。

`WebViewNetworkPlaybackTest` 覆盖 Android 关闭混音共存时的真实 `AudioElementPlayer` 路径。夹具设置实际选项并调用构建产物中的公共 AudioManager getter 重建、重新绑定控制器，不替换引擎或派发伪造媒体事件。初次503、中途TCP中断、淡出暂停后取消恢复、实际十秒进度重置预算，以及Seek不能冒充稳定进度均读取真实HTMLAudioElement状态；同时检查原生ExoPlayer未播放且原生恢复计数为零。

服务器为匿名跨源HTML媒体提供CORS/OPTIONS/Range支持；只有本场景独占端口的测试HTTPS地址在HTML `src` setter映射回HTTP。该映射不用于真实提供方，原setter、XHR、方法、设置、队列、引擎和播放状态在结束恢复。后台用例离开页面30秒，期间不执行WebView JS，返回后核对实际DOM媒体进度，不能等同数小时后台或跨ROM验证。诊断仅记录数字、布尔值、事件类型和错误码，保存为 `detail-regression/web-network-*.json`，不记录媒体URL或凭据。

后台验收先断言目标 Activity 实际进入 `STOPPED`；HTML媒体还须确认 `document.hidden`。MuMu副显示器可能没有桌面，HOME或任务后移后目标仍为RESUMED；此时用仅存在于测试APK的无网络空白Activity覆盖同一显示器，仍以实际生命周期验收，返回后自动结束覆盖窗口。原生弱网与20次自然HTTP切歌共用此入口。此前仅按HOME的平板在线结果仍能支持曲目、音频和歌词身份校验，不能支持真正后台结论。

真实在线 `assertBackgroundOnlineNext` 同样使用实际后台入口：入场确认隐藏，五分钟/加速自然结束的每个原生样本及最终歌词匹配均断言STOPPED，不以WebView轮询维持页面。记录真实单调时钟耗时、生命周期和脱敏上游诊断，以 `-stopped` 文件名与旧HOME证据区分。真实音源不可用、生命周期恢复或歌词不匹配仍导致失败；测试结束恢复原设置、队列、歌曲和速度，不把显式测试来源变成用户默认值。

实测WebView110将首次HTTP503、404、无效音频及CORS拒绝都报告为媒体错误4。Android element路径因此只对HTTP(S)来源执行一次最多两秒的匿名Range状态探测，仅明确读取408/429/500/502/503/504才进入既有三次恢复；403/404、正常HTTP但格式无效、CORS不可读和探测超时仍停止当前曲目，不将所有错误4改成网络错误。响应头到达即停止响应体IO，即使服务器忽略Range也不下载完整音频。此机制不绕过CORS，不引入新音源或跨域凭据。

暂停、Seek、切歌、切源、清空列表及引擎重建取消待定探测；返回结果还须匹配请求代次、播放意图、引擎和来源。错误防抖限定同一请求和意图。暂停后的同请求迟到错误不再启动探测或增加恢复计数，旧等待还需匹配播放意图。耗尽或永久失败后，同一请求的迟到媒体错误不能开启第二轮预算；明确点击播放会重新取当前歌曲资源。设备用例分别覆盖初次503恢复/耗尽、错误2与后续503共享预算、真实状态探测期间暂停及实际STOPPED后台恢复，保留原始失败并检查静默期间无新请求。

冷启动即遇HTTP503、404、无效音频或CORS拒绝时，还需验收进程存活及PlaybackService启动契约。服务附着时先用现有通知完成一次 `startForeground`，随后再执行原有暂停降级或空媒体清理；否则远程暂停状态可能在首次前台化之前撤前台，导致系统 `ForegroundServiceDidNotStartInTimeException`。错误矩阵使用真实响应及HTML媒体，永久/不可观测错误保持停止、不跳曲、无重试；服务启动与HTTP恢复分别验收。

受控原生HTTP夹具不能仅修改混音选项：若现有AudioManager仍为element，须通过构建产物中的公共getter重建、重新绑定并断言实际android-native，结束恢复原引擎、设置和传输。重定向原生API前清除上一测试窗口，防止旧队列的三首预取污染场景请求数，结束通过公开同步入口恢复原窗口。否则从Web测试转到原生测试时，实际仍为HTML媒体，或请求数混入其他歌曲，不能形成有效验收。

### 播放模式与原生队列回归

模式提交须同时对齐当前身份、列表索引及原生窗口；重排后使旧JS预载、Automix安排及原生URL回调失效。原生重选保留NEXT/PREVIOUS/自然结束意图，相同顺序的偏好刷新不能吞掉有效补窗；暂停后迟到wrap不能续播。歌词仍以确认的实际媒体身份加载，并拒绝旧代次结果。

设备用例 `PlaybackModeQueueTest` 使用真实模式命令、受控HTTP推荐/音频、ExoPlayer自然结束和实际STOPPED生命周期，检查队列、实际媒体URI和悬浮歌词。包括连续模式切换、待定心动取消、同曲换列表、手动及通知切歌、待URL重排、不循环尾部、202首补窗及暂停迟到wrap。短音频场景须明确校准真实WAV与元数据时长；预取URL超时前必须先断言已进入目标竞态，不能把已正常播完下一曲的结果误判为旧回包。

完整设备回归另覆盖 `WebViewNetworkPlaybackTest`、`WeakNetworkPlaybackTest`、`PlaybackStabilityDeviceTest`、20次自然切歌、两个真实五分钟后台歌词，以及默认/AMLL的TTML超时回退。受控服务不能代表真实登录账号心动接口和所有ROM已验证；结果须附对应APK哈希及原始日志，失败重跑不覆盖旧证据。
