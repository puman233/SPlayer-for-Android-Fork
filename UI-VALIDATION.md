> 当前发布状态（2026-10-19）：维护者已验收并明确授权本次 v3.0.16-beta.7，且仅接受本次 Beta 的字体授权证据不足、上游短音频和未确认的历史启动/组合失败风险。问题状态保持开放；历史 BLOCKED 记录保留其当时含义。CI、原签名、人工 Environment 审核和最终资产校验仍必须通过。截图和原始设备日志保留在外部验收材料，不随源码提交。

# UI Validation

## 2026-10-17 beta.15：全宽轻量材质与手机播放器布局

- VERIFIED：两MuMu隔离包已覆盖安装beta.15 / 30038，未清除用户数据、未发布。新增HomeRecommendationCard仅用于<600px首页两卡，宽屏仍使用原组件。
- 首页材质：动态取色38%→12%，模糊2/4/6px→0.5/1/2px；矩形与封面同宽，高度等于文字加上下各4px，顶部清透、向下增强。320–412宽度×100/200%文字、两卡横排、封面比例、标题逐行边界、矩形宽高、真实点击每日/喜欢入口均通过。测试使用可控彩色封面，并在结束恢复原账号、设置与歌曲/收藏数据。200%下标题放到封面下方以保证完整可读。
- 播放器：真实点击迷你封面打开→触摸滑动，覆盖360×800、320×640、412×1024及100/200%文字，含无歌词、翻译/罗马音歌词和长标题。入场/触摸后封面、信息与控件几何差异≤2px，标签和操作无相交；手机封面≤300dp。360×800信息区到页脚空隙Red为49.44px，Green前后均8px，封面前后均270px。完整用户截图中的跳位过程尚未独立复现，不扩大此结论。
- 手机套件8/8（96.623s）：首页、竖屏布局、默认/AMLL歌词跟随、Beta开关、锁定控制分离、字体/排除浮层与云盘Empty。平板2/2（20.2s）：原TabletDesktopLayoutTest、音量真实触摸/拖动/独立静音恢复。当前改动没有触及用户原有TabletDesktopLayoutTest源码。
- pnpm lint / typecheck / build:android及336/336项TypeScript回归通过；Gradle Debug/Release/AndroidTest、单元测试、Lint通过（55s）；Android Lint 0 errors / 27 warnings。文字放大采用WebView textZoom，不能据此宣称所有Android系统字号、所有主题/封面都已验收。
- 截图：home-beta15-360.png、home-beta15-320-large.png、portrait-beta15-entry.png、portrait-beta15-after.png；几何portrait-beta15-geometry.json。截图与日志仍在外部临时证据目录。此前beta.14与Red失败证据保留。
- Release仍BLOCKED：通知字体授权P1、历史后台歌词门禁与全量界面/状态覆盖缺口未因此关闭。当前结果只确认本节列出的增量。

日期：2026-10-16。产物是未提交工作区的预览构建；VERIFIED只表示指定用例实际通过，不表示全部界面通过。日志、APK、截图、几何JSON存本次Codex临时证据目录 next-stage/，不纳入Git。

## 设备与构建基线

- MuMu 手机：Android15，1080×1920，480dpi，默认360dp。
- MuMu 平板实例：Android15，280dpi、多显示面；专门配置1024×600的实际Activity显示面进行桌面布局验收，不能凭实例名称声称通过。
- Compact在Activity实际displayId上覆盖320/360/390/412dp，测试结束恢复size。记录CSS视口和density，不把adb默认显示面当作实际窗口。
- 大字号包括WebView textZoom=200；412dp另设系统font_scale=2.0。二者分别记录，不将textZoom假称系统设置。测试后恢复原字号。
- 原应用未覆盖、未卸载、未清数据。安装的是.debug隔离包；预览迭代beta.7/30030、beta.8/30031、beta.9/30032、beta.10/30033；源码默认版本仍beta.6/30029。
- Before时手机旧Debug包为3.0.15-debug，只证明该设备行为；相同缺陷同时在当前源码中确认。After使用本轮明确版本。
- 2026-10-17再次核对最新发布：v3.0.16-beta.6指向646c9db0，当前HEAD 9db84b14包含该Tag；两者src、android、package.json、pnpm-lock.yaml无差异。beta.10-debug仅为本轮预览编号；其APK内303个Web文件与本轮Capacitor资源逐文件哈希一致。版本标签和构建日志不能替代行为回归。

## 重要修复与截图索引

| 项目 | Before | After与行为证据 | 结果 |
|---|---|---|---|
| 默认歌词浏览恢复 | next-lyric-browsing/idle.png；默认143.67，浏览400.67后不回归 | NextStageInteractionTest真实滑动、按住时同歌数据刷新、释放后恢复 | beta.10 VERIFIED |
| AMLL浏览恢复 | amll-beta8.log；6秒后歌词组仍出界；beta.9残余19px | 修复超时不重排及尺寸变化后的陈旧锚点；仍保持5px精度 | beta.10 VERIFIED |
| 桌面锁定遮挡 | next-locked-lyric.png；控件区内5576个正文/阴影像素 | 正文区与控件区交集0像素；48dp目标；真实系统点击解锁 | beta.10 VERIFIED |
| Beta更新开关 | 原版无开关，默认值回归Red | 全局设置→关于：默认Stable→开启Beta明确标识→关闭回Stable | beta.10 VERIFIED |
| 首页窄屏 | next-audit-phone320-large-home.png；320dp右边界360.01；默认字号也越界 | CompactHomeLayoutTest四宽度×100/200%文字：长多语言标题换行、48dp具名操作、滚动后实际命中不被播放条遮住 | beta.10 VERIFIED |

重要After文件：beta10-core/next-update-stable.png、next-update-beta.png、next-lyric-idle.png、next-amll-idle.png、next-locked-lyric.png、next-home-<宽度>-<字号>.png、next-home-<宽度>-<字号>-fm-actions.png。PixelCopy等待过渡/布局，不能以单张图片替代行为和命中测试。

## 导航采样与未覆盖

- 运行时router.getRoutes清点53项，另检查八组设置。已采样360dp默认、320dp/200%文字、390dp默认、412dp系统/文字200%；每种61条观察。JSON为next-audit-<模式>.json，图片为next-audit-<模式>-<页面>.png。
- 父路由重定向到子页为正常导航；鉴权重定向（例如收藏歌曲跳回首页）不算原页面通过。
- 320dp确认首页越界并修复。部分设置记录中的首页outlier来自模态背景，不重复当成八个设置Bug。
- 没有outlier只证明所采可见几何，不代表滚动外内容、所有长文本、所有按钮与浮层通过。
- 39个Modal/账号组件、61个浮层/状态宿主逐项台账见NEXT-STAGE-AUDIT.md。尚未完成每个入口的Loading/Empty/Error/长文本/Light/Dark状态巡检；真实账号/服务端场景未全面覆盖。
- 2026-10-17用户在隔离包自行登录后，authenticated-phone360追加61条导航/设置观察；收藏五类、喜欢歌曲、云盘和每日推荐均进入对应路由，所采可见控件横向outlier为0。只读巡检未执行收藏/歌单/云盘写入；这不等于完整数据、浮层和大字号验收。
- account320-large另采8个账号页面、8组设置及6个设置浮层，明确记录Loading。可见几何扫描无越界，但视觉审查发现字体/歌词排除逐字竖排以及共享空态重叠；已新增真实行为回归，不能以几何扫描取代可读性验收。

## 验证结果

