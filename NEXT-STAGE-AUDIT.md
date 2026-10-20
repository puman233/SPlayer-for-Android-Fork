> 当前发布状态（2026-10-19）：维护者已验收并明确授权本次 v3.0.16-beta.7，且仅接受本次 Beta 的字体授权证据不足、上游短音频和未确认的历史启动/组合失败风险。问题状态保持开放；历史 BLOCKED 记录保留其当时含义。CI、原签名、人工 Environment 审核和最终资产校验仍必须通过。截图和原始设备日志保留在外部验收材料，不随源码提交。

# SPlayer Android Next Stage Audit

日期：2026-10-16。基线：dev / 9db84b14b28148c045bdd91c6c6061fd4122f433。本文是进行中的证据清单，不代表全仓库或设备验收通过。

## 工程基线

- Vue 3.5 / TypeScript / Naive UI / Pinia / Capacitor 8；原生 Java + Media3 1.8，非 Compose 工程。
- package 与 Android：3.0.16-beta.6 / 30029；applicationId、namespace：top.imsyy.splayer.android；debug 后缀 .debug。
- minSdk 29 / targetSdk、compileSdk 36；AGP 8.13；NDK 28.2.13676358；四 ABI 独立 APK。
- HEAD、现有 beta.6、55df6c59 平板布局均须保留；不修改正式版本、签名、依赖版本、工作流、上游。唯一获批依赖例外：Core 0.5.1 两行局部补丁与必要 pnpm 配置/锁元数据，见 beta.47 记录。
- 初始未跟踪项：.agents/、skills-lock.json、TabletDesktopLayoutTest.java；保留，不计入本阶段变更。
- 已检查本地 Skills 与 lock：项目有 codebase-design、diagnosing-bugs、tdd、ui-ux-pro-max、redesign-existing-projects；code-review 使用环境已安装版本。未安装或更新 Skill。
- 既有 CI：android-ci、android-ui-test、android-preview、release、pages。GitHub 实查 HEAD 的 CI 38887174971、UI 38887777350 成功；只证明旧基线。beta.6 是 prerelease，latest 是 v3.0.15。
- 测试：node:test + tsx，Python unittest，JUnit，Android Instrumentation；没有独立 Kotlin 业务源码编译任务，不虚构 Compose/UI 测试。

## 发现

复现栏明确区分静态证据、实际行为和待验证推断。UNCONFIRMED 不计为已确认 Bug。

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-001 | 更新 | 缺少可选 Beta 通道 | P2 | VERIFIED：AboutSetting.vue 与 helper.ts 调用链无选项，filterUpdateReleases 默认 false | UI/持久化没有通道配置，helper 固定稳定过滤 | 默认 false，共享筛选，明确 Stable/Beta | 是 |
| NS-002 | 更新 | 把 API 第一项直接作为最高版本 | P2 | VERIFIED：乱序/混合标签回归 Red→Green | 发布时间顺序不等于 SemVer 顺序；畸形后缀也曾被接受 | SemVer 降序、排草稿和非法版本，禁止降级 | 已实现，回归通过 |
| NS-003 | 默认歌词 | 手动浏览未回归；视窗/内容变化后默认锚点陈旧 | P1 | VERIFIED：旧版微小偏移不恢复；beta.35平板初始720/回归576，新默认锚点断言Red；beta.36平板Green | 仅手机视窗观察、旧12%安全带、同歌刷新误清手势状态 | 观察视窗及自然内容尺寸；手势/惯性结束3秒后单次平滑回归，任何大于1px偏移均恢复 | VERIFIED：beta.48完整组合普通/AMLL回位及双设备长间奏拖动通过；性能覆盖另见NS-039 |
| NS-004 | AMLL 歌词 | 超时清状态但未重排；初始尺寸变化后锚点也可能陈旧 | P1 | VERIFIED：原版6秒后仍出界；beta.9残余19px；beta.10按相同容器/控件状态保留5px精度通过 | AMLL 超时只清 scrollOffset；尺寸回调未必重排；等待/跟随职责缺口 | 原有 RAF 只观察状态，结束后单次 resetScroll/calcLayout；尺寸更新下一帧重排 | 已实现，beta.10 设备通过 |
| NS-005 | 桌面歌词 | 锁图标遮挡正文及边界不足时偏离左侧 | P1（本轮有限矩阵已修复） | 旧像素遮挡及beta.50全宽窗口回退上下方真实Red | 正文与控件区域混用、左侧不足仍尝试上下候选 | 最新确认仅正文正左侧；不足隐藏并禁触摸；设置提供解锁，正文位置/宽度不变 | beta.51手机2/2和平板含原布局基线3/3通过；系统200%另有beta.37证据 |
| NS-006 | 发布 | 通知字体部分字形再分发证据不足 | P1 | VERIFIED：当前字体 blob ce36d59d06853c0e340c42b43fa804efcd1d2cfd 与 beta.6 审计风险对象一致；未确认侵权 | 缺少可追溯字形来源/授权证据 | 补证或替换；本次发布前单独人工风险授权 | 门禁必须重审，不沿用 beta.6 豁免 |
| NS-007 | 窄屏浮层 | 固定尺寸/触控目标需要全量行为检查 | P2 | UNCONFIRMED；main.scss 已有 modal max-width，不能仅凭 500px 判断溢出 | 各子布局约束与实际内容共同决定 | 以导航及浮层清单逐项检查 320–412dp、200% 字号 | 仅复现项小范围修复 |
| NS-008 | 首页 | 推荐/FM 卡片在320dp越界，放大文字被裁切；FM触控目标过小 | P1 | VERIFIED：原320/200右边界360.01；beta.15四宽度×两字号、88%封面、两卡横排、文字/全宽矩形边界及真实点击通过 | Grid min-content、固定高度、缺少flex最小宽度约束、26/36px操作 | 两推荐卡横排、FM独立行、自然高度、完整长文本、48dp按钮；全宽薄材质向下渐强 | 已实现，beta.15设备通过 |
| NS-009 | 更新生命周期 | 关闭窗口后异步初始化仍注册两个原生监听器 | P2 | VERIFIED：真实Vue挂载→卸载→原生返回的测试观察到2个遗留监听，修复后0 | await期间组件可能销毁，返回后无存活检查 | 每个await之后检查，迟到监听立即移除；关闭后不再弹旧更新 | 已实现，TypeScript回归通过 |
| NS-010 | 更新列表 | 20个连续Beta可能把正式版挤出候选；限流Atom兜底丢失权威通道标记 | P2 | VERIFIED：跨页Stable测试及403/畸形响应测试Red→Green | 固定第一页；Atom不含draft/prerelease标记 | 分页有界读取，排重；保留HTTP错误；升级缓存键排除旧猜测数据 | 已实现，TypeScript回归通过 |
| NS-011 | 设备回归 | beta.9恢复/时钟失败；beta.10顺序全套出现控制区未展开及后台切歌歌词不匹配 | P1（测试门禁） | VERIFIED：beta.10独立五分钟通过，但最终全套6/8；2026-10-17重新连接后主包未列出、重新安装权限default，属于新增环境前置差异 | 软件/设备/宿主负载归因UNCONFIRMED；没有证据认定传感器泄漏或确定已修复 | 分别隔离原失败，恢复必要权限后保留原断言重现；不得用单项通过覆盖全套失败 | 历史控制/时钟组合已有Green；真实短音源样本仍见NS-033，不宣称全部后台场景通过 |
| NS-012 | 设置浮层 | 320dp/200%字体和歌词排除标签逐字竖排 | P1 | VERIFIED：实际截图及真实设置入口Red→Green，beta.12通过 | 字体标签内部固定横排；排除卡片横排与控件最小宽度挤压正文 | Compact纵排、自然宽度、正文滚动与独立操作区 | 已实现，beta.12通过 |
| NS-013 | 共享歌曲空态 | 320dp/200%云盘空态图标与文案重叠 | P2 | VERIFIED：真实云盘Empty的SVG/文案原相交16.7px，修复后不相交 | Naive固定px图标容器与em图标放大不一致 | SongList空态按实际内容布局，保持字号 | 已实现，beta.12通过 |
| NS-014 | 音量交互 | 触屏点击音量触发器同时静音 | P1 | VERIFIED：真实触摸将0.63改为0；修复后手机横屏及平板首次点击保值、滑条及独立静音恢复通过 | 同一次点击执行Popover展开与toggleMute | 触发器仅展开，弹层独立静音，具名48dp按钮 | 已实现，beta.13/.14设备通过 |
| NS-015 | 手机播放器 | 入场后信息区远离播放控件；大屏手机封面过大；用户报告触摸后移位 | P1 | VERIFIED：Red空隙49.44px、封面超300dp；beta.15三尺寸×两字号均通过，360×800前后空隙8px、封面270px稳定。完整原始跳位过程UNCONFIRMED | 布局高度取视觉矩形，受入场scale影响；封面统一380上限 | 流式尺寸使用border-box测量，手机封面300上限；保留底部锚定与信息滚动 | 已实现，beta.15设备通过；原始跳位仍需人工路径核对 |
| NS-016 | 首页密度 | Compact推荐两卡间距20dp、FM留白较多 | P2 | VERIFIED：旧包真实布局间距断言Red；FM新尺寸设备待验 | n-flex内联size=20覆盖CSS；FM固定最小高度 | Compact间距8dp、封面比例与信息/按钮独立约束 | VERIFIED：首页密度/几何已有设备证据；动态背景生命周期beta.44通过 |
| NS-017 | 横屏播放器 | 静置后标签隐藏、封面内部呈方角、控件骤然收起 | P1 | VERIFIED：旧包真实静置标签不可操作；圆角/动画新测试待验 | 标签与控制层共用隐藏状态，内部圆角0，网格瞬变 | 标签常驻，圆角继承，160/180ms淡出与布局动画 | VERIFIED：beta.48完整组合横屏标签/圆角/淡出/操作通过 |
| NS-018 | 批量操作 | 320dp弹窗受70vw限制、五列表格与操作区挤压 | P1 | VERIFIED：真实更多→批量入口，200%文字下宽度断言Red | 通用桌面宽度和固定横排筛选/操作区 | Compact窗口减24dp，固定选择列与水平滚动，操作区换行 | VERIFIED：beta.48真实批量入口选择/滚动/操作区通过 |
| NS-019 | TTML | 开启后当前歌曲仍用旧LRC、逐词开关可能关闭 | P1 | VERIFIED：公共歌词管理入口Red→Green，真实TTML解析13项通过；在线设备效果待验 | 只改配置，不重载当前歌词；优先级和逐词设置独立 | 共享setOnlineTTML即时重载，开启逐词；无TTML真实回退 | VERIFIED：单元及beta.48受控TTML开关/逐词渲染入口通过；不等于所有真实歌曲有TTML数据 |
| NS-020 | 心动模式 | 播放页不能独立再次进入心动；本地ID与旧请求回滚风险 | P2 | VERIFIED：旧包独立按钮缺失；本地种子5→101与旧请求回滚回归Red→Green | 入口依赖随机按钮；本地ID误作在线ID；过期异常回滚 | 独立48dp入口、有效收藏种子、取消保护 | VERIFIED：beta.48真实按钮循环及单元失败回滚通过；推荐服务全部状态仍未覆盖 |
| NS-021 | 播放菜单 | 用户报告选择后不立即生效 | P2 | UNCONFIRMED：旧包竖屏受控真实触摸通过；音质API等待才展开为高可信静态发现 | 等待API未提供即时加载反馈；完整失效根因未确认 | 先展开加载反馈、丢弃换歌后旧结果；More原生按钮 | IMPLEMENTED，需网络实际切换和人工路径 |
| NS-022 | 每日推荐大字号 | 320dp/200%标题越界，更多被列表悬浮工具覆盖，选项文字超出固定行高 | P2 | VERIFIED：beta.26标题/触摸Red、beta.28菜单文字Range/48dp Red；beta.30手机和平板默认/200%真实操作通过 | 固定300px标题及固定选项行高不适应文字放大；共享固定工具超出自身列表区域 | Compact自然标题/菜单行高、48dp、文字换行；工具仅在自身列表区域显示，宽屏保持 | VERIFIED：beta.30手机2/2，平板含原布局基线3/3；不代表全页面大字号通过 |


