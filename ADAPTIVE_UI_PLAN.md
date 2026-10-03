# v3.0.11 自适应 UI 修补方案

日期：2026-10-03。目标分支：dev。当前阶段：本轮同版本修补已交付；完整自适应工程继续分阶段推进。

方案已获得用户批准。基础层、播放器/歌词入口与共享列表阶段已提交并执行 MuMu 双端验证。本轮保持版本 3.0.11，已通过工作流更新现有 v3.0.11 APK，并将交付记录同步仓库，不创建新版本。完整验收范围及未验证项见 checkpoint。

## 仓库分析

- 当前 Android 仓库为 Vue 3、Pinia、Naive UI、Capacitor；播放页面是 WebView，非 Compose。原生桌面歌词是 Android Service 与自绘 View。使用现有组件、SvgIcon 和插件边界，不引入 UI 框架。
- `src/composables/useDevice.ts` 已有模块级单例及 visualViewport 监听，`src/core/layout/viewport.ts` 已提供可用尺寸与缩放计算，应扩展而非再建独立监听系统。
- `FullPlayer.vue` 分派竖屏、手机横屏、平板页面。现有 `FullPlayerMobileLandscape.vue` 已使用真实剩余区域、局部滚动和共享控制组件；已有实现及设备测试应保留。
- 桌面歌词已拆分为 `FloatingLyricService.java`、`FloatingLyricPolicy.java`、`FloatingLyricInteraction.java`、`FloatingLyricTextLayout.java`。已有长句滚动、锁定、位置迁移和设置兼容，本轮聚焦入口、真实状态和新增响应式回归。
- `PlayerController.setDesktopLyricShow()` 已提供权限检查、设置引导、等待应用恢复与自动继续开启。UI 通过此接口调用原生能力，不能直接操作 WindowManager。

## 参考实现与可复用边界

- 原 SPlayer：`src/components/Player/PlayerRightMenu.vue` 使用 `DesktopLyric2` 与 ON Badge 表示独立入口；`src/core/player/PlayerController.ts` 统一控制 IPC。保留图标语义和控制接口，但桌面端的固定栏位与窄屏隐藏策略不能照搬 Android。
- 原 SPlayer：`MainPlayer.vue` 的歌曲信息、播放与操作分区，以及 `SongList.vue` 的共享列表入口，可帮助保留现有交互层级；其固定三列布局不能成为分屏适配标准。
- SPlayer Next：`src/components/player/PlayerControls.vue` 集中管理播放核心控件，`compact` 是组件显式输入；借鉴组件职责与状态复用，继续使用本仓库 Naive UI 和 SvgIcon。
- SPlayer Next：`src/settings/categories/externalLyric.ts` 通过设置绑定管理桌面歌词字体、对齐、翻译与锁定；借鉴统一配置入口，保留 Android 现有设置和服务数据流。`useFloatingPlayerBar.ts` 的固定底部留白不适合作为本轮内容测量算法。

## 根因地图

下表记录实施前的实际代码约束；除明确入口隐藏外，视觉症状仍须设备复现，不能视为已验收。

| 问题                               | 组件与证据                                                                                                                             | 修补方向                                                                          | 回归风险                                  |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------- |
| 平板分屏或窗口缩小仍保留不合适布局 | useDevice 的 isPad = isPadDevice && isLandscape；硬件识别包含 UA 和物理 screen 短边                                                    | 保留硬件识别用于方向设置，布局由实际容器宽高及内容预算决定                        | 用户强制 phone/pad 设置与方向锁兼容       |
| 断点语义不统一                     | useMobile 并存 512/640/768/990/1024/1280，useDevice 使用 600 与朝向                                                                    | 共用窗口信息与尺寸等级，逐步迁移本轮四个重点模块                                  | 壳层与播放器判型不一致                    |
| 桌面歌词入口缺失                   | PlayerRightMenu 中 Badge 和按钮带 hidden，810px 以下非 persistent 模式 display:none；手机依赖快捷菜单                                  | 共享独立 Naive UI 按钮，紧凑显示图标、宽裕显示标签，不藏进更多菜单                | 所有播放模式、布局与设置开关              |
| 操作栏内容裁切与拥挤               | PlayerControl 固定 80px、overflow:hidden、repeat(3,1fr)；MainPlayer 固定栏高及三列；MobilePlayerBottomControls 以 flex-shrink 压缩按钮 | 内容决定栏高、minmax(0,1fr)、48 CSS px 最小触控、次要操作重排或明确 overflow 入口 | 进度拖动、播放点击和自动隐藏              |
| 系统大字验收可能没有放大网页文字   | MainActivity.java 中 setTextZoom(100)                                                                                                  | 明确桥接系统字体缩放与主题文字 token；验证真实 computed font-size 随配置改变      | 原生 sp 与网页 px 双重缩放                |
| 列表文字和标签空间竞争             | SongList 有固定头部与 72px 行高，歌曲实际渲染组件需继续定点分析                                                                        | 标题弹性区域、metadata 独立行、菜单固定触控下限；虚拟列表测量与行高同步           | 虚拟滚动错位与性能                        |
| 桌面歌词按钮显示状态可能陈旧       | statusStore 持久化 showDesktopLyric；调用成功后写状态；关闭失败仍显示成功提示                                                          | 从原生服务取得真实状态，进入前台和服务变化时校准，串行处理开关                    | 服务重启、权限撤销、快速连点              |
| 同版本修补工作流不能直接发布新源码 | release.yml 检出旧 Tag，公开 Release 则跳过构建                                                                                        | 独立显式修补入口：指定源码提交、验证版本与签名、四 ABI 全部校验后更新现有 Release | 附件部分更新、源码与 Tag 不一致、恢复失败 |

