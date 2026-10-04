# Phase 3 桌面歌词

按用户“继续”进入原任务已安排的桌面歌词阶段；不修改播放器、首页或卡片。本阶段结束后本地提交并停止等待视觉验收。

现状：已有 IDLE / CONTROLS_VISIBLE / DRAGGING / LOCKED 状态、四秒隐藏、独立解锁入口、AUTO_DEFAULT / USER_DEFINED 字号、长句横向滚动和归一化窗口位置。

根因：文字背景遮罩绘制与 controlsAlpha 无关，启用后待机和锁定仍保留整块背景，与纯歌词目标不符。

计划：复用现有动画，让文字遮罩与控制背景同步淡出；不改变保存的背景、字号、颜色或位置设置。补充实际渲染透明像素、触摸展开、拖动保持、锁定不可移动与解锁、旋转安全边界测试，在隔离包中双端运行并保存真实窗口截图。

验证：最小 JVM 策略测试、原生仪器测试，格式化、lint、前后端类型检查、Android 构建；回归现有播放器行为。使用隔离验证包，不清除正式应用数据，测试结束恢复临时设置。

状态：VERIFIED（以下范围），等待用户视觉验收。

实现：遮罩使用保存颜色的 alpha × controlsAlpha；控制背景仍使用同一 controlsAlpha。待机和锁定时完全不绘制背景，不改保存的配置值。手机五钮、平板原有六钮与标题栏保持，播放器布局没有修改。

验证结果：pnpm format、lint（零错误/警告）、前后端类型检查、build:android 成功；11 项相关 TS 测试通过；完整四 ABI debug、仪器包、JVM 测试与 Android Lint 任务完成，18 项 JVM 报告无失败。Android Lint 零错误目标仍 BLOCKED：历史 187 errors / 27 warnings。

手机 MuMu（density 480）与平板 MuMu（density 280）最终各两项仪器测试通过，耗时 31.529s / 31.575s。验证开启遮罩后的待机透明、触摸展开背景可见、四秒隐藏后透明、锁定背景透明和触摸状态/位置不变、独立按钮实际点击解锁，以及中英日文/emoji/RTL 长句字号稳定、滚动与暂停/seek、USER_DEFINED 30sp/颜色跨旋转和服务重建保留。播放器实际旋转可达性回归通过。

截图自检发现默认截图 API 在平板取到模拟器另一显示面；已改为按悬浮窗实际 displayId 匹配物理显示面并采集系统窗口，重跑通过。最终双端窗口截图已自检，不采用 Mockup 或仅凭 View 绘制图作为最终展示。

新建隔离验证包 `top.imsyy.splayer.android.lyricsverify`，仅清理此包的歌词测试偏好。为测试授予该包悬浮窗权限，结束后恢复 default 并停止测试包；正式包的数据、权限不变。旋转设置在 finally 恢复。未修改版本号，未 push、未发布。

证据目录：`C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/desktop-lyrics`。最终 arm64 验收包 `SPlayer-desktop-lyrics-arm64-debug.apk` 为 64,277,591 字节，3715 个 ZIP 条目，原生 Dex 和内置 Node 资源已检查。SHA-256：`7abe2a2b2e25b2348ea5bf4fb80812fcc1c1efb97721013cf42444127b366092`。

边界：双端 API 35 模拟器验证，不代表新增真实硬件/Android 10/折叠屏验证；复杂背景上的纯歌词对比度仍取决于保存的文字颜色/阴影。没有新增罗马音行，本阶段沿用当前行与翻译/下一行的既有能力。四秒桌面歌词计时沿用原交互，手机播放器两秒逻辑保持。