| NS-023 | 首页随机封面 | Compact新组件固定取第一首，丢失原随机展示 | P2 | VERIFIED：旧beta.18行为Red；beta.19/.21完整列表随机取第二项通过 | 错误使用data[0] | 恢复列表内随机选择，列表不变时稳定 | VERIFIED |
| NS-024 | 设备测试隔离 | 隔离包音源配置仅剩Kuwo | P1 | VERIFIED：实际持久化缺默认项；污染具体发生时刻UNCONFIRMED | 测试曾整数组覆写，异步恢复缺少完成与持久化检查 | 删除覆写，等待恢复并校验持久化；已恢复缺失默认项 | 已修复测试隔离，恢复检查通过；不等同真实服务可用 |
| NS-025 | 音源状态 | 音质/音源切换后currentAudioSource仍保存旧链接 | P1 | VERIFIED：公共切换行为测试Red→Green，16项竞争测试通过 | 仅更新UI和传输链接，没有更新后续原生队列同步使用的当前源 | 同一有效请求中同步更新当前源 | VERIFIED：全部TypeScript348项通过 |
| NS-026 | 手机歌词页 | 清空/加载歌词时整个分页及入口消失 | P1 | VERIFIED：旧包Red，beta.19真实分页触摸和切歌加载保持页面通过 | 用isHasLrc决定页面是否存在 | 歌曲歌词页常驻，加载/空/有歌词为页内状态 | VERIFIED |
| NS-027 | 首页标题与材质 | 预留空白行让标题离底部过远；用户报告材质使底角变方 | P2 | VERIFIED：320dp文字底部留白Red→Green；矩形具体触发状态UNCONFIRMED，角点像素对照及截图通过 | 2.8em最小标题高度；流式图片需独立裁切 | 移除空白行，保留4dp底部内距、6dp侧距，图片与共同材质边界圆角裁切 | VERIFIED：beta.21四宽度×两字号通过 |
| NS-028 | 合并标签浮层 | 极窄屏选项菜单存在但无法触摸 | P1 | VERIFIED：beta.21真实选择“自动”九个命中点均失败 | 子菜单挂在 overflow:hidden 的父浮层内，超出父框部分被裁切；仅调整层级不足以修复 | 合并模式将三个子菜单挂到body；正常模式沿用原挂载 | VERIFIED：beta.23真实触摸选择通过，保留beta.21/.22失败证据 |
## 架构与审查边界

- 导航：src/router/routes.ts；播放器属于全局浮层，不是路由。设置经 utils/modal.ts 打开，八组设置；账号、歌词工具、更新等不能仅靠路由清点。
- 播放：PlayerController / SongManager / AudioManager → AndroidNativeAudioPlayer → AndroidNativePlaybackPlugin → PlaybackManager / PlaybackService / MediaSession。需检查切歌竞态、队列、恢复、通知和后台。
- 歌词：songLyric → DefaultLyric 或 AMLyric → AMLL/LyricPlayer；桌面歌词走原生 FloatingLyricService，不能混淆两个滚动/绘制系统。
- 下载：DownloadManager → AndroidDownloadPlugin / DownloadTransfer；当前 removeDownload 已有原生 cancel，必须以当前测试验证，不能复用旧审计“缺少取消”的结论。
- 更新：api/other.ts → helper.getUpdateLog → AboutSetting → AndroidUpdateApp → AndroidAppUpdate / ApkDownload。缓存原始 Release 后再筛通道，避免通道串缓存。
- 资源：SVG 与通知字体、原生 drawable、Web CSS；dp、CSS px、页面 zoom、系统 fontScale 需分别测量。
- 已完成修改的二次重读和回归已发现并修复同歌刷新手势与更新监听生命周期遗漏；AMLL和最终设备回归仍以最新结果为准。未完成所有调用路径逐行审查、真实账号/音源/下载网络、全部浮层状态和最终设备全量巡检；不得把本表当完整无缺陷证明。

## 参考依据

- Android 窗口分类：http[本地路径已省略] （只采用窗口概念，不迁移 Compose）
- Android 可访问性：http[本地路径已省略]
- Android 14 字体缩放：http[本地路径已省略]
- GitHub Release API：http[本地路径已省略]
- 上游本地只读基线：2e3d414a35ed966dad01d425dd5f3d00cc01c96c；默认歌词的 wheel-only 设计可在上游对应文件找到；不修改上游。

## 实际代码导航与浮层清单

来源：router/routes.ts、utils/modal.ts、全部 Vue 模板。静态存在不代表设备通过；下表是逐项覆盖台账。

### 路由视图

| 文件 | 设备状态 |
|---|---|
| src/views/Home/index.vue | 待全量验证 |
| src/views/Search/layout.vue | 待全量验证 |
| src/views/Search/songs.vue | 待全量验证 |
| src/views/Search/playlists.vue | 待全量验证 |
| src/views/Search/artists.vue | 待全量验证 |
| src/views/Search/albums.vue | 待全量验证 |
| src/views/Search/videos.vue | 待全量验证 |
| src/views/Search/radios.vue | 待全量验证 |
| src/views/Discover/layout.vue | 待全量验证 |
| src/views/Discover/playlists.vue | 待全量验证 |
| src/views/Discover/toplists.vue | 待全量验证 |
| src/views/Discover/artists.vue | 待全量验证 |
| src/views/Discover/new.vue | 待全量验证 |
| src/views/Artist/layout.vue | 待全量验证 |
| src/views/Artist/songs.vue | 待全量验证 |
| src/views/Artist/albums.vue | 待全量验证 |
| src/views/Artist/videos.vue | 待全量验证 |
| src/views/Video.vue | 待全量验证 |
| src/views/List/album.vue | 待全量验证 |
| src/views/Song/wiki.vue | 待全量验证 |
| src/views/Comment.vue | 待全量验证 |
| src/views/List/playlist.vue | 待全量验证 |
| src/views/List/streaming-playlist.vue | 待全量验证 |
| src/views/List/radio.vue | 待全量验证 |
| src/views/Radio/hot.vue | 待全量验证 |
| src/views/Radio/type.vue | 待全量验证 |
| src/views/List/liked.vue | 待全量验证 |
| src/views/Cloud.vue | 待全量验证 |
| src/views/DailySongs.vue | 待全量验证 |
| src/views/Like/layout.vue | 待全量验证 |
| src/views/Like/playlists.vue | 待全量验证 |
| src/views/Like/albums.vue | 待全量验证 |
| src/views/Like/artists.vue | 待全量验证 |
| src/views/Like/videos.vue | 待全量验证 |
| src/views/Like/radios.vue | 待全量验证 |
| src/views/Download/layout.vue | 待全量验证 |
| src/views/Download/downloaded.vue | 待全量验证 |
| src/views/Download/downloading.vue | 待全量验证 |
| src/views/Local/layout.vue | 待全量验证 |
| src/views/Local/song.vue | 待全量验证 |
| src/views/Local/artists.vue | 待全量验证 |
| src/views/Local/albums.vue | 待全量验证 |
| src/views/Local/folders.vue | 待全量验证 |
| src/views/Local/playlists.vue | 待全量验证 |
| src/views/Streaming/layout.vue | 待全量验证 |
| src/views/Streaming/song.vue | 待全量验证 |
| src/views/Streaming/artists.vue | 待全量验证 |
| src/views/Streaming/albums.vue | 待全量验证 |
| src/views/Streaming/playlists.vue | 待全量验证 |
| src/views/History.vue | 待全量验证 |
| src/views/Status/403.vue | 待全量验证 |
| src/views/Status/404.vue | 待全量验证 |
| src/views/Status/500.vue | 待全量验证 |

### Modal 与账号子界面

| 文件 | 设备状态 |
|---|---|
| src/components/Modal/ABLoop.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/AndroidUpdateApp.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/AutoClose.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/BatchList.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/ChangeRate.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/CloudMatch.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/CopyLyrics.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/CopySongInfo.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/CreatePlaylist.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/DownloadModal.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Equalizer.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/JumpArtist.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Login/Login.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Login/LoginCookie.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Login/LoginPhone.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Login/LoginQRCode.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Login/LoginUID.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/LyricPoster.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/PlaylistAdd.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/ScalingModal.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/AMLLServer.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/ContextMenuManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/CoverManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/CustomCode.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/ExcludeComment.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/ExcludeLyrics.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/FontManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/FullscreenPlayerManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/HomePageSectionManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/LocalMusicDirectory.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/PlaylistPageManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/SidebarHideManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/SongUnlockManager.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/Setting/StreamingServerConfig.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/SongInfoEditor.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/ThemeConfig.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/UpdateApp.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/UpdatePlaylist.vue | 待入口/打开/关闭/长文本验证 |
| src/components/Modal/UserAgreement.vue | 待入口/打开/关闭/长文本验证 |

### 内联浮层与状态容器