| 检查 | 实际结果 |
|---|---|
| pnpm lint / typecheck | VERIFIED；max-warnings=0，node+web；AMLL类型冲突修正后重新通过 |
| TypeScript | 335/335 VERIFIED；含版本、API跨页/限流/异常、真实Vue挂载卸载生命周期 |
| Python发布/工作流脚本 | 68/68 VERIFIED；本轮未修改这些脚本 |
| Android JUnit | 68/68 VERIFIED |
| Android Lint | 0 errors / 27 warnings VERIFIED；abortOnError保留 |
| actionlint 1.7.7 | VERIFIED；项目CI固定版本和相同参数 |
| pnpm build:android | VERIFIED；完整Web/Capacitor/内嵌API资源 |
| Gradle Debug/AndroidTest/Release | beta.10/30033 VERIFIED；本地预览，不代表正式发布通过 |
| beta.10新增设备回归 | 5/5 VERIFIED：两套歌词、Beta、锁定、首页矩阵 |
| beta.11首页横排 | 四宽度×100/200%矩阵VERIFIED，同一水平行、文本完整、FM48px与滚动命中；用户要求的问候语缩放和Beta右侧位置另由beta.12继续验证 |
| beta.11浮层/空态 | 两个可读性回归VERIFIED；字体标签由4行逐字竖排恢复，输入外层48px边缘真实点击可聚焦；歌词排除取消可达；空态图标/正文不相交 |
| beta.11构建/静态检查 | Web/Debug/Release/AndroidTest构建及lint/node+web类型通过；Gradle 1分20秒。没有改变正式默认版本 |
| beta.11核心交互 | 修正既有用户偏好与首次默认值混淆、MOVE提交前取样后4/4通过；5px精度保持。首次手机复查2项失败保留在日志，不能算产品修复 |
| beta.12 TypeScript | 336/336 VERIFIED，新增全新Pinia设置默认值与配置隔离行为；其余检查和设备验证进行中 |
| 平板1024×600 | beta.11 VERIFIED，16.352秒、1/1；既有TabletDesktopLayoutTest含两种歌词及桌面侧栏，未修改/提交该用户文件；beta.12需再验证 |
| 原有八项设备回归 | beta.8为8/8；beta.9为6/8，恢复回调超时和后台时钟推进失败均保留 |
| beta.9定点重跑 | 恢复2/2通过；后台仍失败，归因UNCONFIRMED |
| beta.10独立五分钟后台 | VERIFIED，完整305.252秒、三首自动衔接与歌词，原断言不变；CSV为beta10-long.csv |
| beta.10最终顺序八项回归 | 6/8；悬浮窗控制区展开断言失败（alpha期望1、实际0），后台切歌后歌词匹配断言失败；不能用独立五分钟通过替代全套通过 |
| 2026-10-17环境复核 | 两实例均运行但仅余测试辅助包，主应用包未列出，变化原因UNCONFIRMED；重新安装同一beta.10隔离APK，未执行卸载/清数据。平板悬浮窗权限default导致服务未建立，此前置失败与昨晚窗口展开失败分开记录 |
| 2026-10-17定点复现 | 恢复悬浮窗权限后Overlay测试1/1通过（18.156秒）；独立后台仍失败。增加CSV诊断后35秒样本显示原生音频位置35101ms、playing=true、READY、无suppression，但悬浮歌词为空；另一轮15秒时歌词时钟仅10929ms。根因继续定位，不视为已修复 |
| 2026-10-17歌词提交诊断 | 完整303.621秒通过，三曲衔接，四次歌词提交各100行（末尾回到第一曲）；失败未在该轮再现，不能据此关闭间歇性风险 |
| 当前改动云端CI | BLOCKED；按用户要求人工验收后才能commit/push，旧HEAD成功不替代本轮 |

环境问题按证据处理：首次pnpm/Node权限失败在正常权限下通过；esbuild临时目录删除受限，用工作区独立TEMP探针确认后通过；PowerShell预览参数需完整引用。它们不计为产品Bug，也不掩盖设备测试失败。

## 发布门禁

- RELEASE BLOCKED — P1：通知字体字形授权证据仍未补齐；beta.6的风险接受不适用于下一版本。
- 恢复/后台测试失败尚未完成归因，没有证据称为传感器泄漏，也不将环境假设当成已解释或已修复。
- 全部浮层、真实音源下载、在线完整APK下载与系统安装、跨厂商和长时间后台不宣称全面通过。
- 人工验收、准确目标commit/Tag、新CI、source zip及Release资产最终核对未完成。未执行commit/push/Tag/Release；未改正式版本、签名、依赖、工作流、.agents/和skills-lock.json。
# 2026-10-17 beta.13 增量验证

- IMPLEMENTED / VERIFIED：共享音量触发器不再执行静音；触屏点击展开，弹层独立静音并可恢复之前音量。手机720×360真实触摸、滑条拖动通过1/1（8.397s）；平板1024×600音量及更新下载代理失败回退通过2/2（5.544s）；原有TabletDesktopLayoutTest通过1/1（14.666s）。日志：next-beta13-volume-phone-named.log、next-beta13-volume-tablet.log、next-beta13-tablet-layout.log。
- 初次手机横屏测试按旧平板按钮序号点击，误点其他控件而超时；改为具名“调节音量”公共按钮后通过，未降低音量、滑条或恢复断言。原始失败日志保留。
- beta.13 Gradle assembleDebug / assembleRelease / assembleDebugAndroidTest / testDebugUnitTest / lintDebug成功（46s）；pnpm lint、pnpm typecheck成功。测试APK定位修正后仅重编AndroidTest成功（5s）。尚未提交、推送或执行当前变更的远程CI。
- 首页大封面局部材质仍为设计样稿，未合入Android：home-card-design.html加载仓库已安装AMLL Core 0.5.1的BackgroundRender / MeshGradientRenderer，两个真实canvas；封面取色、24fps、低强度6px柔化、文字中心至边缘透明蒙版。样稿保留两卡横排，200%文字转到封面下方；深浅主题可调。完整截图home-card-dynamic-feathered.png。浏览器样稿验证不能代替Android截图、真实性能及实际封面对比度验收。
- 仍保留此前后台歌词间歇失败、全量浮层覆盖缺口及字体授权P1门禁；本增量通过不代表Release READY。


## 2026-10-17 beta.16 回归与下一轮修正记录

- beta.16 Debug/Release/AndroidTest、Android单元、Lint通过；Lint 0 errors/27 warnings。TypeScript342/342，Python68/68（可写临时目录、本机本地clone测试），actionlint通过。
- beta.16手机顺序套件10/13通过；失败为TTML开关链路、低高度分页热区、FM高度。平板原有验收+音量2/2通过。不得把此版本描述为全套通过。
- 定点诊断：TTML真实缓存→真实按钮→真实解析→实际yrc-word节点1/1通过，但顺序套件失败尚需复核；分页320×640顶端热区实际命中播放按钮，FM320dp卡高147.33/封面96，超过目标。
- 下一轮源修改：低高度行距10dp保留48dp分页热区；FM文字区44dp可滚动；Compact两推荐卡删除附加说明，宽屏原文案保留。旧包说明删除断言Red。
- TTML进一步回归确认：旧优先级完成预加载和在途预加载均可覆盖显式TTML选择；分别Red→Green，切换清理缓存并用偏好代次拒绝迟到预加载。全部TypeScript344/344通过。新版设备结果待补。

## 最新增量调查：第三方音源缺失（2026-10-17）

- 已复现：beta.18 隔离包持久化 songUnlockServer 仅含 enabled Kuwo，缺少默认 Netease、Bodian、Gequbao。菜单按启用配置筛选，与 beta.6 相同，非菜单源码删除了服务。
- 关键关联：此前受控菜单测试曾把整个数组替换成单个 Kuwo；已删除这种不必要的测试写入。测试恢复异步完成前改变窗口/方向可能中断恢复，正在加强恢复完成等待及持久化校验。污染发生的具体时刻仍 UNCONFIRMED，不能把关联当作完整根因证明。
- 已恢复隔离包缺失的默认条目，保留现有项、排序与开关；Gequbao仍默认关闭。真实播放尚未通过验证，不因菜单恢复就判定故障已解决。
- 临时播放探针第一轮在启动尚无控制器时调用而失败（TypeError）；这是诊断前置问题，需等控制器就绪后再验证真实解析、原生playing与进度推进。

## 2026-10-17 beta.19 增量结果（尚未全套通过）

- VERIFIED：恢复手机歌词页。旧包在歌词清空/加载期间移除页面的回归为 Red；beta.19 的真实分页触摸、加载状态、无歌词状态、重新加载歌词及下一首加载期间停留歌词页通过。证据：beta19-phone.log 的 lyricsPageRemainsAvailableWhileLoadingAndAcrossSongChanges；截图 beta19-lyric-page-preserved.png。
- VERIFIED：桌面歌词锁定前后窗口坐标与正文位图完全一致；独立48dp解锁窗口不覆盖歌词像素，真实点击解锁通过；低高度无法放置安全控制时隐藏控制且不拦截触摸，应用原生解锁入口通过。证据：同日志 lockedControlDoesNotCoverAnyRenderedLyricPixels。未以缩窄正文或移动歌词实现避让。
- beta.19 手机14项通过11项。失败：音质菜单选项到达、极窄屏合并标签几何、AMLL浏览后默认锚点16px偏差。三项继续排查，不得记为全套通过。平板原有布局与音量2/2通过，全部TypeScript348/348通过；Gradle Debug/Release/AndroidTest/Unit/Lint通过。
- 真实官方音源已实际播放并推进进度；所测歌曲Kuwo返回404、Bodian无链接，Netease解析有链接但短观察未确认开始播放。这些是具体歌曲/服务的结果，不能泛化为全部音源可用或不可用。
- 修复音质/音源切换后 currentAudioSource 仍缓存旧链接的问题：行为测试先Red后Green，音源竞争测试16/16通过。菜单恢复与真实远程服务播放分别验证。
- 未提交、未推送、未发布。字体再分发证据P1及全量UI覆盖缺口继续保留，Release仍BLOCKED。

## 2026-10-18 续验：beta.21/.22