## 实施顺序与架构

每个逻辑阶段完成验证、提交与 checkpoint 后停止，遵守仓库持久执行规则。

1. 首个实现阶段：扩展现有 viewport/useDevice 为共享窗口信息，提供宽高等级、安全区、系统字体缩放和有限尺寸/文字 token；新增纯布局策略测试。布局与硬件身份分离，保留手动模式的兼容语义。
2. 播放器阶段：迁移竖屏、横屏、平板的布局分派与内容约束；以容器预算保持封面、信息、进度及核心操作可见，高度不足时局部滚动；不改音频生命周期。
3. 桌面歌词入口阶段：Naive UI 共享按钮、统一 DesktopLyric2 图标、OFF/ON/PERMISSION_REQUIRED 状态、忙碌保护；复用权限自动继续流程，并补服务状态同步。
4. 共享组件阶段：Naive UI 控件封装、Chip、MiniPlayer、SongItem；优先移除限制内容的高度/宽度，保留语义尺寸、触控下限与性能预算。
5. 回归与修补发布阶段：文档、完整验证、提交推送与远端 CI；工作流从明确源码提交签名生成四 ABI，更新 v3.0.11 附件。保持现有 Tag，不强推；在 Release 正文记录修补源码提交、时间和哈希，使旧 Tag 与修补 APK 的来源可追溯。先准备完整可校验产物再更新附件，保留恢复材料。

## 验证计划与现状

- VERIFIED：开始时 dev 与 origin/dev 同步、产品工作区干净；GitHub Latest 为公开 v3.0.11；两台 MuMu 在线，emulator-5554 为手机、emulator-5556 为平板；127.0.0.1:16416 是平板别名。
- 基础层已执行构建、格式化、类型和相关回归，详见下方当前交付；以下完整验收矩阵仍包含后续阶段待完成项。
- 双端真实 APK：纵横屏、窄窗口/分屏、手势/三键导航、模拟 cutout、fontScale 1.0/1.15/1.3/1.5/2.0；验收记录实际 CSS 窗口及网页文字尺寸，避免只有系统值改变。
- 内容场景：长标题、多歌手/专辑、中英日按钮文字、标签、播放/进度/队列/收藏/更多、普通与唱片封面、Default/AMLL 歌词、权限拒绝/授权自动继续、快速开关、服务重启、后台播放与旋转状态保持。
- 几何不变量：图标文字不重叠，核心操作和菜单在安全区，触控不低于 48 CSS px，行测量匹配虚拟列表，悬浮歌词实例不因旋转重复创建。
- 测试使用隔离包与测试素材，不清除正式应用数据；每组核实实际尺寸、配置与结果，结束恢复模拟器设置并清理临时包。
- 历史 checkpoint 的设备矩阵不能替代新改动验收。历史 Android Lint 187 errors / 27 warnings 仍需如实报告；API 29 与物理 cutout 未验证，不虚构 VERIFIED。

## 当前交付

基础层新增 `src/core/layout/adaptive.ts` 和策略测试、`useAdaptiveLayout.ts` 容器测量接口；扩展 useDevice 单例与 App 的语义 CSS token。自动双栏要求窗口宽至少 840 CSS px、高至少 480 CSS px；强制平板允许 600 CSS px 宽度，但仍受高度约束；强制手机保留单栏。未重写其他 useMobile 调用点，也未迁移所有页面 token。

MainActivity 根据系统 fontScale 设置 WebView textZoom，配置变更保留 Activity；原生插件提供初始查询、配置变化与前台恢复事件，App 同步单例并在卸载时移除监听。网页 token 不再次乘字号系数。已有安全区变量继续沿用，usePageZoom 的历史安全区兜底与页面缩放预算需在播放器阶段继续检查。

已执行：28 项 TS 回归、15 项 Python 发布保护、18 项 JVM 测试、Vue 类型检查、pnpm build:android、pnpm format、ESLint、原生 APK 与仪器包编译。MuMu 双端完整仪器套件各 4 项通过，包含五档字体的真实文字测量、可变 Activity 窗口、播放页真实旋转与悬浮歌词回归。Android Lint 任务完成，但 XML 仍有历史 187 errors / 27 warnings。最终测量证据与提交详情记录在 AGENT_CHECKPOINT.md。