| 文件 | 实际控件 | 设备状态 |
|---|---|---|
| src/views/Cloud.vue | n-dropdown, n-empty | 待点击后检查 |
| src/views/DailySongs.vue | n-dropdown | 待点击后检查 |
| src/views/History.vue | n-empty | 待点击后检查 |
| src/views/Video.vue | n-skeleton | 待点击后检查 |
| src/views/Artist/layout.vue | n-dropdown, n-skeleton | 待点击后检查 |
| src/views/Discover/playlists.vue | n-modal | 待点击后检查 |
| src/views/Discover/toplists.vue | n-skeleton | 待点击后检查 |
| src/views/Download/downloading.vue | n-empty | 待点击后检查 |
| src/views/Home/HomeMobile.vue | n-empty, n-spin | 待点击后检查 |
| src/views/List/album.vue | n-empty | 待点击后检查 |
| src/views/List/liked.vue | n-empty | 待点击后检查 |
| src/views/List/playlist.vue | n-empty | 待点击后检查 |
| src/views/List/radio.vue | n-empty | 待点击后检查 |
| src/views/List/streaming-playlist.vue | n-empty | 待点击后检查 |
| src/views/Local/layout.vue | n-dropdown, n-empty | 待点击后检查 |
| src/views/Radio/hot.vue | n-skeleton | 待点击后检查 |
| src/views/Search/albums.vue | n-empty | 待点击后检查 |
| src/views/Search/artists.vue | n-empty | 待点击后检查 |
| src/views/Search/playlists.vue | n-empty | 待点击后检查 |
| src/views/Search/radios.vue | n-empty | 待点击后检查 |
| src/views/Search/songs.vue | n-empty | 待点击后检查 |
| src/views/Search/videos.vue | n-empty | 待点击后检查 |
| src/views/Song/wiki.vue | n-dropdown, n-empty, n-skeleton, n-spin | 待点击后检查 |
| src/views/Streaming/layout.vue | n-dropdown, n-empty | 待点击后检查 |
| src/components/Card/SongDataCard.vue | n-skeleton | 待点击后检查 |
| src/components/Global/Provider.vue | n-dialog, n-modal | 待点击后检查 |
| src/components/Layout/Nav.vue | n-drawer, n-dropdown, n-modal | 待点击后检查 |
| src/components/Layout/User.vue | n-popover | 待点击后检查 |
| src/components/List/ArtistList.vue | n-empty, n-skeleton | 待点击后检查 |
| src/components/List/CommentList.vue | n-empty, n-skeleton | 待点击后检查 |
| src/components/List/CoverList.vue | n-empty, n-skeleton | 待点击后检查 |
| src/components/List/ListDetail.vue | n-dropdown, n-popover, n-skeleton | 待点击后检查 |
| src/components/List/SongList.vue | n-empty, n-popover, n-skeleton, n-spin | 待点击后检查 |
| src/components/List/SongPlayList.vue | n-drawer, n-empty | 待点击后检查 |
| src/components/Menu/CoverMenu.vue | n-dropdown | 待点击后检查 |
| src/components/Menu/MobileSongMenu.vue | n-drawer | 待点击后检查 |
| src/components/Menu/SearchInpMenu.vue | n-dropdown | 待点击后检查 |
| src/components/Menu/SongListMenu.vue | n-dropdown | 待点击后检查 |
| src/components/Modal/AndroidUpdateApp.vue | n-alert, n-spin | 待点击后检查 |
| src/components/Modal/BatchList.vue | n-popover | 待点击后检查 |
| src/components/Modal/CopySongInfo.vue | n-spin | 待点击后检查 |
| src/components/Modal/DownloadModal.vue | n-alert | 待点击后检查 |
| src/components/Modal/Equalizer.vue | n-alert | 待点击后检查 |
| src/components/Modal/JumpArtist.vue | n-alert, n-skeleton | 待点击后检查 |
| src/components/Modal/PlaylistAdd.vue | n-empty | 待点击后检查 |
| src/components/Modal/UpdateApp.vue | n-alert | 待点击后检查 |
| src/components/Modal/UserAgreement.vue | n-alert | 待点击后检查 |
| src/components/Player/FullPlayerMobileLandscape.vue | n-popover | 待点击后检查 |
| src/components/Player/MainPlayer.vue | n-dropdown | 待点击后检查 |
| src/components/Player/PlayerQuickActionsMenu.vue | n-popover | 待点击后检查 |
| src/components/Player/PlayerRightMenu.vue | n-dropdown, n-popover, n-popselect | 待点击后检查 |
| src/components/Player/PlayerLyric/index.vue | n-popover | 待点击后检查 |
| src/components/Player/PlayerMeta/PlayerData.vue | n-popover, n-popselect | 待点击后检查 |
| src/components/Modal/Login/LoginCookie.vue | n-alert | 待点击后检查 |
| src/components/Modal/Login/LoginQRCode.vue | n-skeleton | 待点击后检查 |
| src/components/Modal/Login/LoginUID.vue | n-alert | 待点击后检查 |
| src/components/Modal/Setting/AMLLServer.vue | n-alert | 待点击后检查 |
| src/components/Modal/Setting/CustomCode.vue | n-alert | 待点击后检查 |
| src/components/Modal/Setting/LocalMusicDirectory.vue | n-empty | 待点击后检查 |
| src/components/Modal/Setting/SongUnlockManager.vue | n-alert | 待点击后检查 |
| src/components/Modal/Setting/StreamingServerConfig.vue | n-alert | 待点击后检查 |

额外全局入口：AppLayout、FullPlayer、FullPlayerMobile、FullPlayerMobileLandscape、PlayerLyric 的两种引擎；PlayerMenu/RightMenu/QuickActionsMenu；Setting 八组与目录 SAF 系统选择器；message/dialog/notification provider；原生通知/MediaSession/悬浮窗。设置中 Electron-only 分组应记录 Android 不可达，不能当作遗漏或通过。

## 2026-10-18 最新增量：beta.23–.26（覆盖旧阶段结论）

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-029 | 播放信息操作区 | 390dp有空间却收纳桌面歌词/队列 | P2 | VERIFIED：旧包几何回归Red，beta.23八种宽度/字号组合通过 | 按固定动作宽度及静态标签48dp测量，过度估算所需空间 | 按实际动作数量和可交互标签自然宽度测量 | VERIFIED：beta.23 |
| NS-030 | AMLL模糊 | 用户打开模糊，但鼠标悬停覆盖效果 | P2 | VERIFIED：真实触摸对照通过、真实鼠标悬停旧包Red，beta.24/.25通过 | 上游Core的:hover规则用!important清空filter | Android包装层在原更新帧保留Core自身计算filter优先级；关闭后清零 | VERIFIED：beta.25触摸/鼠标/关闭 |
| NS-031 | TTML加载 | 已就绪的优先TTML等待低优先级LRC | P2 | VERIFIED：延迟HTTP边界回归先Red后Green | 顺序无条件等待fallback | TTML有效时不发起fallback，由现有最终处理派生普通歌词 | 单元VERIFIED；真实全部格式速度仍待设备计时 |
| NS-032 | 自动歌词加载 | 慢TTML阻塞已就绪LRC/YRC；可选来源异常清空成功结果 | P1 | VERIFIED：两个延迟/异常边界先Red后Green | Promise.all后才提交、来源异常使整体回到空结果 | 自动模式先提交官方歌词，TTML随后升级；复用排除/简繁/请求代次，分别处理来源失败 | VERIFIED：20项相关单元、beta.26手机/平板渐进渲染通过；最终手机顺序套件待收尾 |
| NS-033 | 后台桌面歌词/音频切换 | 后台自然结束后目标曲未持续播放/显示 | P1 | VERIFIED：beta.26五分钟正常速度失败；三倍速自然结束重复失败；不以短时Next通过关闭 | 已确认先trackChanged到186016且提交50行歌词，再收到2612ms的真实ended并顺序回首曲。beta.30进入目标时首个src对应缓存/独立HTTP206均16009bytes、2612ms，非loopback。之前34MB/403属于诊断误捕获上一首源，证据作废。短音源为何被接受及其他歌曲后台行为尚待验证 | 保留原失败样本，补充另一首公开歌曲对照；不盲删ended，不把短片段结束当成重复事件 | 未关闭；诊断进行中 |
| NS-034 | 后台原生队列 | 未显式补窗的真实在线下一首未切换 | P1 | VERIFIED：60秒原生仍为28941713；resolver记录186016失败；具体根因UNCONFIRMED | 首次推窗早于JS解锁预载完成；完成后未再次推送，原生解析目标失败 | 预载完成且仍为当前歌曲、开关开启时补交原生窗口 | VERIFIED：beta.25无额外补窗Red，beta.26相同入口Green |

桌面歌词背景目前仅独立设计预览，不计作产品已实现或Android已验证。全量页面/状态覆盖缺口、NS-033后台问题及字体授权P1门禁均未关闭。未提交、推送或发布。

## beta.31–.34 证据更新

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-035 | 原生悬浮窗 | 配置变更后safeArea不更新，临时显示器失效时创建窗口异常 | P1 | VERIFIED：beta.31低高度断言失败；beta.32/34两台完整原生入口通过 | 非UI Service上下文不能可靠承载窗口配置；创建显示器上下文也需置于异常边界内 | 单一窗口UI上下文创建View/WM、配置回调成对释放、处理无效显示器 | VERIFIED：beta.34低高度/锁定/解锁及平板基线；非所有ROM证明 |
| NS-036 | 首页FM | 长文字被固定右侧高度裁切 | P2 | VERIFIED：beta.30父级边界Red、截图可见；beta.31两台八组宽度/字号通过 | 固定info等于封面高度，48dp控件挤占文字，祖先裁切未被旧断言发现 | Compact自然高度、metadata不收缩，保留封面上限与48dp | VERIFIED：短标题紧凑与长文本完整均通过 |

NS-005锁位置以最新用户参照为准：beta.34共享未锁定工具栏几何，左/上各4dp；真实手机、平板、200%系统字号验证通过，正文不变且零遮挡。NS-033新增Echo正常速度五分钟和三倍速对照通过，但原186016短音源原因仍未解决。平板组合套件4项入口缺失与全量状态覆盖仍待收尾，不记作通过。显示器编号必须有效且一致，不能以MuMu下字面0为产品验收条件。发布P1门禁未解除。

## 2026-10-18 beta.35–.37 补充审计（阶段未完成）

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-037 | 本地/流媒体分类 | 空扫描/空结果仍永久显示骨架 | P2 | VERIFIED：旧版两项设备回归Red，beta.35/.36平板Green，覆盖五个分类 | 把列表为空当作loading | 使用父级实际loading，空态与加载态分离 | VERIFIED：beta.48本地/流媒体空分类入口通过 |
| NS-039 | 歌词拖动性能/跟随 | 手动位置插值、跨句与间奏插入布局造成拖动迟滞/跳位 | P1 | VERIFIED：原增益0.339、跨句跳120dp、间奏跳26dp；beta.47延迟/普通跨句及beta.48双设备间奏回归Green，完整组合仍复测 | 手动CSS插值、布局目标切换和间奏高度变化已确认；其余设备延迟边界仍待覆盖 | 手动时停止位置插值/暂停几何弹簧，Core固定浏览锚点及间奏布局，松手恢复当前播放句；保留原阈值 | VERIFIED：beta.48双设备100/200%拖动/点击/长按、跨句/间奏回位与完整24项通过；关闭已复现项，保留测试范围 |
| NS-040 | 大字号歌曲列表 | 320dp/200%“操作”列标题逐字竖排并越出40px表头 | P2 | VERIFIED：beta.37 Range两行77.8px Red；beta.38手机/平板默认及200% Green | 操作列固定40px且允许收缩/折行 | 仅Compact操作列自然宽度、不可压缩、不折行；保留列表高度和字体 | VERIFIED：手机21项组合与平板3项组合通过 |

- 可用空间按钮分级：依次显示全部、喜欢+队列+更多、喜欢+更多；保留三个标签自然尺寸。beta.36平板真实360/412入口通过，手机最新回归待完成。
- beta.36账号登录布尔状态确认有效，八个账号路由加载完成，16张导航/设置观察无横向outlier；截图仍发现NS-040，不能称全UI通过。
- 账号交互4项2通过（批量、云盘空态），每日推荐两项失败在最后浮动工具断言。追加诊断显示工具容器已正常位于列表内，但当前歌曲定位按钮未生成；需核对合成列表与真实原生当前曲的测试前提，不据此直接修改产品。
- beta.36平板组合16/16（112.971秒），另默认锚点/最新锁位/受保护平板基线3/3；不覆盖手机失败或逐词性能失败。
- Release仍BLOCKED—P1（NS-006），另NS-033、NS-039及全量状态覆盖未关闭。没有commit/push/Tag/Release。

beta.37补充：最新左侧锁位手机默认/200%分别1/1通过；每日推荐当前曲成员前提修正后2/2通过。NS-040新增真实Red后beta.38最小样式已IMPLEMENTED，Green待验。手机逐字+弹簧固定基准字体完整拖动/seek/长按通过39.479秒；不覆盖旧平板延迟失败。原costs探针没有匹配实例，禁止按空数组宣称引擎耗时为0。shallowRef与Vue官方第三方复杂实例处理建议一致，但单点效果尚不能从现有样本中独立归因。

2026-10-18 beta.38收尾增量：手机21/21完整组合通过260.811秒，包括此前手机失败的默认锚点、AMLL hover、标签/更多、横屏菜单、账号日推/云盘、五分类空态，以及八组播放器尺寸/字号和音量。平板默认/200%日推表头及原TabletDesktopLayoutTest 3/3通过20.034秒。不能将该组合通过扩展为全部网络音源、所有导航状态或平板性能通过。最新平板逐词/弹簧仍106.4ms输入延迟Red，NS-039不关闭。