- beta.21首页四宽度（320/360/390/412dp）×100/200%文字通过；标题首行对齐、底部文字留白4–14dp、图片底角像素、材质宽高、随机封面、FM对齐与真实点击均通过。已人工查看beta21-home-320-100.png与beta21-home-320-200.png；200%采用原设计的标题流式排列，封面独立圆角保留。beta.20孤字换行失败记录保留。
- beta.22完整手机套件13/14通过：仅极窄屏合并标签内选择“自动”的真实触摸失败。恢复歌词页、锁定完整正文、首页、TTML、批量、横屏、心动、两套歌词回归、更新、设置/空态等本套对应项通过。此前AMLL16px偏差没有对应产品修正，在beta.21/.22通过，仍需保留间歇时序风险，不把一次通过当根因已关闭。
- 去掉合并外层11000层级后仍有失败，因此“层级是完整根因”尚未证明；当前补录失败时目标矩形、父层及真实命中堆栈，继续排查。保持原几何与真实触摸断言。
- beta.22平板原验收与音量2/2通过；Debug/Release/AndroidTest、Android单元/Lint、pnpm lint/typecheck通过。两台安装版本3.0.16-beta.22-debug/30045，保留数据。
- APK SHA256 d2cc67e3d9c048c606f9757328dfaa0c288d976baed7ec2ebfe4d116136dd214；Debug证书SHA256 607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。仅本机测试产物，无Tag/Release，源码仍未提交。

## 2026-10-18 beta.23–.25 已完成及beta.26待验

- beta.23手机四项通过：包含八种竖屏宽度/字号几何、390dp展开动作、极窄屏合并菜单真实选项触摸；父overflow裁切已确认，body挂载修复通过。
- beta.24/.25 AMLL真实触摸及鼠标悬停模糊、关闭清零通过。旧beta.23鼠标路径失败证据保留；不能用原先仅触摸通过替代鼠标复现。
- beta.25完整Android构建（含embedded Node资产）成功；Gradle Debug/Release/AndroidTest/Unit/Lint通过。pnpm lint/typecheck通过；首次Lint新增测试脚本全局引用错误已改显式window引用，未降低规则。
- beta.25平板2/2：完整正文锁定、48dp解锁、正文像素/位置相等、6dp居中位置、实际overlay显示器260×260极端空间回退，以及原TabletDesktopLayoutTest。手机在线切歌/锁定2/2通过；截图next-locked-lyric-screen.png已人工查看，属于合成歌词测试，未冒充真实歌曲截图。
- 真实在线前台对照：28941713→186016，两首原生歌词匹配；样本整体耗时3768ms/9530ms包括音频准备，不能当作纯歌词请求或解析时间。online-lyric-transition.json保留提交时间轨迹。
- 后台真实在线首轮失败：20秒后台后原生下一首仍留在28941713，60秒采样仍playing；不能称为“已切歌但歌词未切换”的同一复现。原生resolver记录186016解析失败，根因待定。
- 加入现有公开补窗入口预先确保音频可用后，20秒后台原生下一首→186016，1000ms采样已playing/position1003ms、正文为“故事的小黄花…”匹配独立公开歌词响应。循环中没有WebView求值、没有回到前台；52.521秒1/1通过。它仅是受控对照，不关闭用户报告或历史间歇风险。
- 自动模式LRC/YRC延迟和TTML异常回归先Red后Green；20项相关单元通过。beta.26源码新增渐进显示，完整设备验证尚待完成，暂为IMPLEMENTED。
- 背景模糊是独立浏览器设计预览，未修改原生背景；原生动态背景能力尚未验证。
- RELEASE BLOCKED — P1。字体授权、后台剩余问题、全量UI覆盖和人工验收继续保留；未commit/push/Tag/Release。

### beta.26 更新：设备与产物证据

- 源质量：353/353 TypeScript（9 suites），pnpm lint/typecheck通过；完整Web/Node/embedded资产构建通过；Gradle Debug/Release/AndroidTest/Unit/Lint通过33秒。Android Lint仍为0 errors/27 warnings。后续仅修改AndroidTest的渲染等待、平台入口与鼠标输入前提，未修改主APK。
- 后台真实在线无额外补窗：beta.25原生未切歌为Red；beta.26预载完成补交队列后，同一公开原生下一首入口切到186016，2秒样本playing且position1971ms，桌面歌词匹配独立歌词响应。样本beta26-online-background.json。不会把原生解析服务失败泛化为所有第三方服务故障。
- 平板后台自动切歌：在桌面保持70秒后，通过原生seek让真实音频播放到结束，由原生ENDED自动推进，不调用JS下一首。101.159秒1/1；目标曲开始约1秒后歌词匹配（采样第4秒已目标音频/仍旧歌词，第5秒目标歌词），没有App恢复或WebView求值。样本beta26-background-ended-tablet.json，不能宣称零延迟。
- 渐进显示：旧beta.25真实设备先Red（慢TTML期间LRC未提交）；beta.26手机通过。平板首次测试误用手机分页入口，修正为现有player-lyric可见区域后通过。进一步增加innerText确认替换旧文案与两帧等待，平板2/2（含AMLL）13.605秒通过；已人工查看TTML截图hello world及TTML标签。模拟HTTP延迟与公共缓存输入只验证显示/解析链路，不冒充真实远程TTML服务的延迟基准。
- 手机首轮16项15项通过，唯一失败为鼠标悬停前提未建立，未报告模糊断言失败。测试事件改为明确TOOL_TYPE_MOUSE并落在实际歌词中心，平板同断言通过；手机完整套件正在重跑，原失败日志保留。
- 主APK为3.0.16-beta.26-debug/30049，applicationId top.imsyy.splayer.android.debug，两台覆盖安装保留数据。SHA256 2fbe40c802c4b9a7c8476298f53a22ea9243159ed8a7953f8a2fc0a6e92b4bb5；官方apksig库验证签名通过(v2=true)，证书SHA256 607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。仅本机隔离测试包，不是正式Release资产。
- 通知字体blob重新核对仍为ce36d59d06853c0e340c42b43fa804efcd1d2cfd；NOTICE仍SOURCE_CONFIRMED_LICENSE_UNCONFIRMED，beta.6风险接受明确限该版本。RELEASE BLOCKED — P1。

### beta.27–.28 更新：每日推荐与后台自然结束（覆盖旧结论）

- beta.28完整Web/Node/Android资产构建成功；Gradle Debug/Release/AndroidTest/Unit/Lint通过，Lint 0 errors/27 warnings。正式版本仍beta.6/30029；本机隔离主包beta.28-debug/30051，两台覆盖安装保留数据。
- 当前源码重新执行lint、node/web typecheck、完整TypeScript回归：353/353，9 suites通过。测试Java增量编译通过。
- NS-022 Red：beta.26在320dp/200%标题高352px却位于固定300px容器，标题顶部出界，说明/菜单/列表重叠；更多按钮中心被共享SongList悬浮工具遮挡。Before截图beta26-daily-before.png。
- beta.28：Compact标题自然增高、不缩小55px原始标题，文字换行；按钮至少48dp；固定工具通过一帧合并后的实际列表/工具边界判断，仅在所属列表区域内显示。宽屏保留原布局。截图beta28-daily-after.png、beta28-daily-more-after.png。
- 平板原TabletDesktopLayoutTest、每日推荐200%/默认文字、Batch入口4/4通过。增补完整真实操作：首页→每日推荐→更多→批量操作→关闭→列表工具；首轮关闭按钮测试在入场动画中失败，增加有限动画等待后2/2通过15.490秒，未降低尺寸/点击断言。视觉截图增加WebView visual-state回调及两帧绘制等待，实际运行通过。
- 手机beta.26最终17项16项通过；唯一失败是AMLL鼠标:hover前提没有建立，不能记录为模糊断言通过。最新平板对应模糊入口通过；手机该项仍待处理。
- 后台正常速度五分钟自然结束失败；三倍速自然结束亦重复失败。beta.28事件轨迹确认先原生trackChanged到186016并加载该曲50行歌词，约1秒后native ended(durationMs=2612)触发JS nextOrPrev(autoEnd=true)回首曲28941713；不是已证实的单纯旧歌词不刷新，也不能直接判定为重复结束事件。观察期间不求值WebView、不seek、不发Next。下一步核对音源实际短片段与缓存；NS-033继续未关闭。
- 字体授权P1及最终人工验收仍未解除。RELEASE BLOCKED — P1；没有commit/push/CI/Tag/Release。

### beta.29–.30 菜单文字边界回归

- beta.28新增菜单Range/48dp断言真实Red（7.574秒）；截图可见两行选项文字越出固定行高。beta.29误将Naive menuProps写成对象，类型检查及两台设备均失败，保留日志，绝不计作通过。按已安装组件公开类型改成返回属性的函数后，beta.30类型/lint通过。
- beta.30完整Web/Node/Android构建通过；Gradle Debug/Release/AndroidTest/Unit/Lint通过56秒，Lint 0 errors/27 warnings。无依赖、签名、正式版本变更。
- beta.30平板3/3通过21.311秒：每日推荐320dp/200%、默认文字的真实入口/全文行边界/48dp/批量关闭/工具，以及用户原TabletDesktopLayoutTest。截图beta30-daily-menu200.png已人工查看，文字行间不再互相重叠。手机回归待执行，不提前记通过。
- beta.30主隔离包30053，SHA256 b6021ef8b65b220cf2629462977032ed01231351457155f6b517d920eb324ccf；apksig verified/v2=true，沿用debug证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。
- 音频诊断首版按songId保存最后一个src，可能被切歌期间旧metadata/新src状态覆盖。现保留进入曲目的第一个src；此前后台34MB/403证据不再视为目标曲音源证据。前台独立HTTP206为16009bytes，文件和直接请求均2612ms，只证明该次前台取得短音频。用户后台缺陷仍未关闭。