本阶段不代表播放器/列表所有大字排版已通过验收，也不代表完整 split-screen 生命周期已验证；可变 Activity 窗口验证不等同于系统分屏操作验收。基础阶段结束时版本、Tag 与 Release 附件未改变；后续已按用户授权更新 3.0.11 APK，不创建新版本，并同步仓库。最终验收见下方交付记录。

## 播放器与独立入口阶段

播放器顶栏、内容区、底栏按实际高度排布；宽屏封面从剩余容器测量尺寸，信息区允许局部滚动。手机竖屏按容器宽度选择常规或较大控件，底栏不再用固定高度裁切文字。控制栏在窄空间或大字号下重排；核心触控下限为 48 CSS px，SVG 图标尺寸独立约束。

共享 DesktopLyricsButton 使用 Naive UI 与现有 DesktopLyric2 图标，手机和平板播放页均有独立入口。Android 以实际悬浮窗口与权限确定 OFF/ON/PERMISSION_REQUIRED，窗口尚未就绪或切换中禁用按钮；先注册前台恢复事件再跳转设置，返回后继续开启。原生服务附着、停止与页面恢复均校准状态，不改音频引擎生命周期。

双端已执行五档真实系统字号 × 两个方向 × cover/record/fullscreen 共 60 组长标题、中英日、emoji 内容几何检查，以及原生开关、外部服务停止、缺权限、实际系统授权页返回自动继续。详细最终产物与测试范围见 checkpoint。共享列表、MiniPlayer、模拟导航/cutout 补充回归和 v3.0.11 APK 替换已在后续阶段完成；AMLL 全内容矩阵、物理 cutout 和真实系统分屏仍未完成完整验收。

## 共享控件与列表阶段

新增 AdaptiveButton、AdaptiveTag，沿用所接入组件的 Naive UI props、事件及默认/图标插槽，图标尺寸与文字缩放分开，交互触控下限为 48 CSS px。已接入 MainPlayer、SongCard 与播放队列；未替换所有页面的 Naive UI 调用。

MainPlayer 按容器宽度与字号重排，紧凑布局保留前后曲和播放，随机/循环进入明确的更多菜单。歌词行自然增高，测量实际播放栏高度供 AppLayout 预留空间，不再根据硬件身份决定控制按钮。歌曲标签与歌手分行，表头、队列头尾和歌曲行允许自然增高，队列依据可用窗口分级选择布局。

SongList 与 SongPlayList 使用现有 VirtualScroll 动态测量，只观察已渲染行；按 DOM 的真实 data-index 映射测量，避免 keyed ref 数组顺序变化导致错配。累积位置的 shallowRef 明确触发更新，字号改变重置未测量行估计并保持当前行锚点。滚动、跳转和拖拽仍复用同一实际位置表。

动态行定位取消位置过渡动画，避免连续测量期间出现视觉重叠；旧滚动偏移超过新列表末尾时钳制到最后一行，不能回退首行并渲染整个列表。迷你播放栏在宽度足够的矮窗口改为单行；队列数量进入流式排布，通过 Naive UI 徽标值插槽显示完整数量，避免固定数字动画行盒的大字裁切和重叠。

最终测试证据、产物和剩余验收范围记录于 checkpoint；已按用户授权替换 v3.0.11 四 ABI APK，不创建新版本。

## 同版本修补工作流

`Android Same-Version Patch` 分 prepare/apply：先生成同签名、同包名、同版本号和 code 的四 ABI 候选包，备份旧包并保存不可变 artifact；双端检查后再更新既有 Release。替换前后核对 Tag、Release 身份、源码 CI、附件 digest 和更新说明。中断恢复、回滚及保留期见 [同版本修补说明](.github/ANDROID_SAME_VERSION_PATCH.md)。不要求增加版本号或移动 Tag。

## 本轮正式交付（2026-10-04）

源码 `99c2c3ce3290668ca4d7123e71f551590d65e4de` 的 Android CI、签名准备运行 37135681053 和实际替换运行 37137618497 均成功。既有 v3.0.11 Release 四 ABI 附件已更新；包名、3.0.11 / 30017、证书、Release 身份和 Tag 保持一致。正式 ARM64 APK 已在 MuMu 手机、平板完成保留数据的覆盖升级和启动检查。

补充四套完整仪器测试各 7 项通过，覆盖双端手势/三键导航及模拟 cutout、五档实际字号、横竖屏、播放器和共享列表。API 29、物理 cutout、真实系统分屏、AMLL 全矩阵、物理拖拽及全部页面大字号仍未完整验证，不扩大 VERIFIED 范围。正式 APK、SHA-256、运行链接和证据目录见 [检查点](AGENT_CHECKPOINT.md)。