补充待验证风险（UNCONFIRMED，不计已确认Bug）：Radio/type.vue的onBeforeRouteUpdate读取query.type，而首次/激活读取query.id；可能导致同组件分类切换用NaN请求。尚未完成真实分类切换复现，不直接修改。公开播客和分类API现已取得有效样本，继续补导航覆盖；流媒体歌单仍需有效服务配置，不能假造真实连接。


## beta.39 本轮增量（尚未完成最终验收）
- 构建：Web、Debug/Release APK、AndroidTest、Android Unit、Lint 均通过；完整 Gradle 1m55s。pnpm lint/typecheck 通过。正式版本仍 beta.6，未修改签名/工作流/依赖。
- beta.39 debug/30062 已覆盖安装两台设备，保留用户数据。arm64 APK 64,438,792 bytes，SHA256 FD2E352BBEDDED95299208246F86F849673C8FD7069F76BFBB2265FF316C5662，APK v2 验签通过。
- NS-039：真实 Chrome 轨迹发现触摸起始命中查询先遍历 getBoundingClientRect，导致同步样式计算。最小改为先通过 DOM 包含关系命中，只有未命中才查询坐标；保留长按阈值、手动浏览、回位和动画。
- beta.39 平板逐字+弹簧真实系统触摸、回位后点击 seek、长按菜单完整 1/1 通过（31.154秒）：普通最大26.1ms/位移增益1.009，AMLL最大88.8ms/增益1.000，无观测到的长任务。
- 手机同入口拖动采样普通最大78.8ms、AMLL最大22.7ms，但后续激活行点击目标未生成，整项失败54.832秒；不能仅凭延迟采样关闭NS-039，继续隔离双引擎测试初始化与产品状态。
- 性能历史反例保留：beta.38 手机逐字+弹簧真实系统输入155.1ms、84/58ms长任务；同步主线程注入和带Profiler测试不能单独用于归责或验收。
- 测试辅助 p7Init 增加可选 beforeRender 回调，在真正暂停原生后、渲染合成歌曲前固定公开 getSeek 时钟，现有无参调用不变。没有更改任何性能阈值或删除断言。
- 一次错误APK文件名导致未安装beta.39的测试已更名 beta38-with-fixture39-invalid-version-phone.log，不计入beta.39证据；随后使用实际分架构APK安装并核实版本。
- Release 仍 BLOCKED—P1，字体授权、NS-033短音源、NS-039和全量状态覆盖仍未关闭。未commit/push/CI/Tag/Release。


| NS-041 | 大字号共享歌曲列表 | 固定90px虚拟行内标题/歌手在200%被裁切 | P2 | VERIFIED：beta.39真实320dp/200%截图 | SongCard固定90px且VirtualScroll定高；内容增高未计入 | 先建立正文边界Red，研究现有动态测量模式，同步定位/恢复滚动索引 | VERIFIED：动态行高修复，beta.48日推正文/200%组合通过，平板基线通过 |
| NS-042 | 迷你播放器 | 大字号歌手越出播放器，固定副标题高度裁切 | P2 | VERIFIED：beta.39真实截图 | phone栏固定64px、副标题18px与可缩放文本不匹配 | 自然文本/栏高度，内容预留随实际栏高更新；不缩字 | VERIFIED：beta.48 phone/auto模式、100/200%迷你栏与真实封面打开入口通过 |
| NS-043 | 视频详情 | 作者/操作菜单与简介重叠 | P2 | VERIFIED：有效MV10928968加载后真实截图 | info固定60px、menu固定40px，换行后父级未增高 | Compact自然高度、作者与操作分组换行 | VERIFIED：beta.48真实视频详情作者/操作区/简介几何通过 |


### beta.39后续诊断与beta.40进行中
- 组合失败诊断：合成歌曲ID99007102仍在，但100行歌词被替换成真实歌曲23行；这是测试直接改Store没有使旧歌词请求失效。p7Init现通过公开setupSongUI初始化无服务器标识的流媒体占位曲，等待处理结束再装载合成歌词，不操作私有请求代次、不清用户数据。普通歌词原回位入口重新1/1通过34.86秒；原21项组合20/21失败记录保留，不能记为全通过。
- 手机逐字性能仍未关闭：稳定数据下原系统连续输入159.1ms；公开Instrumentation定频系统输入对照最高124ms，位移正常但延迟断言失败。未通过换注入方式或降低阈值“修复”性能；不再将剩余延迟单独归因于测试。
- 真实媒体巡检：首次三项缺输入、第二次视频仍loading，均未算通过。等待本地服务并外部只读公开API核实ID后，MV10928968、播客1230174483、情感分类3在320dp/200%加载完成，所采横向越界0。但截图发现视频菜单重叠NS-043；不能将横向越界0等同UI正常。
- NS-042/043两个新回归入口已获用户同意。beta.39旧布局两项2/2 Red：歌手文字超出64px迷你栏及18px副标题，视频作者/控制区超出40px父级菜单并与简介相交。
- NS-042 beta.40 IMPLEMENTED：phone自然栏高/副标题行高、48dp封面和播放目标；通过组件heightChange把实际栏高传到AppLayout底部预留。宽屏横版未改。lint/typecheck/Web构建通过，Android设备Green待执行。NS-041共享列表正文、NS-043视频尚未修改。
- 已形成新Before截图beta39-media-video.png、beta39-media-radio-type.png、beta39-daily-header-200.png。后者证明操作列表头已横排，但正文和迷你栏仍不合格。
- 同代理二轮报告已更新为3项HIGH开放门禁（字体、NS-039、NS-033），不是独立人工批准。Release BLOCKED—P1；无commit/push/CI/Tag/Release。


## beta.40–.43 可读性增量（最终验收仍未完成）
- NS-042迷你栏：旧版真实两字号入口Red后，beta.40手机1/1 22.332秒、平板1/1 5.966秒通过。自然栏高和副标题、48dp封面/播放目标，真实栏高传给AppLayout页面底部预留。
- NS-043视频详情：旧版菜单高40px，作者高89.59px、操作高76px越界并覆盖简介；beta.41限定Compact自然高度和作者/操作分组，两项媒体/迷你回归手机2/2 39.614秒、平板2/2 23.67秒通过。视频元数据和业务未改。
- NS-041正文行：第一次Range把行省略外的隐藏标题全部算入高度（773px），该自动结论无效。修正为实际标题行框和歌手文字后，旧版仍真实Red：歌手越出90px行约11.43px。beta.42复用非定高VirtualScroll、自然SongCard最小90px、动态ResizeObserver、布局offsetHeight测量及按实际高度表计算滚动索引。默认/200%日推与原平板横屏基线3/3通过27.712秒，截图beta42-daily-200-after.png正文不再相互覆盖。
- beta.42完整手机23/23通过360.837秒，包含歌词回位/TTML/心动/标签/更多/横屏/批量/空态/日推/字体设置/布局/音量以及新增视频和迷你栏。使用已修正公开初始化的合成数据边界；不替代性能测试。
- 视觉复核新缺口：物理平板320dp自动模式恢复全部迷你控制，导致歌曲标题/歌手宽度均0；强制phone模式此前未覆盖。相同入口增加phone/auto各100/200%后beta.42 Red确认。beta.43仅将平板恢复扩展控制条件增加min-width600px，IMPLEMENTED待Green。旧宽屏控制保留。
- beta.41全套Gradle Debug/Release/AndroidTest/Unit/Lint通过1m2s，lint/typecheck通过。beta.42 Debug/AndroidTest通过23s，lint/typecheck通过；beta.43全套检查进行中。
- 性能NS-039仍开放：公开系统定频输入124ms、旧系统连续输入159.1ms反例均保留。普通歌词34.86秒回位Green不能代表逐字性能已关闭。
- 新增布局文件：MainPlayer、AppLayout、Video、SongCard、VirtualScroll；均为当前已证实问题的最小调整。没有修改受保护TabletDesktopLayoutTest、正式版本、依赖、签名、工作流、README/CHANGELOG。
- Release BLOCKED—P1。没有commit/push/CI/Tag/Release，最终仍须人工验收。

| NS-044 | 首页渲染生命周期 | 全屏播放器覆盖首页时两个动态材质仍持续绘制 | P2 | VERIFIED：beta.43真实触摸打开播放页后750ms仍24次WebGL绘制，生命周期回归Red | IntersectionObserver只判几何可见，不知道全屏播放器遮挡；animate缺少showFullPlayer条件 | 播放器打开释放首页renderer，关闭后沿现有逻辑恢复；不改封面/文字/播放/歌词效果 | VERIFIED：beta.44真实入口1/1通过31.607秒；覆盖时停止绘制，关闭后恢复 |

## beta.44–.47 歌词拖动专项增量
- VERIFIED：beta.44 首页卡片在全屏播放器覆盖时停止 WebGL 绘制，关闭播放器后恢复；真实入口回归1/1通过31.607秒。beta.43反例为覆盖期间仍绘制24次/750ms。
- VERIFIED（限定样本）：beta.46保留用户弹簧设置，仅在Android手动浏览时暂停位置弹簧，回位前恢复。逐字+模糊+弹簧设置开启，手机连续输入、手机定频输入、平板连续输入分别通过52.453/46.087/32.331秒；AMLL延迟P95分别73.0/83.6/93.5ms，位移增益均1.000。历史失败样本保留，不能据此关闭整个性能问题。
- 已复现：正常播放跨句时，AMLL在手指按住期间仍换布局锚点，位置跳动120dp（354.7→234.7）；默认歌词同项通过。证据beta46-held-line-advance-tablet.log。归入NS-039，尚未关闭。
- 用户明确批准：保留Core 0.5.1，添加两行运行时补丁及必要pnpm配置/锁元数据。补丁仅在isScrolled时保留已有布局锚点，播放时钟与高亮仍更新。package.json增加patchedDependencies，pnpm-lock.yaml只增加补丁路径/哈希和两个引用，不升级依赖。
- IMPLEMENTED：beta.47补丁已应用；回归增加跨句后继续拖动、松手后当前句回默认锚点。Web构建、ESLint、TypeScript检查通过，Android构建和设备验证待完成。
- beta.45实验性滚动合并适配器未解决延迟，已撤回，不在最终源码中。
- VERIFIED：手机真实QR账号巡检16个账号/设置入口通过35.005秒，报告确认非合成账号；不等于所有页面所有状态全覆盖。
- Release仍BLOCKED—P1：通知字体授权证据不足及已有音源短文件问题未关闭。没有commit/push/CI/Tag/Release，仍需最终人工验收。

### beta.47 局部补丁设备证据（2026-10-19）
- VERIFIED：跨句按住→继续拖动35dp→松手自动回位，两种歌词引擎均通过；手机53.352秒，平板34.926秒。旧包AMLL跨句120dp反例保留。实际播放高亮已推进下一句，而浏览位置保持；继续拖动位移、回到当前句默认锚点均有独立断言。
- VERIFIED：平板逐字+模糊+弹簧设置开启的拖动响应入口通过32.62秒（同一延迟阈值、位移阈值，含点击跳转和长按）。手机性能复测尚在进行。
- beta.47 Web构建、lint/typecheck、Android Debug/Release/AndroidTest构建、Unit与Lint全套成功48秒。APK64439332字节，SHA256 6D1CDF7B45E3BCDFBB336A5BED17A79151D281E98B69EF1339688EBC5F2D46E7，v2签名验证通过，证书沿用隔离测试包。
- 补丁复核：CommonJS与ESM各一行且一致，resetScroll与seek保持原路径解除浏览锚点；未改播放时钟/逐字高亮。长间奏、动态行高与完整设备矩阵仍需回归，不以本项Green替代最终验收。