### beta.30 手机补充结果

手机320dp默认/200%每日推荐完整菜单/批量/关闭/列表工具2项通过；同轮后台原生Next控制也通过，3/3共67.469秒。目标186016进入时的第一个src确认为非loopback HTTP；缓存与独立HTTP206均16009bytes，二者独立媒体时长同为2612ms。观察到原生Next→目标曲及正确歌词，再由实际短音频结束回首曲；不能归为已证实的歌词未加载。保留原自然结束失败样本，追加28941712公开歌曲对照，仍沿用真实播放/后台/歌词入口，不替换或删除原失败测试。

### 后台与AMLL对照（beta.30）

- 原始186016目标的实际短音频问题仍保留，平板也取得相同16009bytes/2612ms；known-empty路径匹配为false，不能假称是已识别的404占位音频。
- 首个替代样本28941712为-Interlude-，公开metadata为29546ms，实际29597ms且只有一行歌词；三倍速下在观察期内正常结束，不满足“较长、有多行歌词且观察结束时仍在第二首”前提，此次失败属样本选择问题，原日志保留，不当作产品Red。
- 改用先核对的Echo/28941714（metadata212986ms、48行公开歌词）后，三倍速真实后台自然结束切歌通过1/1，140.054秒。过程中不seek、不发Next、不执行WebView求值；目标缓存首段与独立HTTP样本解析时长均213029ms。正常速度五分钟Echo对照尚待验证，不能把短Next通过代替自然结束。
- 手机AMLL独立场景：先通过公开入口关闭原生桌面歌词悬浮层，保留并恢复原开关状态，真实触摸→鼠标悬停→关闭模糊1/1通过31.952秒。旧顺序套件的hover前提失败仍保留；独立场景通过不宣称原场景已全部解释。不存在全屏失败截图是因为此次未进入失败捕获，随后的pull找不到文件不能当成测试失败。
- 运行原生锁定位置回归时，平板在临时显示器撤销后出现WindowManager.InvalidDisplayException，尚未到位置断言；手机独立执行才到达新左对齐断言并Red（138px预期/477px实际）。分别记录，不混为同一原因。FM320/100文字被父级固定高度裁切已截图与新增断言Red。

### 2026-10-18 beta.31–.34 增量结果（非阶段全部通过）

- FM：beta.30新增父级裁切断言Red；beta.31手机和平板320/360/390/412dp、100%/200%组合通过。短文本卡片保持紧凑，长文本允许自然增高；截图beta31-fm-short-after.png与beta31-fm-long-after.png。
- 悬浮窗：beta.31低高度safeArea未更新；beta.32使用同一窗口UI上下文和配置回调后，手机/平板低高度、位图、同屏、解锁完整通过。原硬编码displayId=0断言不成立，MuMu可映射默认显示器；不把该测试假设失败计作产品Red。beta.33补齐初始化异常范围和回调成对释放。
- 最新位置：用户指定未锁定锁按钮稍左上方，替代左下方案。beta.33旧包新位置断言18.742秒Red；beta.34手机27.705秒、平板11.312秒、手机系统font_scale=2.0时21.622秒均1/1通过，测试后恢复1.0。正文锁前后位图和坐标相同、控制至少48dp、与任何正文/阴影像素零相交、真实原生点击解锁及260dp低高度无安全区回退均通过。手机截图beta34-lock-phone.png已查看；平板为非零临时显示器，系统全屏截图未获取，不冒充截图通过。
- beta.34原TabletDesktopLayoutTest 1/1通过18.298秒，未修改用户测试文件。主包3.0.16-beta.34-debug/30057，两台adb install -r；正式版本未改。原生增量Debug/Release/Test/Unit/Lint全部通过19秒，Lint 0 errors/27 warnings；Web资产沿用已验证beta.31（本轮只改原生）。
- 后台真实对照：Echo三倍速自然结束140.054秒1/1通过；正常速度五分钟观察327.045秒1/1通过，观察期不执行WebView求值/Seek/Next，原生悬浮窗持续可见且正确加载下一首。原186016返回2612ms短音频样本仍保留，NS-033未全面关闭，不能把该对照当所有音源正常。
- AMLL独立触摸/鼠标悬停入口在关闭悬浮窗干扰后手机1/1通过31.952秒；此前顺序套件失败日志保留。最新TypeScript 353/353、9 suites、lint/typecheck已通过。
- beta.33平板组合套件9项中5通过、4失败：四项手机播放器标签/模式/TTML/横屏入口未出现。布局分支/临时显示器测试准备仍待定位，不能宣称整个组合套件通过。beta.34仅上述原生及平板基线验证通过。导航全量已加载/错误/空态覆盖仍未完成，第二轮同代理复核不等同独立人工验收。
- 未commit/push/Tag/Release；通知字体授权证据P1及人工验收仍是发布门禁：RELEASE BLOCKED — P1。

### 2026-10-18 beta.35–.36 与账号重新登录

- beta.36完整Web构建、Debug/Release/Test/Unit/Lint通过（Gradle1分28秒），Lint0 errors/27 warnings；lint/typecheck及353/353 TypeScript通过。正式版本仍beta.6/30029，测试隔离包beta.36/30059。
- 主APK SHA256：F16BF261EF650A9724325A5F71DFABDA4B06D6C2C6EE8022D8FFFC005EEAEA59。签名复核和保存副本待完成，不以历史包替代。
- 平板16项组合112.971秒全部通过；默认锚点、最新锁位、原TabletDesktopLayoutTest另3项48.143秒通过。用户平板测试文件没有修改。
- 默认歌词新增自然内容尺寸观察，修复控件收起后初始锚点陈旧。原beta.35初始720/恢复576，beta.36独立37%默认锚点及微小偏移、真实手势/同歌刷新验证通过。
- beta.35手机拖动：默认位移增益1.011/P95 88.2ms，AMLL0.339/44.7ms；beta.36默认0.945/69ms、AMLL1.000/59.1ms。每次只有4–14个有效样本，不能推断所有设备帧率；延迟指标包括输入排队。
- 逐词+弹簧平板仍失败：并行负载时745/994ms尖峰，单独复测P95 167ms；不计通过。普通歌词段已执行点击定位和长按菜单，AMLL该组合尚因前置性能断言失败未到达后续操作。继续保留阈值。
- 手机重新登录后认证布尔状态true；320dp/200%导航八个账号路由及八个设置面板，全部采样没有水平outlier/持续加载。实际截图复核发现歌曲列表操作列标题竖排，列入NS-040；该巡检不等于全部弹窗/菜单通过。
- 账号4项交互：歌单批量、云盘空态通过；每日推荐默认/200%两项最后定位工具断言失败。容器几何在列表内，需核对当前原生歌曲是否属于合成列表；未掩盖原失败日志。
- 测试修正：手机路径测试显式选项目既有phone分支，平板自动分支另测；原生WebView.onHoverEvent验证悬停，不声称系统全局鼠标注入成功。测试退出主动触发已有持久化flush，确认恢复后歌曲ID和音源配置；不清空用户数据。
- 账号截图：beta36-account-playlists-phone.png、beta36-account-songs-phone.png、beta36-account-cloud-phone.png；日志beta36-account-navigation-phone.log/.json、beta36-account-interaction-phone.log、beta36-word-spring-tablet-isolated.log。
- 最新锁位置改为正文正左侧；旧beta.34手机及200%截图只证明旧位置，不能当最新左侧证据。最新手机与200%待验。
- beta.37浅层引擎引用仅IMPLEMENTED；Web构建遭esbuild临时文件remove Access denied，尝试更换临时目录仍未完成，绝不记为可安装已验证版本。
- 本阶段未完成，Release BLOCKED—P1；未提交、推送或发布。

### beta.37–.38 增量证据

