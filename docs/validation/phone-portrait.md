# Phase 2：手机竖屏播放器

## Phase / Root Cause

Phase 1 手机横屏已获用户确认。本阶段只调整硬件手机的竖屏信息页，平板使用原布局。
共享 PlayerData 原先把标签和操作放在歌手、专辑之前；标签和四个操作按钮在窄屏换行，无法保持参考图中的同一横排。
真实触摸验收还发现，上一阶段新增 controlsVisible 可选布尔参数省略时被 Vue 转为 false，误使竖屏操作行 inert。本阶段显式采用 undefined 默认值，仅实际传入 false 时禁用隐藏控件。

## Changes / Responsive Strategy

- 手机竖屏顺序：封面、标题、歌手、专辑、标签＋操作、进度、播放控制。
- 可选 phonePortrait 参数默认关闭，只由手机竖屏传入。平板继续使用原信息顺序和操作尺寸。
- 根据实际标签和容器宽度预算四个 48px 操作热区；空间不足时保留收藏、更多，将添加到歌单和队列放入现有更多菜单。
- 字号不缩小，封面继续按剩余空间计算；长标题使用现有滚动组件，长信息沿用局部滚动。
- 复用现有播放器、标签切换、收藏、添加歌单、队列和快捷操作逻辑。
- 两端竖屏均验证操作行不会被误标记 inert；手机真实点击更多及收纳后的队列入口。
- 收纳菜单滚动区为新增入口预留高度，窗口截图自检及顶部/底部断言确认不裁切。

## Validation / Screenshots

状态：VERIFIED（以下实际验收范围），等待用户视觉确认。

- 手机 MuMu SM_A5560：1080×1920、density 480，竖屏 WebView 360×640 CSS px。
- 平板 MuMu ALT-AL10：1080×1920、density 280。横竖屏均保留原 composition。
- 手机与平板各 2 项仪器测试通过，分别 55.822 秒、15.632 秒；真实 MotionEvent 点击更多和队列，PixelCopy 截取应用窗口。
- 验证标题、歌手、专辑、标签顺序，同排中心线、不重叠、48px 热区、菜单边界、两端竖屏可交互、100/130% WebView 字体及长文本。
- 手机横屏两秒隐藏、弹层保持、DefaultLyric/AMLL/无歌词及旋转播放状态回归通过；原生音频真实 seek 到 38381ms。
- pnpm format、pnpm lint（零错误/警告）、pnpm typecheck:web、pnpm build:android 通过；23 项 TS 回归通过。四 ABI debug 与仪器包构建通过，18 项 JVM 缓存报告无失败。
- 最终 arm64 APK 64,743,944 字节，ZIP 3715 条目，确认包含 portrait-compact-panel 资源。独立包 top.imsyy.splayer.android.phase1verify，未覆盖正式应用数据。

截图、日志、几何坐标和安装包：`C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/phone-portrait`。
主截图为 phone-portrait.png、phone-portrait-more.png、phone-portrait-1.3-return.png、tablet-portrait.png 与 tablet-landscape-original.png。
测试沿用 docs/validation/phone-landscape.md 中的隔离包复现步骤，PhoneLandscapePlayerTest 已扩展竖屏断言。

APK：SPlayer-phone-portrait-arm64-debug.apk。
SHA-256：5e8cd73babe6b4ec2a04c6d47ba5d7647876efbff0264c6fffdd786d49f0e813。

## Known Issues

产品 WebView 沿用固定 100% textZoom；130% 字体验收通过测试包临时设置实现，不代表产品已支持系统字体缩放。
Android Lint 任务完成，但历史 187 errors / 27 warnings 仍存在，零错误验收为 BLOCKED。Vite 原有构建提示保留。
截图使用隔离测试曲目和默认封面；本轮未扩展在线账号功能、真实硬件 cutout 或折叠屏验收。

## Approval Required

完成本阶段测试和本地提交后停止，等待用户确认，不自动推进下一阶段或发布。