### beta.47 复核补充
- 手机拖动回归通过47.808秒，普通歌词P95 64.1ms/增益1.028，AMLL P95 95.3ms/增益0.9996；平板32.62秒，普通48.8ms/1.095、AMLL89.4ms/1.0009。均保留逐字、模糊、用户弹簧开启及原阈值；延迟余量有限，不能扩推所有设备。
- 原平板横屏基线通过17.665秒，未编辑受保护测试文件。
- 手机整组首次启动出现libnode.so ParserBase::ParseFormalParameterList原生SIGSEGV（进程启动4秒）；保留beta47-functional-phone-crash.log。尚不能归因到歌词补丁或模拟器，记为UNCONFIRMED根因、未关闭风险，不能用随后重跑覆盖失败记录。
- 首次启动列表另有一处测试类名误写，已改用实际CompactContentReadabilityTest，完整24项正在回归。
- 当前是同一代理重新读补丁及调用链的代码复核，不等同于独立人工审查。最终发布仍BLOCKED，尚未请求commit/push授权。

## 启动崩溃待定位（2026-10-19）
| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-045 | 原生运行时/启动 | 手机MuMu中隔离包启动4秒后，libnode.so ParserBase::ParseFormalParameterList发生SIGSEGV，整组测试中止 | P0（按崩溃临时门禁；归属待定位） | 已发生一次，beta47-functional-phone-crash.log有原生堆栈；后续单项及组合有成功启动 | UNCONFIRMED：未证明歌词补丁、应用运行时或模拟器转译层责任 | 保留原始失败，做受控启动对照，禁止用重跑Green抹掉崩溃；归属查清前不宣称P0=0 | 尚未修复/待定位 |

整组第一次重跑日志停在第10/24项，连接与会话在恢复工作时已不存在，缺最终结果；计为中断，不计为通过或代码断言失败。重连后保留同一beta.47包重新执行完整24项。

### beta.48：间奏造成的手动位置跳动（同一已批准回归入口）
- beta.47长间奏真实触摸回归Red：进入间奏时，保留的歌词行从354.7移到328.7，跳26dp。原两行补丁仅固定目标行索引，未固定间奏圆点插入布局。
- 设计：手动浏览时同时复用最近一次自动布局的间奏元数据；正常播放、恢复跟随与seek仍计算当前间奏。播放时钟/逐字高亮不冻结，不添加坐标补偿。
- IMPLEMENTED：同一Core 0.5.1补丁在ESM/CJS各增加最近间奏布局快照，保留原索引修复；pnpm补丁hash随内容更新，没有依赖升级。适用原批准的局部依赖补丁范围。
- 测试沿用已确认的真实歌词触摸入口，增加7秒间奏的进入/退出、期间继续拖动、松手回位。尚待设备Green，不以普通跨句通过替代。

### beta.48 间奏修复设备结果
- VERIFIED：同一长间奏拖动入口，手机1/1通过45.419秒、平板1/1通过27.132秒；覆盖进入7秒间奏、退出间奏期间按住位置稳定、继续拖动35dp、松手回到当前播放句默认位置。beta.47原26dp跳动Red保留。
- 全Gradle Debug/Release/AndroidTest/Unit/Lint成功57秒，lint/typecheck通过；补丁版本仍Core0.5.1。
- beta.47完整手机组合23/24，唯一失败为心动入口点击前等待所有有限动画结束超时，未执行到模式断言；添加动画诊断，未改变等待条件。beta.48平板该入口通过3.651秒，手机完整24项进行中，尚不判定已修复。
- 平板原ARM64包3次冷启动均成功，但不足以关闭NS-045手机启动崩溃风险。

### beta.48 完整功能组合与平板回归
- VERIFIED：手机完整24/24通过412.623秒，保持所有原始断言，包括心动模式循环（此前beta.47动画等待超时的同入口）、普通/AMLL回位、TTML、首触标签/更多、横屏、批量、空态、日推、字体浮层、布局、音量、视频及迷你播放器。保留beta.47的23/24失败证据，不用本次Green消除历史不稳定记录。
- VERIFIED：当前包原平板横屏基线1/1通过16.707秒，受保护TabletDesktopLayoutTest文件未改（16156字节，原修改时间保持）。
- 当前TypeScript353/353通过9834ms；Android全构建57秒、lint/typecheck通过。测试包覆盖安装保留数据。
- beta.48 APK64439280字节，SHA256 2DB5A6F6BE9C51018013E8FBCC9B2194ED31845B52A4DBDD2508EEA586B4F1FA；v2签名通过，证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。
- 启动风险对照：beta.43与beta.48内置Node原生库、bridge、项目脚本共2868文件哈希完全相同（beta48-node-assets-comparison.json），未证明崩溃归属，仍保留NS-045临时P0门禁。不能把代码未变当作模拟器故障证明。
- 当前包拖动性能增量复测进行中；NS-006字体授权P1、NS-033短音源及完整状态覆盖仍开放。Release BLOCKED；无commit/push/CI/Tag/Release。

### beta.48 歌词专项最终采样与测试纠正（2026-10-19）
- VERIFIED：当前包100%文字，手机43.241秒、平板32.062秒整项通过；AMLL P95分别62.5/78.9ms，位移增益0.9996/0.9996，包含真实点击跳转和长按。
- VERIFIED：200%文字，手机39.764秒、平板31.108秒通过；默认歌词P95分别61.8/26.7ms、AMLL30.1/22.9ms；AMLL增益1.0001/1.0000，均无所记录长任务。逐字、模糊、用户弹簧保持开启，原100ms及位移阈值未变。
- 测试纠正记录：首个大字号准备在非UI线程读取WebView设置，测试中止，修正到主线程；第二次长按复用了点击前坐标，点击唤出控件后，该点已被歌词裁切区排除，真实touchstart命中page lyric-page。诊断截图/事件链保留；测试改为通过elementFromPoint重新找到可见歌词触点，无可命中点仍失败，没有修改产品交互。
- 用户确认只有MuMu；平板同版本x86_64原生包3次冷启动成功，API200；已覆盖恢复ARM64包。手机ARM64包3次冷启动均alive且API200。仅为有限样本，不消除先前libnode崩溃，NS-045仍待定位。
- NS-039中已复现的位置插值/跨句/间奏跳动和当前100/200%性能矩阵已有Green；此前反例全部保留，不扩推未经测量的设备/超长数据规模。
- 接续NS-033真实短音源诊断：同一后台下一首公共入口，新增只读Content-Range/Accept-Ranges与样本SHA256，不记录媒体URL/凭据，不修改播放逻辑。

## NS-046：音质结果被歌曲ID级旧缓存替换
| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-046 | SongManager/音质 | 新音质API已返回新URL，最终仍播放旧lossless URL却标成HQ | P1 | 公共useSongManager().getOnlineUrl两次取源：先lossless再exhigh，真实类执行Red；实际url=lossless、quality=HQ | getOnlineUrl重新按歌曲ID取本地URL，checkLocalCache忽略quality/md5 | 新API已成功时采用其规范化URL；由原生CacheDataSource按音频身份命中缓存，避免跨音质回退 | 已修复；真实SongManager行为回归及355项TS、设备控制组合通过 |

设计与风险：删除getOnlineUrl中错误的旧URL替换分支；保留Auto解锁的现有离线缓存路径，不改变缓存schema、不清理文件、不升级依赖。原生resolveCacheKey对音频文件名及去签名query稳定归一，保持同一媒体缓存命中；实际切换不同质量应更换媒体身份。测试沿用已确认的音质/音源结果链路，公共取源函数通过外部API/Native适配器准备两个URL，没有替换SongManager内部方法或源码字符串断言。修复后必须回归新URL、同源缓存、控制器竞态和设备标签入口。

NS-033新增证据：beta.48后台下一首/歌词同步入口1/1通过47.426秒，但独立上游仍Content-Range bytes 0-16008/16009、完整16009字节、2.612秒，SHA256 2388618f83cce8519c301d9ad64abfceb5f07167602308cfa7bd91608ea0bcc1；故同步Green不等于短音源已修复。公开无Cookie的exhigh与standard均业务404、无URL。尚未证明NS-046就是该短音频的来源，不合并归因。

### beta.49 音质缓存根因修复增量
- NS-046公共真实SongManager行为回归Red：lossless→exhigh后quality=HQ但url仍lossless；去除按歌曲ID覆盖新API结果后Green。新增Auto解锁继续复用最新已完整缓存URL的兼容回归；相关17项及完整TypeScript355/355通过。
- 不改缓存schema、不清除用户文件，不升级依赖；实际新URL由既有原生缓存按媒体身份处理。根因修复位于SongManager共享链路，没有在标签组件伪造状态。
- beta.49 Web构建、lint/typecheck、Android Debug/Release/AndroidTest/Unit/Lint全套46秒成功。两台MuMu覆盖安装ARM64隔离包，保留数据；原平板基线通过18.001秒，手机7项控制/标签/菜单组合进行中。
- 静态补充风险：原生resolveCacheKey先按音频文件名返回，忽略同文件名的host/path/有意义query区别。真实字节串用尚UNCONFIRMED；已准备独立HTTP→CacheDataSource字节回归方案，等待用户确认新增测试入口，不先改缓存键或清理缓存。
- NS-033与NS-045仍未关闭；通知字体授权P1仍未解决；没有commit/push/CI/Tag/Release。

### beta.49 当前验证与待确认入口
- VERIFIED：手机7/7控制/标签/TTML/心动/横屏/批量入口通过76.706秒，原平板基线1/1通过18.001秒；355/355 TypeScript、68/68 Android Unit、Android Lint 0错误/27警告、lint/typecheck与完整构建通过。
- 二次增量复核：读取SongManager差异及所有checkLocalCache调用，API成功后的新音质URL不再被ID级旧URL覆盖；Auto解锁缓存入口保留，两项公共行为回归分别验证新URL和缓存兼容。没有修改显式音源选择逻辑、缓存schema或删除数据。此为同一代理的新一轮复核，不能冒充独立人工审查。
- beta.49 ARM64隔离APK64439160字节，SHA256 0599F6C47C55D080775278EEBCF142E544BB6E4368533D2157542396B796BF1A，v2签名及原测试证书通过，已保留在两台MuMu。
- 新增原生缓存字节测试入口仍等待用户确认；tdd要求Test only at pre-agreed seams，在收到答复前没有编写该新测试、没有修改原生缓存键。现有可独立验证的音质修复已完成上述检查。
- 当前HEAD仍9db84b14b28148c045bdd91c6c6061fd4122f433；没有stage/commit/push/CI/Tag/Release；.agents、skills-lock及原用户TabletDesktopLayoutTest保持。
- Release BLOCKED：字体授权P1、短音源NS-033、启动崩溃NS-045尚未关闭，仍未到最终人工验收发布门禁。

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-047 | 原生音频缓存身份 | 同名文件的不同歌曲query、服务器、音质曾串用缓存字节 | P1（已修复，有限矩阵VERIFIED） | 旧beta.49六项实际失败；beta.50手机与平板六项实际通过 | 文件名优先键忽略完整资源身份 | v2完整URI身份；限定可信内容哈希复用；保留显式DataSpec.key；旧文件不删除但不猜测归属 | 已修复；30项手机组合及平板原基线通过，见最新证据 |

## NS-047 cache identity v2 design after real Red