- beta.37 Web构建权限故障已在保留既有pnpm版本后，通过bundled Node、工作区临时目录与ESBUILD_WORKER_THREADS=0完成；未重装依赖或修改锁文件。备用pnpm提示移除依赖需TTY后中止，未继续。完整Gradle44秒，lint/typecheck通过。
- beta.37隔离APK保存为splayer-beta37-arm64-debug.apk（64,438,684字节），SHA256 58b31860afb4123ad9eb989a4453e1b0ab432b094a5a42f859dd55b61e9ecbf9，apksig verified/v2=true、v3=false，debug证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。不是正式签名发布包。
- 手机最新正文左侧锁位默认字号19.688秒、系统font_scale=2.0时17.485秒，均1/1通过；原生48dp控制、正文位图/坐标不变、像素零交叉、真实点击、低高度安全区均保留。截图beta37-left-lock-phone.png已查看，系统字号finally恢复原值。先前“手机最新位置待验”在这一切片已关闭。
- 每日推荐新增失败查明：账号初始化可替换合成日推，当前曲不在显示列表时，本就不生成定位按钮。明确当前曲属于实际显示列表后，默认及200%入口2/2、26.98秒通过（beta37-daily-membership-phone.log）。不修改产品来满足错误测试前提。
- 逐字测试起初用第一条.on选中旧渐隐行，导致目标在屏外；改为固定35秒公共时钟对应的索引5行，保留可命中、原生触摸和实际seek命令断言。基准字体固定32/18/18，独立大字号测试另计；AMLL阻止浏览器click，点击定位以实际命令验证，不能要求原生DOM click。
- beta.37手机两引擎逐字+弹簧完整回归1/1、39.479秒，通过拖动、恢复后实际点击定位、长按菜单且不误seek。默认5样本、增益1.032/最大70.4ms；AMLL10样本、增益1.000/最大98.5ms。测试样本有限，不能替代平板失败；此前成本探针未匹配实例，costs为空，不代表引擎成本0。
- NS-040在beta.37新增表头断言真实Red：200%两行77.8px超出40px表头；beta.38仅Compact操作列使用自然不可压缩宽度，不改变虚拟列表高度或字体。Green待核对。
- beta.38完整Web及Android Debug/Release/Test/Unit/Lint通过（Gradle47秒），lint/typecheck通过，353/353单元通过9.714秒。主APK64,438,772字节，SHA256 0880a916d4613046391a388aa383357e85df41e7b3f78a2a9e035e0b4a70290a；已保存splayer-beta38-arm64-debug.apk，签名及设备最终套件待验。
- 以上均为局部验证，阶段未完成，未commit/push/CI/Tag/Release。

### beta.38 手机组合与平板基线最终结果

- 手机21/21通过，260.811秒：NextStageInteractionTest全部6项、CompactControlInteractionTest全部7项、CompactContentReadabilityTest全部6项、PortraitPlayerLayoutTest、VolumeTouchInteractionTest。此前失败日志全部保留，未删断言、未跳过任何该套件方法。
- 账号已登录前提下验证日推默认/200%菜单、批量关闭、定位工具、表头文字1行且不越出；云盘真实空态和本地/流媒体五分类加载与空态通过。截图beta38-cloud-phone.png等。
- 平板3/3通过20.034秒：默认/200%日推真实入口及新表头断言、用户原TabletDesktopLayoutTest。beta38-daily-header-tablet.png已查看，但该截图停在标题区，不能用它冒充表头视觉截图；新增列表滚动后截图等待下一次测试采集。
- 主APK apksig verified=true、v2=true、v3=false，仍为既有debug证书。手机font_scale确认已恢复1.0。两个MuMu均保留已安装隔离包。
- 平板逐字/弹簧固定字体复测仍失败：AMLL10样本最高106.4ms（阈值100ms），默认4样本最高31.4ms；位移增益仍约1。输入排队与引擎成本尚未定责，costs探针仍没有匹配实例，不能宣称JS计算为0或性能全部通过。下一步仅测试端临时渲染提示对照，不改产品功能、不降低标准。
- 公开媒体补覆盖已取得播客1230174483与分类3/2等有效ID；首次视频API无响应，不把无效/缺数据页面当通过。流媒体歌单需要真实连接。

### 平板输入延迟性能轨迹（诊断，不等同无开销验收）

- 已取得与真实拖动同步的正确SPlayer WebView轨迹beta38-synchronized-v2-webview-trace.json（113804事件）。确认标题SPlayer、来源http[本地路径已省略]
- 测试专用profile参数通过公开WebView.setWebContentsDebuggingEnabled(true)临时开启，并在finally关闭；正式capacitor配置仍为false。采集辅助脚本最初因CLI守护进程继承输出管道挂起，改用独立文件后同步成功；未改App解决工具问题。
- 轨迹中21个触摸事件完整对应。WebView EventDispatch约4–7ms；入口到渲染线程约0.7–9.4ms。记录112.5ms尖峰并非全部发生在该WebView处理区间；最大相邻入口间隔约116ms。runOnMainSync同步投递前事件已创建，可能把测试自身队列等待计入输入延迟，尚未以该推断撤销原失败。
- 因此在相同真实拖动入口保留direct对照，新增系统input -d实际显示器swipe，仍要求位移增益0.65–1.35、至少3帧、移动>50px、P95<100ms。系统路径结果待核对，未降低断言。


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
| NS-046 | SongManager/音质 | 新音质API已返回新URL，最终仍播放旧lossless URL却标成HQ | P1 | 公共useSongManager().getOnlineUrl两次取源：先lossless再exhigh，真实类执行Red；实际url=lossless、quality=HQ | getOnlineUrl重新按歌曲ID取本地URL，checkLocalCache忽略quality/md5 | 新API已成功时采用其规范化URL；由原生CacheDataSource按音频身份命中缓存，避免跨音质回退 | 本阶段最小修复 |

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

### beta.51 锁位置与迷你播放器复核
- 本轮重新检查FloatingLyricService.java:497、lyric.ts:759、MainPlayer.vue:38/55/898/1244及对应触摸/图片/逐帧布局断言。没有发现本增量另一个有证据的待修缺陷。此为同一代理的新一轮审查，不能冒充独立人工审查。
- 锁图标仅在安全区内的正文正左侧显示；不足时INVISIBLE、alpha0且NOT_TOUCHABLE，歌词窗口尺寸/坐标和待机位图不变。Android设置按钮等待原生解锁成功后才提示成功，失败有明确提示，不依赖旧More入口，也不改桌面端配置开关。
- 小封面出错时使用原封面回退；占位图不再继承透明，按解析后小封面URL更新过渡。手机副标题的新旧过渡内容放同一Grid单元，info切歌保持挂载；大字号自然增高允许，桌面info切换行为不变。
- Red证据：beta.50全宽锁图标错误退到上下方；缩略图错误但原图有效时仍空白；100%slide下底栏64→71.796875。Green：beta.51手机定向2/2（50.835秒），平板同两项加原布局基线3/3（50.505秒）；图片实际解码/可见度断言通过；100/200% × fade/slide逐帧高度变化<=1px，保留至少48dp触摸目标。
- Web构建、lint、typecheck、TypeScript355/355、Android Unit68/68及完整Debug/Release/AndroidTest/Lint通过；Lint0错误/27已有警告。手机31/31项组合已通过326.358秒。APK64439436字节，SHA256 7EED94C7E275314DB6FC4BFD67891705967E95D6D2EC183CD779C985EE1F35D4；v2及原隔离证书验证通过。
- 用户要求暂不排查启动崩溃与字体授权；此为工作优先级调整，不把历史风险记为已解决或发布授权。没有commit/push/CI/Tag/Release。

截图：beta51-mini-tablet-320-200.png已人工查看，实际320dp宽窗口、WebView文字200%，封面为回归用已解码图案，非真实账号封面；不得冒充远程歌曲全部通过。beta51-lock-tablet-lyric-unchanged.png为原生歌词位图，锁前后逐像素相等另由测试断言；平板辅助显示器无法取得系统合成截图，不能将该位图冒充完整屏幕。Android15 MuMu，手机density480、平板280；测试后恢复窗口和文字设置。

本轮手机系统合成截图beta51-lock-phone-screen.png已核对设备写入时间并查看：独立锁图标位于正文正左侧，未遮挡正文；底层为恢复后的真实账号首页，迷你播放器可见真实封面。这是当前一次成功渲染证据，不证明所有远程封面或断网场景均通过。原生锁前后正文位图相等、完整48dp触控区在系统安全边界内及真实点击解锁均由定向测试验证。

### beta.51本轮完成状态
VERIFIED：手机完整31/31通过326.358秒；平板定向及原布局基线3/3通过50.505秒。构建/单元测试/代码规范/类型/Lint及签名检查通过。锁图标位置、设置解锁、底栏封面回退和高度修复均纳入本轮真实渲染回归；未修改字体或追查启动崩溃。剩余历史风险仍按原记录保留。dev HEAD仍9db84b14，未暂存/提交/推送/CI/Release。