- Confirmed Red: real HTTP id=2 returned id=1 cached bytes; first differing WAV byte 44, expected -82, actual 87. Test-owned random filenames avoid user cache collisions.
- General URLs: retain the complete URI resource identity, including authority/port, encoded path and query; remove fragment only. Do not guess that an unknown server token is only an expiry signature.
- Preserve explicit DataSpec.key as Media3 1.8.0 CacheKeyFactory.DEFAULT does: http[本地路径已省略]
- Restricted optimization: trusted music.126.net CDN hosts with a terminal 32-hex content filename may share a provider-scoped identity across changing CDN/signature parameters. Other hosts cannot create this namespace. Keep business query values, duplicates and encoding distinct.
- Compatibility: the old index stores only key/time/promoted, not original URI ownership. Do not guess ownership or clear caches. v2 keys leave old files/index readable but do not serve ambiguous legacy audio entries; the first request may require a new download. Existing downloaded music, databases, cover/lyric caches and normal LRU rules remain untouched. This is a disclosed cache miss strategy, not a claim of byte migration.
- Regressions: first finish query-identity Red→Green, then different servers/quality paths, explicit same-resource signature reuse, provider-content identity contract and legacy-byte nonreuse. No source UI, license/signing/dependency/version defaults change.

## NS-047真实复现与beta.50局部修复
- 用户已明确同意真实HTTP→原生缓存→切换资源入口。旧beta.49手机6/6 Red，首个曲目query复现第二首得到第一首的真实WAV字节（offset44预期-82、实际87）。此前UNCONFIRMED真实后果现已确认，仍未把旧2.612秒上游文件归因到该碰撞。
- beta.50手机6/6 Green 0.108秒，平板6/6 Green 0.094秒：不同歌曲query、服务器端口、质量路径、opaque token保持不同字节；显式稳定资源键可在URL轮换后无需重复HTTP请求；旧索引下的测试自有字节仍可在关闭服务器后读取，但不被猜测为另一首；可信网易内容哈希保留签名更新复用、隔离其它发布者与业务参数。
- 实现局限于AudioCacheProvider缓存键与现有DataSpec.key契约。普通URL保留完整authority/端口/编码path/query；仅可信网易CDN终端32位内容哈希使用受限命名空间；业务query编码并保留重复值。预载原本setKey(resolveCacheKey(uri))，与新工厂一致。
- 兼容策略：旧索引只有key/time/promoted，不能证明原始URL归属。采用v2命名空间，保留旧文件和旧索引，首次音频读取可能重新获取。未删除用户下载、数据库、封面或歌词缓存；现有LRU规则未改。不能宣称旧音频离线缓存已自动迁移。
- 对普通未知地址不再把token一概当临时签名剔除；只有显式稳定身份或可信网易内容哈希才复用签名轮换后的缓存。
- beta.50完整Debug/Release/AndroidTest/Unit/Lint构建进行中；新缓存核心还需真实后台在线切歌与平板/播放器组合回归。没有Git发布操作。

## beta.50最终增量验证（2026-10-19）
- VERIFIED：手机30/30组合通过304.45秒；最终测试夹具重编译后，手机缓存6/6通过0.109秒，平板缓存6项加原TabletDesktopLayoutTest基线共7/7通过16.533秒。未修改原基线测试，没有降低断言。
- VERIFIED：完整Debug/Release/AndroidTest/Unit/Lint构建12秒成功；最后仅测试夹具的错误传播修订重编译3秒成功。首次夹具命令因PowerShell未引用预览版本参数被拆分而失败，已保留日志；引用参数后成功，未修改正式版本来绕过门禁。
- VERIFIED：beta.50真实平板后台下一首/歌词同步通过56.541秒；目标资源仍为16009字节/2612ms，故NS-033没有关闭。缓存串用修复不得冒称已解决上游短资源。
- APK：ARM64隔离包64439156字节，SHA256 4FC011DE0D1CFEC28D5CA2225BF56938C445CE8CDD0300A5F42109099951A23D；v2签名验证通过，证书SHA256 607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。两台MuMu均保留安装；无真实ARM64手机验证。
- 二次增量复核：重新读取缓存键差异、DataSpec和预载调用、旧TTL索引及真实HTTP夹具。普通URI保留端口/编码query；可信内容哈希的优化限定发布者且保留业务query；显式key保持Media3契约。未发现本增量另一个有证据的新缺陷。此为同一代理复核，不冒充独立人工审查。
- 兼容限制：旧音频缓存文件仍保留，不能安全证明归属的旧键不会自动复用，首次读取可能需要联网重新获取。未知签名地址没有显式稳定key时，签名变化可能新增缓存项；现有LRU规则不变。
- Release仍BLOCKED：NS-045一次libnode启动崩溃临时P0（根因UNCONFIRMED）、NS-033短音源P1、通知字体授权P1。成功重跑不能消除历史崩溃；Beta.6字体风险授权不延伸到本阶段。
- Git：dev，HEAD 9db84b14b28148c045bdd91c6c6061fd4122f433；未暂存、未commit/push/CI/Tag/Release。diff --check未报空白错误；现有换行格式提示未用全仓格式化处理。用户TabletDesktopLayoutTest、.agents、skills-lock保持。
- 证据文件：beta50-functional-phone.log、beta50-cache-final-phone.log、beta50-cache-tablet-baseline-final.log、beta50-full-gradle.log、beta50-test-harness-build-quoted.log、beta50-background-source-diagnostic.json、splayer-beta50-arm64-debug.apk，均位于本次会话外部证据目录。

## beta.51局部设计与Red证据（待验证）
- 用户最新确认：锁定入口只在正文正左侧6dp、垂直居中；左侧不足隐藏且不触摸，歌词不缩不移。新增Android设置“解锁桌面歌词”调用现有原生公开解锁入口；不恢复播放器More解锁项。覆盖原上下回退方案。旧beta.50全宽真实窗口断言Red：预期INVISIBLE(4)，实际VISIBLE(0)，18.852秒。沿用已确认原生锁定/解锁测试入口。
- 迷你播放器封面：旧beta.50缩略图错误、原图可用的真实WebView图片回归Red，13.44秒。按解析后小封面作为过渡key，失败回退原封面；加载占位图显式可见，避免继承真实图片opacity:0。网络不可用不能保证即时取得未缓存图片。
- 迷你播放器高度：旧beta.50实际播放/暂停、切歌与slide歌词切换Red，100%高度64→71.796875。手机歌词/歌手过渡采用同一Grid单元重叠，保留自然文字高度；手机切歌保留info容器，避免out-in期间信息区清空；平板桌面info切换保持原行为。100/200%与fade/slide各自测量全帧高度，不把大字号本身合理增高判为跳变。
- 变更仅FloatingLyricService、歌词设置、MainPlayer及已确认入口的设备回归。无需依赖、正式版本、签名或工作流变更。

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-048 | 底部迷你播放器封面 | 小封面失败、原图可用时仍空白 | P2 | beta.50真实WebView图片加载入口13.44秒Red；beta.51同入口Green | 仅请求小封面，未提供原封面回退；占位图继承opacity:0 | 原图作为失败回退，解析后小封面URL作为过渡身份，占位图可见 | beta.51手机/平板已验证；不能保证网络不可用时获取远程图片 |
| NS-049 | 底部迷你播放器高度 | 播放/暂停、切歌及slide歌词切换令高度64→71.796875 | P2 | beta.50逐帧几何Red；beta.51手机/平板100/200%和fade/slide Green | 自动高度布局中新旧过渡内容同时占流；整块info出入时可能清空 | 手机副标题Grid同一单元叠放，切歌保留info容器，大字号自然增高仍允许 | 已修复；手机31/31项组合已通过326.358秒，平板原布局基线已通过 |

## beta.51当前验证
- 本轮仅锁入口/设置解锁、迷你播放器封面及高度；启动崩溃与字体证据按用户要求暂不排查，未记为解决或发布授权。
- 手机定向2/2通过50.835秒，平板定向两项加原TabletDesktopLayoutTest共3/3通过50.505秒。左侧不足不改歌词也不退到上下方；设置真实触摸解锁成功。
- Android完整构建40秒成功，Unit68/68，Lint0错误/27警告；Web构建、lint、typecheck、TS355/355通过。测试包beta.51/30074已原位覆盖两台MuMu，无清数据。手机31/31项功能组合已通过326.358秒。
- APK64439436字节；SHA256 7EED94C7E275314DB6FC4BFD67891705967E95D6D2EC183CD779C985EE1F35D4；v2及原隔离证书通过。正式版本、签名与发布流程未改。

### beta.51本轮完成状态
VERIFIED：手机完整31/31通过326.358秒；平板定向及原布局基线3/3通过50.505秒。构建/单元测试/代码规范/类型/Lint及签名检查通过。锁图标位置、设置解锁、底栏封面回退和高度修复均纳入本轮真实渲染回归；未修改字体或追查启动崩溃。剩余历史风险仍按原记录保留。dev HEAD仍9db84b14，未暂存/提交/推送/CI/Release。

## beta.52在途修复与执行阻塞（尚无新主APK）
- 用户新增：FM长文本省略且不撑高、底栏封面缓存加载、官方在线切歌延迟、关闭桌面歌词自动解锁。沿用既有入口；用户另已明确同意原生封面缓存到真实底栏的回归入口。
- 已复现：beta.51手机320dp/100%下FM短文本135.729dp、长文本260.583dp；关闭已锁定歌词后重开仍出现锁控制窗。两项旧包回归失败已保留，未被当作修复后结果。
- IMPLEMENTED：Compact FM名称/歌手改单行省略；明确关闭时清除持久锁并解除服务当前锁定。原生生命周期及布局尚未重新构建或验证。
- VERIFIED（仅公共取源行为测试）：较高优先级源成功后仍被低优先级慢源阻塞；普通lossless取源被独立质量元数据请求阻塞。两条Red已复现；局部修复后7/7通过，包含不抢占优先源、lossless失败降级exhigh、杜比兼容检查。不能据此宣称真实官方网络或原生首声耗时已改善，尚需设备测量与全套检查。
- 未实现：本轮底栏原生封面缓存读取及小封面预热修复。新缓存设备测试也尚未写入。
- BLOCKED：包含新封面测试写入、Gradle编译、MuMu安装/回归的下一条操作，因自动审批执行额度不足未执行；系统明确这是审批检查失败，不是安全否决，禁止绕过。当前设备主包仍beta.51，不能声称beta.52已安装或本轮全部修复通过。
- 当前保留未提交修改；HEAD不变，未stage/commit/push/CI/Tag/Release。需执行额度恢复后继续封面Red、实现、构建、设备回归及二次审查。

## beta.52局部方案及Red→实施记录
- 2026-10-19继续后审批恢复，已连接两台MuMu，保留既有数据。当前正式版本不变，预览beta.52/30075。
- FM：Compact名称/歌手单行ellipsis，长度不增加行数；字号缩放仍允许自然高度变化，按钮48dp不缩。旧320dp/100%短135.729dp、长260.583dp已复现。用户明确允许长文本省略；未将省略策略扩散到其他页面。
- 封面：底栏原n-image没有读取covers原生缓存；原生缓存已有测试PNG、远程域名不可用时旧包10秒内没有显示blob，真实Red。改为现有s-image、关闭可见性等待和lazy，读取covers；可选fallbackSrc保留原封面回退，其他调用者默认行为不变。下一首同时预热小图和主封面，不等待该操作。测试缓存键格式只用于准备唯一测试图片，最终断言为真实解码、可见和延迟，不检查源码字符串。
- 取源：普通无损先等待额外质量元数据、以及高优先级成功仍等待低优先级慢源均有公共行为Red。普通音质直接按URL接口的实际level和既有降级请求处理，杜比/AI保留元数据兼容；多源并发启动并立即接住错误，按优先级消费，在第一个有效优先结果返回，不让低优先级快响应抢占。没有降低网络超时或伪造播放成功。
- 关闭歌词：显式hide清理持久锁，运行中服务同步解锁后停止；系统进程重建不等于用户关闭，不在onDestroy无条件擦除锁。旧包关闭重开仍锁定的设备Red已保留。
- Web/lint/完整361项测试、完整Android构建57秒通过。新增测试Promise.withResolvers不符合当前TS目标，已改兼容辅助函数，类型检查及8项取源回归通过；未改tsconfig/降低标准。设备验证进行中，实际官方网络首声时间尚不能宣称改善。