## beta.52/53证据纠正与增量复核
- 封面缓存初次测试写入键漏了文件名末32字符规则，导致旧包及新包均读不到测试图片。这两次Red属于夹具错误，不能用来证明产品缓存行为；原日志保留。按规则修正后，beta.52手机203ms、平板135ms实际从原生缓存blob解码显示，在不可用图片域名下通过。旧底栏n-image不调用covers缓存的根因属于代码可确认事实，不能冒充有效旧缓存设备Red。
- FM长文本等高检查通过后，旧“main-rec后代必须完整换行”仍间接选中FM，违反用户最新省略要求。只排除该FM后代，并新增FM的nowrap/ellipsis/hidden及安全边界断言；按钮48dp、其它页面完整文字检查保留。beta.52手机最终完整Home入口1/1通过37.679秒，320/360/390/412dp×100/200%文字。
- beta.52真实在线切歌/桌面歌词入口通过，时序捕捉到官方URL请求每次约1.57–1.65秒、官方失败后第三方回退。因此不能把该样本当作纯官方播放成功，也不能宣称网络延迟已消失。
- 真实时序还显示预载与切歌重叠取源。公共getOnlineUrl并发同资源Red实际请求2次/应1次；在途Map按歌曲ID、音质、PC标记、AI禁用状态及账号会话隔离；成功失败都清理，只复用未完成请求，不长期缓存已完成URL。不同质量与已完成后重新请求、账号切换均有回归。账号隔离在复核中补充Red→Green，最终专项11/11通过。
- beta.53为本轮最终候选，当前完整构建和设备回归尚在进行，不将beta.52部分通过写成beta.53验收通过。

### beta.53第二轮增量审查（设备组合待完成）
- 重新审查FM单行省略范围、MainPlayer到s-image/cache接口、可选原图回退、显式hide锁清理和SongManager请求选择/清理链路。未发现本增量另一个有证据的待修缺陷。本次为同一代理重新审查，不能冒充独立人工审查。
- 待机/切歌图片改用现有covers读取，不引入第二套缓存；默认s-image调用者未提供fallbackSrc时逻辑不变。未恢复More解锁项。显式关闭清锁与系统重建保留状态区分处理。
- 在途取源Map只存Promise，按歌曲/配置/账号会话隔离，双分支清理；不缓存完成URL；原生缓存仍按资源身份。复核中的账号隔离缺口已用公共Red→Green补齐，专项11/11通过。
- 普通音质省去额外串行元数据；AI/杜比与失败降级保留；低优先级请求启动时立即接住失败，按配置顺序等待，后到响应不修改选中结果。仍可能等待失败的高优先级源或实际网络请求；不将优先级改为抢答，也不宣称网络秒开。
- 已通过：364项完整测试、lint/typecheck、Web构建；Android完整构建33秒、Unit68/68、Lint0错误/27既有警告；v2原隔离签名验证。最终设备组合进行中。APK64439560字节，SHA256 F19BF5D14A982E2FE728934AE37E6CCA2E7425B026E7EB7591CBA9DBC9F5C4E1。
- 证据修正保留：旧封面Red夹具格式无效；FM旧完整换行检查选择器间接包含FM，按明确新需求修正并保留独立省略/等高/触摸断言。不能用这些夹具失败宣称产品回归。

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

## beta.55 / beta.56 本轮新增修复证据（2026-10-19）
- Phone MuMu SM-A5560，Tablet MuMu ALT-AL10，均Android15隔离包；正式beta.6版本不改，预览beta.56/30079原位安装。没有真实ARM64手机。
- NS-055 Before：beta54-metadata-horizontal-before.png，手指按住32dp横拖info.left从16到-16；After：beta55-metadata-horizontal-after.png，双方向按住不移位，分页真实进入歌词。默认截图1080x2160/480dpi，宽360dp；Phone完整播放器矩阵320/360/390/412dp及WebView文字100/200%在beta.55和56均通过。未将WebView textZoom200%冒充系统字体200%。
- NS-056：受控HTTPS图片返回真实PNG，后台下载120ms，独立image请求4000ms；beta.54在图片字节可用1.2秒后仍占位的有效Red，beta.55/56升级blob实际解码并可见。原生已缓存图片且图片域名不可用、失败缩略图回退、底栏播放/暂停及切歌高度回归保持。
- NS-058：beta.55通过真实setupSongUI选新歌后1.5秒无大图请求Red；beta.56提前准备实际s/l，播放页打开后真实64px PNG解码可见。测试图片均合成资源，不是官方封面端到端网络时延结论。
- NS-057：beta.54连续预载使真正下一首离线512KB读取失败Red；beta.55/56优先真正下一首后生产DataSource离线读出完整512KB且字节身份正确。
- beta.55新增Phone5/5（49.159秒）、Tablet5/5（25.447秒）；Phone扩大回归10/10（129.467秒）、Tablet3/3（24.153秒）。beta.56最终Phone16/16（175.179秒）；Tablet8/10（73.027秒），组合快速选择与后台窗口两项失败保留。
- beta.56后台窗口默认display0独立重开后1/1通过42.664秒，但仍保留NS-054。快速选择新增资源诊断，未把未知事件标识问题写成已确认真串歌。

### beta.59 最终相关回归（2026-10-19）
- VERIFIED：两台MuMu均覆盖安装3.0.16-beta.59-debug/30082，保留数据。手机新进程16/16通过204.471秒；平板相关9/9通过49.122秒；平板切歌三轮各3/3通过16.411、12.284、12.154秒；真实在线后台预备下一首桌面歌词1/1通过50.831秒。
- 手机最终覆盖标题/SVG双方向按住拖动、真实分页、320×640/360×800/390×800/412×1024dp各100/200% WebView文字、封面300dp上限、标签图标不挤压、封面、音量、TTML、菜单与模式按钮。WebView200%不冒称本轮系统字体200%全量验收；手机横屏和其他未重跑页面沿用历史覆盖，不能称本轮全量巡检通过。
- 受控WAV自然结束到下一首isPlaying事件：手机44ms、平板106ms；最终快速选择起播：手机242ms、平板174ms。不是声学采样间隙，不代表任意在线官方源无延迟。下一首真实缓存字节离线读取及所有受控状态的src/songId一致性断言均保留。
- 冷封面：真实PNG后台下载120ms、独立img请求4秒场景通过，下载完成后不继续等待慢图片请求；当前歌曲大图在未打开播放页时提前请求并解码。不是任意CDN即时性的保证。
- 必须保留的失败：手机首轮切歌2/3（歌词初始化超时）、完整组合10/16；存在测试外真实歌曲、封面不一致、动画/菜单/TTML/模式等待失败。NS-060 P1风险UNCONFIRMED；新进程16/16与隔离通过不抹去失败。NS-054组合显示器风险未关闭。beta.58后台准备失败也保留，不用59通过倒改历史。
- VERIFIED：beta.59 Web构建、lint/typecheck、项目369/369、Android Unit68/68、Debug/Release/AndroidTest构建通过；Android Lint0错误/27既有警告。工作流Python68项及actionlint是beta.58实际结果，本轮工作流未变，未重跑、未称CI通过。
- APK：64439856字节，SHA256 01D766AA86F66945CA6BA822DEF5573F641B9343CD0F9FA066F4A86C80847C3F，v2有效，证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。证据为外部会话目录中的beta59-*与splayer-beta59-arm64-debug.apk。
- 截图已查看：beta59-phone-final-metadata-held-horizontal-icon--1.png为Android15手机360dp/density480、实际按住图标横拖，信息左侧稳定且歌词分页保留；默认占位封面是合成歌曲夹具，不冒充冷图证据。beta59-tablet-new-song-cover.png为强制手机分支的真实64px合成PNG解码证据，不作为平板原布局或所有大字号内容通过证据。
- 第二遍code-review增量报告已修正beta.58过早结论，保留原有finding IDs；同一代理复核，不冒充独立人工审查。Release BLOCKED：NS-045启动根因UNCONFIRMED、NS-033短音源P1、字体授权P1，以及NS-054/060组合风险未关闭。用户暂缓调查不是发布风险授权。
- Git：dev/9db84b14b28148c045bdd91c6c6061fd4122f433，85项现有修改/未跟踪路径，未暂存/commit/push/CI/Tag/Release。正式版本、工作流、签名、README/LICENSE未改；用户TabletDesktopLayoutTest、.agents、skills-lock保持。

### beta.60 双端修复验收结果（2026-10-19）
- VERIFIED：TTML直连请求超过6秒实际Abort，沿原优先级回退；歌词UI请求12秒截止，已有内容保留，旧/超时返回不再覆盖。自动模式TTML先就绪也立即发布，不再只允许官方先显示。未获得内容时退出加载进入原有空状态；总UI截止不声称可以取消不提供取消接口的缓存IPC，也不是系统冻结时的强实时保证。
- VERIFIED：信息区恢复横滑翻页，info-group自身scrollLeft=0；标题和SVG按住双方向32px时与封面相对位置保持，整页保留既有边缘0.3阻尼；实际160px滑动进入歌词页。纵向浏览、点击菜单、歌词分页及播放器几何回归保留。此前绝对left不动的断言不再代表用户目标。
- Red证据：beta60-device-valid-red.log在旧beta.59上2/2失败（挂起TTML不回退、信息区禁止翻页）；beta60-loading-unit-red.log及beta60-auto-ttml-red.log分别捕获整体请求无截止和已就绪TTML等待官方。早期0测试安装错误不计作产品验证。