## beta.52/53证据纠正与增量复核
- 封面缓存初次测试写入键漏了文件名末32字符规则，导致旧包及新包均读不到测试图片。这两次Red属于夹具错误，不能用来证明产品缓存行为；原日志保留。按规则修正后，beta.52手机203ms、平板135ms实际从原生缓存blob解码显示，在不可用图片域名下通过。旧底栏n-image不调用covers缓存的根因属于代码可确认事实，不能冒充有效旧缓存设备Red。
- FM长文本等高检查通过后，旧“main-rec后代必须完整换行”仍间接选中FM，违反用户最新省略要求。只排除该FM后代，并新增FM的nowrap/ellipsis/hidden及安全边界断言；按钮48dp、其它页面完整文字检查保留。beta.52手机最终完整Home入口1/1通过37.679秒，320/360/390/412dp×100/200%文字。
- beta.52真实在线切歌/桌面歌词入口通过，时序捕捉到官方URL请求每次约1.57–1.65秒、官方失败后第三方回退。因此不能把该样本当作纯官方播放成功，也不能宣称网络延迟已消失。
- 真实时序还显示预载与切歌重叠取源。公共getOnlineUrl并发同资源Red实际请求2次/应1次；在途Map按歌曲ID、音质、PC标记、AI禁用状态及账号会话隔离；成功失败都清理，只复用未完成请求，不长期缓存已完成URL。不同质量与已完成后重新请求、账号切换均有回归。账号隔离在复核中补充Red→Green，最终专项11/11通过。
- beta.53为本轮最终候选，当前完整构建和设备回归尚在进行，不将beta.52部分通过写成beta.53验收通过。

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-050 | 首页FM | 长歌名/歌手撑高卡片 | P2 | 320dp短135.729、长260.583dp；等高Red | Compact显式normal换行 | 用户新要求单行省略，保持触摸目标及字缩放 | VERIFIED：beta.53手机完整34项及宽度/字号矩阵通过；beta.54布局未改 |
| NS-051 | 底栏封面 | 已有原生封面缓存仍只能联网加载 | P2 | 旧代码无缓存入口；有效旧包设备Red未取得（夹具格式错误）；beta.52当前功能Green | n-image绕过covers，预热只覆盖大图 | 复用s-image covers并预热实际小图URL | beta.52离线域名缓存显示手机203ms/平板135ms通过；beta.53当前完整矩阵通过，beta.54图片实现未改 |
| NS-052 | 取源性能 | 元数据串行等待、无关低优先慢源等待、在途同源重复请求 | P2 | 三类公共行为Red，11项最终专项Green；实际混合源仍有网络等待 | 普通取源前额外请求、allSettled等待全部、无在途共享 | 普通音质直接取源；保留优先序及专用音质检查；严格身份隔离在途请求 | 已实现；纯官方网络实际延迟改善尚未量化 |
| NS-053 | 桌面歌词 | 关闭后重开仍锁定 | P2 | beta.51真实关闭/重开Red，beta.52锁定完整入口通过 | 显式关闭未重置持久锁 | 显式hide清理持久锁和服务锁；非系统销毁一概清除 | VERIFIED：beta.53手机完整组合及平板单项通过；平板组合display异常另记NS-054 |

## beta.53已验证结果与beta.54最终取源增量
- beta.53手机34/34组合通过364.46秒，包含首页完整320/360/390/412dp×100/200%文字矩阵、FM长短文本等高/单行省略、封面缓存、锁关闭重开、音量、歌词/菜单/批量及音频缓存。
- beta.53平板组合6/8通过：首页导航“我喜欢的音乐”未跳到预期路由；后续锁测试遇到失效显示器。FM几何断言、迷你播放器4项、原布局基线和真实在线切歌通过。保留完整失败，未改断言掩盖。重开隔离包到默认显示器后，原锁生命周期及原平板基线2/2通过31.342秒；两项组合异常根因仍UNCONFIRMED，不能以单项重跑全部关闭。
- 原生缓存有效测量beta.52手机203ms/平板135ms，beta.53平板316ms；均为已缓存图、不可用图片域名、实际解码可见。冷缓存仍受图片网络影响。FM截图beta53-fm-tablet-320-100.png及200%图已查看，100%可见单行省略；200%保留字体缩放并可滚动访问操作。
- 真实混合源时序beta.52→53的URL接口调用从6到4次，但本次切换ready/playing约8.85秒→10.308秒，网络波动及第三方回退仍存在，不能宣称整体速度提升。发现第三方回退也重复请求后，公共入口Red（2次/应1次）再修复。
- beta.54复用同一在途Promise管理处理官方和第三方请求；键有官方/解锁命名空间、歌曲/音质配置/账号会话，解锁另包含元数据关键词、启用源顺序、明确指定源。不同明确音源不合并，不把较低优先级先完成者选为首选。完成/失败立即移除，不长期记住过期URL。13项公共真实SongManager行为回归通过。
- beta.54 Web、lint/typecheck、完整366项测试、Android全套33秒通过；Unit68/68及Lint0错误/27既有警告。APK64439540字节，SHA256 30F93CD413A32590D0C3B940FAFFE6B3794538E75A3D779616399C69CA34B510；v2原测试签名通过，已原位安装两台MuMu。
- 新增取源增量已重新审查：共享Map仅在途、身份/账号/源隔离、双分支清理；优先级及特殊音质检查保留；无另一个有证据的新待修问题。本次仍为同一代理复核，非独立人工审查。最终设备增量回归进行中。
- 纯官方性能验收仍需一首官方接口可完整播放的有效样本；现有真实两曲的官方URL不可用并回退，不能替代该验收。用户已被询问提供名称/ID，不请求Cookie或密码。

## beta.54当前交付状态（2026-10-19）
- VERIFIED：FM长短文本不增高、按最新要求单行省略；底栏复用原生covers及小图预热；明确关闭桌面歌词清除锁定。beta.53手机完整34/34通过364.46秒，布局/原生实现到beta.54未改。
- VERIFIED：官方及第三方在途请求共享、额外元数据/低优先慢源等待修复，13项公共取源行为通过。beta.54最终增量手机8/8通过92.994秒，平板真实在线及原布局基线2/2通过47.238秒。完整366项测试、Web构建、lint/typecheck、Android全套33秒、Unit68/68、Lint0错误/27既有警告及APK签名均通过。
- IMPLEMENTED/待性能验收：切歌延迟整体改善。beta.54真实混合源样本仍约8748ms到native ready+playing，不能把单元去重或API调用减少当作全部速度问题已解决；纯官方有效样本仍待提供。未调低网络超时、未伪造播放或加载完成。
- BLOCKED/待定位风险：beta.53平板组合曾2项失败，导航未进入喜欢页和锁窗口display失效。隔离重开后锁/基线2/2通过、beta.54基线通过，不足以消除组合异常；原始失败日志和未知根因保留。
- APK：splayer-beta54-arm64-debug.apk，64439540字节；SHA256 30F93CD413A32590D0C3B940FAFFE6B3794538E75A3D779616399C69CA34B510。v2签名有效，证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c；两台MuMu均保留安装，正式版本未改。
- Git：dev / 9db84b14b28148c045bdd91c6c6061fd4122f433；工作区81项含既有修改和用户文件，未暂存、commit、push、CI、Tag、Release。原TabletDesktopLayoutTest长度16156、最后修改2026-10-13 19:19:23保持；.agents/skills-lock保留未提交；无README/LICENSE/工作流/正式版本/签名新改动。

| NS-054 | 平板组合巡检 | 首页喜欢入口触摸未导航，后续锁窗口显示器失效 | P2风险（未归属） | beta.53组合2项失败；重开默认display后锁/基线2项通过 | UNCONFIRMED，不能断言模拟器单方原因或产品已修复 | 保留失败、用稳定显示器和有效登录进一步隔离；不以单项Green抹去 | 尚未关闭；无证据不扩大修改 |

## beta.55 新增复现与局部修复（验证中）
| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-055 | 手机播放器 | 信息区触摸小幅横拖使整页侧移并裁切 | P1 | beta.54真实按住拖动32dp，left从16到-16，截图及失败日志已保存 | info-group没有排除整页横向翻页手势 | 信息区保留纵向浏览与按钮操作，排除页面翻页；分页仍可进入歌词 | IMPLEMENTED，待beta.55设备回归 |
| NS-056 | 冷封面 | 后台图片字节已返回，底栏仍等待重复的慢图片请求 | P2 | beta.54受控有效PNG下载完成后1.2秒仍占位，images=1；测试自身主线程错误已修正后才获得有效Red | 冷缓存下载不向当前useCoverCache返回可显示字节 | 下载完成交付blob，异步写盘；旧歌曲和卸载后的结果释放引用，原图已可见时不重复淡入 | IMPLEMENTED，待beta.55设备回归；不等于所有远端图片无网络延迟 |
| NS-057 | 下一首预载 | 较远歌曲预载取消真正下一首 | P1 | beta.54真实队列+HTTP，收到超过2MB后关闭服务器，下一首512KB无法从生产DataSource读出；有效Red已保存 | 连续prefetchUrl每次取消上一项；异步远曲回调亦可抢占 | 按队列紧邻下一首预载，不跨过未取源曲目；取源回调切回主线程重选当前队列 | IMPLEMENTED，待beta.55设备回归 |
- 用户已明确同意真实音频自然结束到下一首播放事件、快速连续选择及预载不串歌回归。沿用已确认封面和触摸入口，无新增未授权测试入口。
- beta.54受控真实HTTP WAV自然结束回归1/1通过14.108秒；事件间隔另存natural-http-transition.json。事件间隔不是声学采样间隙，不能据此承诺零间隙。
- 测试开发中的WebView非主线程调用、HTTP空请求、JSONObject检查异常均为测试自身错误，不计作产品Red；资源准备与Gradle重叠导致一次libnode暂时缺失，改为顺序执行后通过，不归属启动崩溃。

| NS-058 | 当前歌曲封面 | 未打开播放页时不准备大封面，打开后额外等待网络 | P2 | beta.55隔离真实渲染入口，选中新歌1.5秒内大图请求为0，3.736秒有效Red | 原prefetchCover仅从下一首预载调用，当前歌曲只有底栏小图启动 | 当前歌曲UI初始化复用公开封面准备方法；Android仅预热实际s/l，不额外下载xl和原图 | IMPLEMENTED，待beta.56验证 |

## beta.56 回归新增观察（尚未关闭）
- 手机最终组合16/16通过175.179秒；平板组合8/10通过，2项失败：快速选择事件记录非最终歌曲标识；后台歌词overlayShown=false/display35。不得用beta.55或隔离Green替代此失败。
- 已增加快速选择失败前持久记录及受控资源文件名，区分真音频回切与元数据/事件滞后；未删原断言，额外要求资源final.wav。初次56失败未包含资源字段，根因UNCONFIRMED，不能认定实际串歌。
- 默认display0重开后后台真实在线预备切歌1/1通过42.664秒，仍不关闭NS-054组合显示器风险。
- 缓存最终音频的手机快速选择1/1通过14.473秒；平板队列/自然结束/快速选择组合压力诊断继续。