| 检查 | 实际结果 |
|---|---|
| 手机核心两项 | 2/2通过，45.011秒 |
| 平板核心两项 | 2/2通过，18.446秒 |
| 手机完整相关组合 | 17/17通过，234.010秒 |
| 平板相关/原布局组合 | 11/11通过，56.378秒 |
| 平板真实在线后台下一首歌词 | 1/1通过，55.507秒 |
| AMLL挂起TTML→回退可见歌词 | 手机1/1通过28.663秒；平板1/1通过11.412秒 |
| 项目TypeScript测试 | 373/373通过；其中歌词管理24项 |
| lint/typecheck/Web构建 | 通过 |
| Android Debug/Release/Test构建 | 通过，完整构建2分35秒 |
| Android Unit/Lint | 68/68；0错误、27既有警告 |
| 工作流Python/actionlint | 本轮68/68通过6.668秒；actionlint退出0 |
| CI | 未运行，等待人工验收及后续提交推送 |

- 手机覆盖320×640、360×800、390×800、412×1024dp及100/200% WebView文字；保留封面上限、控件不挤压、实际逐字TTML、更多菜单、音量、模式、批量操作、横屏控件及封面/切歌等相关断言。WebView200%不冒称本轮系统字体200%全量巡检。
- Android15 MuMu手机SM-A5560/density480，平板ALT-AL10/density280。仅MuMu；未新增真实ARM64设备验收。本轮截图beta60-ttml-fallback-phone-core.png、beta60-amll-fallback-phone.png已查看，均为测试自有缓存歌词；beta60-metadata-swipe-phone-core.png为手指按住横滑中，封面与信息整页一起移动，不能把正常翻页过程中内容离屏再次判为局部漂移。歌词正文真实解码/可见由回归断言核对；不是所有远端歌曲/网络条件的即时性证明。
- 主应用beta.60-debug/30083两台均已覆盖安装并留在前台，保留账号和数据。APK64440248字节，SHA256 8C52B2F43951308A9471656A7D5E163BBC4501F908A63F29A303305DEC3FA732；v2验证通过，隔离证书607f7260704bf3e03a1ac1489351aa010008959acfa825345be71c989f07343c。
- 新一遍code-review检查了超时计时器清理、先到结果优先级、过期请求所有权、缓存/解析异常、翻页与纵滚、触控和页边缘。此增量未发现另一个有证据的未修代码问题；同一代理复核，不冒充独立人工审查。
- 历史风险保持：NS-060手机组合/NS-054显示器组合未确认根因，本轮组合成功不等于关闭；NS-045启动崩溃根因UNCONFIRMED、NS-033上游短音源P1和通知字体授权P1仍在。Release BLOCKED—P1。用户暂缓调查不是本版本发布风险授权。
- 人工验收门禁：等待用户验收beta.60后，再准备明确范围的commit/push/CI；发布仍须经过原有门禁、目标commit/tag/APK/资产及Environment审核。当前未暂存、commit、push、CI、Tag或Release。工作流单元测试中的“已发布”字样来自隔离夹具，不是实际发布操作。
- Git：dev，HEAD9db84b14b28148c045bdd91c6c6061fd4122f433；86条现有修改/未跟踪路径；60个已跟踪文件累计变更。diff --check通过。正式版本、README/LICENSE、工作流、签名无新增修改；既有AMLL授权锁文件补丁保留；.agents/skills-lock及用户TabletDesktopLayoutTest未改。建议阶段commit：fix(android): stabilize playback, lyrics and compact layouts。

## 发布前版本核对（2026-10-19）

- 公开目标：v3.0.16-beta.7 / 30030，接续公开 beta.6 / 30029；隔离测试包使用独立构建号 30085，不限制正式包升级。
- Web 构建、373 项应用测试、68 项 Android 单元测试通过；Android Lint 无错误，保留 27 项既有警告；75 项发布/工作流测试及 actionlint 通过。
- beta.7 平板核心组合 4/4 通过（31.158 秒）。手机组合 3/4 通过，自然衔接未完成且事件中恢复了原账号歌曲；相同自然衔接独立进程 1/1 通过（8.102 秒）。原因仍 UNCONFIRMED，不记作手机组合通过；保留在本次明确接受的历史组合风险范围内，不豁免随后 CI 门禁。
- 四架构正式构建清单为 beta.7 / 30030；本地未注入正式签名，最终签名与公开 APK 校验仍由受保护流程执行。必要源码归档已按固定来源、大小和摘要实际下载验证。
- 暂存限定阶段文件；排除 .agents/、skills-lock.json 和原有 TabletDesktopLayoutTest.java。Git 全量空白提示仅来自统一差异补丁中固有的“上下文空格＋原始制表符”；逐项核对 32 处上下文，实际新增源码空白检查通过，补丁摘要与锁文件一致。

## 远程 UI 回归失败及局部测试修正（2026-10-20）

- a2519339 的 Android CI 成功，change-review 实际人工审核已通过；同提交 UI 工作流 39345685282 手机/小屏各 4 项、平板 3 项失败。未创建 Tag 或 Release，不能把这些结果算作通过。
- 已确认测试前提问题：WebView reload 后清理阶段持有已销毁页面的 fixturePinia；音量测试把 1024dp 乘原高密度后超出模拟器窗口表面上限；旧悬浮窗测试仍点击按用户要求隐藏的解锁入口。
- 局部修正：清理时重新获取当前页面的真实 stores；音量入口在可实现的 160dpi/1024dp 窗口测试并无条件恢复原密度/尺寸；解锁测试按真实可见性验证图标或设置命令分支。音频队列用例回到迷你播放器状态，隔离与该入口无关的全屏歌词/背景渲染，并提前保留失败计时证据。
- 保留原八项与全部新增回归；自然衔接 <1200ms、快速最终目标 <1500ms 的原断言不变，不删除、跳过或放宽测试。全屏信息区、歌词与播放器的真实 UI 回归仍独立运行。
- MuMu 双端相关组合均 7/7：手机 75.311 秒，平板 39.897 秒。实际 Media3 ENDED→下一首 isPlaying 为手机 60ms / 平板 73ms（不是声学无缝间隔）；快速切歌最终目标为 103ms / 88ms。两端测试后密度恢复为原值。
- Android 测试包编译、75 项发布保护脚本检查通过。此为同代理二次增量复查，未改变应用代码、公开版本、签名、原人工审核与资产保护。远程三尺寸 UI 仍待新提交复验，不能以本机 Green 代替。

## 捕获层真实遮挡修复及最终候选（2026-10-20）

- 本机平板完整重跑15/16，TTML菜单真实触摸被drag-handle拦截，日志命中堆栈及修复前截图已保留。独立旧用例随后1/1通过，保留时序不稳定事实，不伪造稳定Red。
- 修复仅改FullPlayerMobile的捕获区高度：已有顶栏流高度＋封面流高度，不改变封面、信息区、按钮布局。增加捕获边界、真实更多面板、横滑与下拉关闭验证；测量改在既有截图视觉帧确认后进行，原1dp容差不变。
- 双端相关回归：手机15/15，216.787秒；平板14/14，75.020秒。覆盖TTML/加载回退、菜单音源音质、心动模式、批量操作、手机横屏、旋转、信息区手势、封面上限和快速切歌；手机另含320/360/390/412dp与200%文字矩阵。额外同路径更多菜单/TTML截图单项再次验证，实际结果在外部原始日志。
- 快速切歌保留JS<1500ms断言，增加原生首播同值断言与双计时：手机原生113ms / JS115ms，平板原生79ms / JS77ms。它们是事件时间，不是声学衔接间隔。远程旧两核模拟器仅此项仍失败；四虚核只用于验证资源假设，原八项、总用例数、设备尺寸、动画及限值均未减少，最终仍以新CI为准。
- Build、Lint、typecheck、373应用测试、68Android单元测试、75发布保护测试、actionlint通过；Android Lint保留27项既有警告。公开版本仍beta.7/30030；本机新候选隔离包独立构建号30086。
- 原签名核对补充：本机Release实际使用已存在原证书，四架构均实证匹配受保护指纹，均为原包名、beta.7、30030且非debuggable。ARM64正式包手机/平板均以install -r成功安装，冷启动2045/2168ms，进入首次许可协议页并继续存活；未代用户接受协议。此前本地“未注入签名”不代表无签名，签名状态以此次apksigner实证为准。最终发布仍只使用受保护CI产物，产出后再次核对安装。
- 本机相关通过不能代替远程UI。历史风险授权、NS-064验收失败、原始中断和失败日志保留；未创建Tag或Release。原签名、稳定版、源码/许可/摘要资产范围及双Environment保护不变。

- 截图补充的TTML单项：平板1/1（4.802秒）；手机首次失败（开关已开、普通歌词50行），随后加入歌曲ID/测试状态诊断的同入口两次均1/1（18.750/19.129秒）。失败保留为UNCONFIRMED组合状态风险，不以随后成功覆盖，不宣称已修复。门禁继续要求远程完整通过。

## 通知合并候选（2026-10-20）