| NS-059 | 快速切歌/MediaSession | 缓存音频已播放，但事件携带上一首歌曲标识 | P1 | beta.56平板三轮组合3/3复现：source始终final.wav，最初id991560，20–50ms后才到991562；未确认实际音频串歌 | loadAndPlay先加载音频，afterPlaySetup后置提交metadata；缓存资源可在IPC前就绪 | 原生加载前同步所选歌曲metadata，检查请求失效并保留等待中的暂停/seek；移除后置重复提交；旧metadata回调失效 | IMPLEMENTED，待beta.57真实回归 |
- NS-059对应两个公共控制器行为Red：元数据待完成时已加载旧音频；局部修复后19项控制器回归通过，包括等待中暂停/seek与旧任务晚完成。

- NS-055补充：beta.57从标题触摸已稳定，但从SVG图标内部拖动仍left16→-16，真实Red已保留。根因为isNoPageSwipeTarget仅判断HTMLElement，SVG不属于该类型；局部改用Element.closest覆盖HTML与SVG。beta.58待回归。

- NS-059原子性补充：beta.58更严格真实事件回归仍Red，暂停/加载事件出现id991562+slow.wav、id991560+final.wav；不以只核对playing的Green掩盖。beta.59对所选metadata附带forSource，原生仅暂存并在匹配load中与资源原子应用，错误/不匹配load清理暂存，cleanup也释放。立即更新当前资源的旧API语义保留。待完整真实事件回归。
- beta.58 Phone16/16通过178.194秒、Tablet9/9通过46.835秒；另一次真实后台回归在进入后台前因目标在线音频URL尚未进入队列失败，不能计作歌词同步已通过，也不能仅据此认定后台歌词回归；保留失败。

## beta.59 实际回归与组合风险（2026-10-19）
- NS-059：平板三轮真实HTTP组合各3/3通过（16.411、12.284、12.154秒），严格核对所有受控资源状态事件；手机单独快速切歌1/1通过8.162秒。beta.58仅核对playing的早期Green并不充分，原子暂存修订通过的是更强断言。
- 平板完整相关回归9/9通过49.122秒；真实在线预备下一首、进入后台、桌面歌词更新1/1通过50.831秒。后者不消除NS-054显示器组合风险或NS-033上游短资源。
- 手机首轮切歌组合2/3通过，失败发生在歌词测试初始化；随后完整组合10/16通过，失败涉及自然结束、冷封面播放页一致性、动画等待、菜单、TTML及模式按钮。事件记录出现测试列表之外的真实歌曲。保留原日志，不将其直接判为产品根因或模拟器单方错误。
- 从新进程单独自然结束1/1通过7.956秒；新进程完整组合另行记录在UI-VALIDATION.md。隔离通过不能抹去前述失败。

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-060 | 手机连续组合验证 | 已恢复真实歌曲状态干扰受控歌曲/封面，另有动画与控件等待失败 | P1风险（待确认，非已确认产品Bug） | beta.59完整组合6项失败，新进程16/16通过204.471秒；UNCONFIRMED | 尚未区分Activity/异步恢复、测试夹具和实际播放链路 | 保留失败与事件，进一步隔离恢复边界，禁止降低断言或宣称全量稳定 | 未关闭；本轮不凭推测扩大业务修改 |

- NS-055/056/057/058/059：beta.59新进程手机16/16、平板9/9实际通过；仅在已定义场景记VERIFIED，不扩大到NS-060连续恢复稳定性、任意在线网络速度或全阶段发布通过。

## beta.60 用户验收前修正（2026-10-19）

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-061 | 歌词请求/UI | 挂起的TTML或其他请求使加载状态长时间不退出；自动模式TTML已就绪仍等官方 | P1 | 旧版真实TTML优先挂起10秒不取消/回退；总请求截止及自动TTML先到两项单元Red | 直连fetch无超时、handleLyric无整体截止；自动模式仅提前发布官方结果 | 6秒Abort TTML并回退；12秒UI截止保留已有内容；两方就绪均可提前发布；旧请求不得清除新状态 | VERIFIED于双端核心2/2；全量相关回归另见UI-VALIDATION.md |
| NS-062 | 信息区手势 | 上轮阻止信息区翻页，不符合用户要求 | P1 | beta.59真实标题拖动32px整页不跟随，回归Red | 把整块info-group设为翻页排除区，错误理解“不可独立位移” | 恢复整页横滑；禁止info局部横滚；固定单次手势方向；比较信息与封面相对位置，实际滑到歌词 | VERIFIED于手机2/2（45.011秒）/平板2/2（18.446秒）核心回归 |
- NS-055此前“禁止从信息区翻页”的方案及绝对left不变断言已被本次用户澄清替代；保留历史证据，不再把该行为称为正确验收目标。
- 保留原有0.3页边缘阻尼：32px拖动的边缘期望是9.6px，测试改为精确核对该既有契约而非统一要求>=16px；整页相对位置、scrollLeft=0、真实160px翻页断言同时保留，不删除失败用例。
- 初次测试包安装发生在编译完成前，0测试结果不计验证；等待编译完成后重装得到有效2项Red。沙箱包管理器路径拒绝访问改为正常授权环境执行，未降低lint/typecheck标准。

- beta.60最终相关回归：手机17/17、平板11/11、真实后台歌词1/1，AMLL双端各1/1通过；NS-061/062在这些场景VERIFIED，历史NS-054/060及发布门禁不自动关闭。完整证据见UI-VALIDATION.md。

## 发布前复查补充

公开版本收敛为 v3.0.16-beta.7 / 30030。手机本轮组合出现自然衔接中断并恢复原账号歌曲，独立复测通过；此项保留在 NS-060 的 UNCONFIRMED 恢复边界风险，不能以独立成功覆盖。维护者已明确接受本次 Beta 的历史组合风险；CI 与人工审核仍为独立门禁。

## 发布回归追加发现（2026-10-20）

| ID | 模块 | 问题 | 严重程度 | 复现情况 | 根因 | 建议 | 是否本阶段修复 |
|---|---|---|---|---|---|---|---|
| NS-063 | 手机布局/触摸 | 透明下拉捕获层覆盖更多按钮，按钮可见但真实触摸不可达 | P1 | 已复现：MuMu 平板强制手机竖屏 618dp，完整组合 TTML 用例命中 drag-handle；15/16，截图保留。独立原用例又通过，说明有时序边界 | 两个变换后的视口读数被用于绝对定位捕获区；异步采样/进场动画不同步为高可信推断，具体时序 UNCONFIRMED | 使用已有未变换布局流高度形成捕获边界，真实检查菜单、横滑和下拉关闭 | VERIFIED 于手机15/15、平板14/14；双端TTML组合通过但手机补截图单项曾失败，诊断两次再通过，状态风险未关闭；捕获边界、菜单、横滑和下拉关闭均通过，最终CI仍待复验 |
| NS-064 | 远程性能验收 | 软件渲染模拟器快速切歌回调 1727/2699ms，超过原1500ms门槛 | P1 验收失败，非已确认音频Bug | 963bbf4d 远程小屏成功，手机/平板各仅此项失败 | 实际原生开播慢或 JS 回调积压仍 UNCONFIRMED；默认两虚核及界面负载可能影响结果 | 同时记录原生首播与JS回调，保留原JS门槛并增加同值原生门槛；四虚核作资源假设验证 | 未关闭，不能忽略失败、豁免CI或称已解决 |

本机完整手机17/17通过；平板一次被外部 STOP APP 中断（系统退出记录 USER REQUESTED，不能当作应用崩溃），重跑15/16的唯一失败为NS-063。所有原始结果保留。

TTML补截图单项手机出现一次开关已开而仍为50行普通歌词；平板成功，手机追加状态诊断后两次成功。具体状态覆盖时序UNCONFIRMED，保留失败；不能把局部绿色称作完整发布通过。

## 通知重复刷新候选（2026-10-20）

- c1212503远程CI成功，完整UI失败：手机15/17、小屏16/17、平板15/16。快速切歌原生/JS分别1811/2096、1384/1541、1460/1755ms。手机另有菜单第二次点击未收到click；此前“只有性能失败”的中间判断不完整，以完整结果为准。
- 原生日志实证通知频繁更新被系统限流、主线程跳过60帧。重复通知IPC影响时延为高可信推断，不能仅凭相关性宣布根因完全确认。四虚核未解决门禁，不继续堆资源或改限值。
- IMPLEMENTED候选：首次通知即时提交，后续200ms合并最新状态，清理/服务脱离时取消待提交任务；播放事件、MediaSession和歌词同步不节流。保留真实切歌与服务恢复入口验证。
- 菜单第二次点击测试未等待入场动画；共享触摸辅助方法补齐现有动画结束等待，断言不删，实际阻塞是否仅为时序仍待重验。

Android官方说明频繁更新同一通知可能被丢弃（https://developer.android.com/develop/ui/compose/notifications/create-notification）。此规则与本次系统限流日志一致，但不单独证明切歌延迟根因；最终看同门槛远程回归。

候选本机验证：平板完整16/16（413.977秒），手机局部6/6；手机完整组合前8项通过后整台MuMu手机实例退出，退出原因UNCONFIRMED，完整手机组合BLOCKED。不能以局部结果替代完整组合，也不能未经证据定为应用崩溃。

## UI Test 分段根因与候选（2026-10-20）

- 744e71b6远程手机17/17通过，小屏15/17、平板15/16；1397064a诊断运行手机16/17、平板15/16、小屏15/17。两轮失败均保留。诊断提交cad19b4f曾因新增脚本Lint失败，1397064a修正后CI通过，不能隐去该记录。
- NS-064分段证据：1397064a平板原生/JS1669/1840ms、手机1634/1846ms。最终选择后，stop/setVolume/setRate/updateMetadata回包集中等待约1200–1300ms，load直到其后才发出，随后约210ms返回。状态存储写入0–7ms，长任务53–104ms；旧日志墙上时间与单调时间差波动9ms，不足以解释秒级超限。
- 已证实的可避免依赖：PlayerController等待单独元数据ACK后才发送load。ACK传输/主线程排队为何持续约1.2秒的底层机制仍UNCONFIRMED，不把“单次往返耗时”直接等同于某个Java方法执行时间。
- IMPLEMENTED：普通Android原生加载携带PlaybackMetadata，以一次IPC在PlaybackManager同一锁内应用身份并load；不移除元数据、不提前用旧身份开播。API上下文同步、过期请求、暂停/seek修订及crossfade原路径保留。两条新行为回归先失败后通过，375应用测试、Lint与类型检查通过；设备/远程结果另验。
- NS-065（P2测试夹具）：校验后再次取触摸点曾读到null，改单次有效取样后139运行无JSONException。小屏仍有未收到click；650ms主线程排队注入未复现该项，已移除实验。补绘制就绪屏障及失败输入轨迹，不声称该项根因已全部确认，保留原点击/翻页断言。

- 原子加载候选本地完整手机17/17、平板16/16，额外320dp触摸/切歌2/2通过；原生/JS平板110/110ms，原1500ms限值不变。远程139失败仍保留，最终CI待新提交。
- 第二轮复审发现NS-066（P1，候选内已修复）：crossfade等待API上下文期间切到新曲，旧请求会在后置令牌守卫前提交新曲身份/旧URL。公共控制器回归实证先失败（songId=2/source=obsolete-crossfade）后通过；最小修复为等待后、任何身份构建/提交前校验令牌，不改crossfade原行为。376应用回归通过。

最终原子加载守卫候选的应用376项、Android68项、保护脚本75项、Lint/typecheck和app构建通过。平板相关8/8通过；双计时手机94/107ms、平板82/82ms。最终远程门禁尚未运行，NS-064不能仅凭MuMu结果宣称远程已关闭。