- c1212503的远程Android CI成功；UI手机15/17、小屏16/17、平板15/16，失败与双时钟数字详见NEXT-STAGE-AUDIT.md。没有创建Tag/Release。
- 候选局部验证：MuMu手机6/6（87.471秒）、平板6/6（30.080秒），覆盖HTTP自然衔接、快速最终目标、下一首缓存、信息区手势/菜单及两项悬浮歌词服务恢复；快速切歌原生/JS分别81/84、78/76ms，均小于原1500ms，不代表远程性能问题已关闭。
- Android构建、68单元测试和Lint成功；75发布保护测试成功。默认沙箱临时目录及本地git浅克隆曾受权限限制，改为已授权可写临时目录并在本地执行后通过，失败日志保留。应用默认版本仍beta.7/30030；仅隔离包构建号30087。
- 通知首次提交即时，后续合并上限200ms，只改变通知快照调度；播放事件和歌词不节流。服务脱离/清理取消待提交任务，后台长时组合验证继续执行。

- 完整候选组合：平板16/16成功（413.977秒）。手机前8项含五分钟后台均通过，进入第9项后ADB断连；MuMu管理器实证手机实例is_process_started=false、is_android_started=false，平板仍运行。整台虚拟机退出原因UNCONFIRMED，不能当作手机17/17成功或已确认应用崩溃。手机局部6/6证据仍有效，完整手机组合标记BLOCKED并保留原始中断日志；后续远程完整门禁仍必需。

## 原子加载候选验证（2026-10-20）

- 新回归先红：旧控制器在元数据ACK挂起时不发最终音频加载，旧原生引擎load不携带身份。实施后两个真实类的公开命令回归通过，原过期请求、暂停和seek断言保留在API上下文等待处。
- 375应用测试、完整Lint、类型检查通过；build:android、Android Debug/Test构建、68原生单元测试及Lint通过。仅隔离包构建号30088，公开版本仍beta.7/30030。
- 双端完整17/16设备组合执行中。1397064a诊断远程UI仍失败，不将本机或实现状态充当远程通过；没有创建Tag/Release。

- 原子加载候选完整设备结果：MuMu手机17/17（553.288秒）、平板16/16（418.668秒）均通过；额外320dp触摸/快速切歌2/2（36.862秒）通过，尺寸与密度已恢复原配置。平板原生/JS首播110/110ms，身份断言通过；本机结果不替代远程门禁。
- 复审发现并先红后绿修正：API上下文等待期间被取代的crossfade请求曾把新歌曲身份与旧URL绑定；现于等待后立即校验请求令牌，原后置守卫保留。376应用测试、Lint、类型检查通过。包含此守卫的最终隔离包另行重建/设备验证，公开版本不变。

- 最终守卫候选30089：build:android及app模块Debug/Test/68单元测试/Lint成功（0错误、27项既有警告）；376应用测试、Lint、typecheck、75保护脚本测试成功。此前根Gradle命令额外构建Cordova库自身测试遇既有Kotlin重复类；改为项目CI的app任务后通过，未改依赖或删检查。Web重建曾受系统临时文件删除权限阻断，改用已有可写项目临时目录后通过，保留失败日志。
- 最终平板相关8/8通过（45.631秒）；手机相关回归执行中。快速切歌原生/JS分别手机94/107ms、平板82/82ms，仍以原1500ms限值验证。21项Release Notes来源提交前覆盖校验通过，公开文案和版本不变。
- 最终手机相关组合8/8通过（132.560秒）；与平板8/8覆盖HTTP预载/自然衔接/快速切歌、三项TTML加载、信息区滑动与菜单、音量真实触摸。均原位install -r，账号与数据保留。最终本机验证完成，远程UI待提交后运行。

## 隐藏控件真实触摸回归（2026-10-20）

- 032e5d12远程：手机17/17（完整组合）、平板16/16（559.968秒）、小屏16/17（676.481秒）。小屏唯一失败明确为controlsHidden/inert/hidden；三端切歌原1500ms双限值均通过，详细数字见审计记录。旧6f6e687f平板初始化失败保留为UNCONFIRMED触发，不隐去历史失败。
- 320dp受控红：2.6秒静置后直接点分页，实证失败于hidden/inert状态；临时实验源码已逐字节恢复，原失败日志保留。初版唤醒触摸正文导致歌词跳转仍失败，同样保留。
- 最终修正使用空白边距真实触摸唤醒，保留原信息区跟手、分页、菜单和下拉关闭断言；本机320dp1/1（29.685秒）、手机1/1（26.982秒）、平板1/1（15.436秒）通过，共享辅助方法前一候选平板相关8/8（46.261秒）。没有更改应用两秒隐藏行为或原性能门槛。
- Android测试包构建成功。仅修改测试与审计/发布来源记录，应用、正式版本beta.7/30030、签名及资产范围不变。最终远程CI/UI须重新通过，尚未创建Tag或Release。
- 最终六文件候选复审无新增待修改项；Android Lint成功（0错误，既有警告保留），75保护测试、23项来源预检通过。应用源码与032e5d12一致，未变更公开版本、签名、工作流或阈值。

## 自然结束边界回归（2026-10-21）

- 7e33e8e1远程手机17/17、小屏17/17、平板15/16，唯一失败为不循环末曲结束后重播首曲；分页与原性能限值均通过。
- 旧隔离包30089在MuMu平板上运行增强的真实HTTP回归，1/1失败，明确记录final.wav之后播放slow.wav。修复包30090保留数据原位覆盖安装双端，完整组合结果待补。
- 候选应用380项测试、Lint/typecheck、Web构建、Android app Debug/Test构建及原生单元/Lint通过；公开版本保持beta.7/30030，无布局、签名、阈值或依赖修改。

最终候选30090本机完整手机17/17（545.674秒）、平板16/16（415.566秒）通过；快速切歌原生/JS手机62/65ms、平板69/68ms，原1500ms双限值及自然衔接1200ms限值保留。NS-067设备真实HTTP先红后绿及兼容回归VERIFIED，最终新提交远程CI/UI仍待通过。380应用测试、68原生测试、75保护测试、Lint/typecheck和构建通过；24项发布来源预检通过，公开文案/版本/签名/资产规则未变。首次保护测试临时目录权限及沙箱本地克隆失败保留，使用已有可写目录和允许克隆的环境复验75/75。

## 原生短触排队与远程绘制阻塞（2026-10-21）

- cc10593a CI成功（应用380、Android68、保护75；Android Lint零错误、28警告），UI手机16/17、小屏16/17、平板15/16，完整失败记录保留。手机快速切歌原生/JS1650/2020ms，小屏1383/1660ms，均未满足原1500ms双门槛；平板877/1009ms通过并验证NS-067自然结束不回播。发布仍BLOCKED，未创建Tag/Release。
- NS-065追加已复现证据：平板唤醒短触出现pointerdown/touchstart/contextmenu/pointerup/touchend，DOWN到UP事件时间约82ms，但未click。原方法在测试线程生成时间戳，两次同步排入主线程，中间sleep80ms；主线程排队会使短触变成长按。700ms受控排队在MuMu复现同类contextmenu失败，时间戳改在主线程实际派发时生成，DOWN/UP同一主线程turn派发后受控链路1/1通过（55.807秒）。前两次候选验证未到触摸步骤、失败于旧打开方法，记录保留；打开入口现复用同一公共触摸方法。
- 保留原生命中、click监听、分页/菜单/滑动/下拉断言，并新增不允许contextmenu；没有JS click、重试点击或放宽延迟。临时700ms阻塞实验已移除，最终测试包构建和Android Lint成功，双端相关组合待补。
- NS-064仍有远程绘制阻塞：失败窗口中小屏HWUI单帧963ms，手机1189ms，与主线程nSyncAndDrawFrame样本一致；小屏采样25次位于该方法、12次空闲轮询。可确认绘制同步占用主线程，不足以归因到具体Vue组件或音频方法。不通过扩大阈值、改签名或无证据重写播放架构绕过失败，最终仍须同提交三端成功。

参考：Android MotionEvent的downTime/eventTime以uptimeMillis计时（https://developer.android.com/reference/android/view/MotionEvent）。官方支持多种模拟器软件渲染后端（https://developer.android.com/studio/run/emulator-acceleration），本轮未更换后端，也不以文档代替性能根因证据。

最终短触测试修正：手机相关9/9（143.818秒）通过；平板相关6/8（176.082秒），全部触摸/歌词/音量通过，唯一失败为自然衔接1502ms及快速切歌原生/JS1759/1757ms。不能声称双端组合全绿；测试修正的触摸行为VERIFIED，性能门禁仍BLOCKED。为隔离长期图形状态，保存全部证据后仅重启MuMu平板，保留账号和数据，使用同一应用及同一1200/1500ms阈值作对照。

重启对照：同一应用与原门槛，平板HTTP两项1/2（38.632秒）；快速切歌通过，但自然衔接1708ms仍失败，故“仅长期资源累积”不足以解释现象。未因重启后单项成功宣称性能修复，不继续无变化地反复重跑。已确认的短触夹具修正单独提交，原1200ms/1500ms性能门禁及失败记录均保留，新提交须全量CI/UI通过后才能发布。
