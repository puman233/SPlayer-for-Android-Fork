# 桌面歌词与横屏播放器专项重构方案

日期：2026-10-03。目标仓库：SPlayer-for-Android-Fork；分支：dev。
状态：IMPLEMENTED（调查与方案文档）；功能尚未修改，尚未验证。

## 调查范围

已阅读父目录任务与检查点、仓库 AGENTS.md 与检查点，并检查目标仓库 Git 状态：dev，初始工作区干净。
本次以附件专项需求为范围，不重新执行父目录已完成的桌面版 Phase 0–7。
技术栈为 Vue 3、Naive UI、Capacitor 与原生 Java Service/Canvas，并非 Compose。

已定位完整入口链路：PlayerRightMenu/PlayerQuickActionsMenu → PlayerController → AndroidNativePlaybackPlugin → PlaybackManager → FloatingLyricService。
歌词与进度沿用 LyricManager/PlayerController 的现有推送；PlaybackManager 缓冲后在服务注册时回放。
MainActivity 在清单中自行处理 orientation/screenSize 等配置变化；FloatingLyricService 未实现配置变化后的布局恢复。

## 已确认的根因

- 字号：原生默认 16sp，前端默认 24；没有自动/用户模式。computeAdaptiveFontSize 随当前行是否有翻译/下一行调整高度约束，drawFittedText 与 paintWordLyric 再按文本宽度缩小到最低 55%，造成句间跳变。
- 颜色：前端默认 #fe7971，原生默认 0xFFFE7971，缺少跨桥接一致的默认策略。
- 设置：设置页面与 PlayerController 各自读取相同 localStorage 键并拼装桥接参数，原生另存恢复快照。首次无配置时 PlayerController 不推送配置，行为取决于是否打开过设置页。
- 长歌词：普通行每帧 measureText；逐字行每帧分配宽度数组、重复测量并缩小。没有溢出滚动缓存与重置机制。
- 锁定背景：ACTION_UP 在 onBtnTap 后无条件执行 showCtrls = !showCtrls；锁定动作刚把它置为 false，随即又变为 true。背景判断优先检查 showCtrls，导致 Locked 仍画控制背景。
- 交互：locked/showCtrls/dragging 分散管理；隐藏直接切换布尔值，无淡出；removeCallbacksAndMessages(null) 范围过宽，销毁未显式清理计时。手机交互态只绘制控制栏，不绘制歌词。
- 图标与触控：已使用 lyric_lock/lyric_unlock 资源，但路径形态、解锁按钮比例与控制按钮比例不同；独立解锁窗口仅 32dp，控制区 34dp。
- 坐标：prefs 保存 x/y 绝对 px，窗口与拖动只按 displayMetrics 边界钳制，未统一扣除系统栏/cutout，也没有旋转重投影。解锁窗口独立定位，没有共享尺寸变化后的定位流程。
- 横屏：走专用内容组件，却借用桌面绝对定位顶/底栏；内容直接减去固定 116px；封面靠固定百分比与 translateX；歌词菜单明确 display:none，placeholder 固定 80px。隐藏与恢复仍受父页面 playerMetaShow 逻辑控制，需统一触摸可达性。
- 生命周期：WindowManager 多处空 catch，addView 失败后仍可能继续按已附着窗口操作；需要显式附着状态与错误日志。

## 实施范围与文件

### 原生桌面歌词

- 重构 android/app/src/main/java/top/imsyy/splayer/android/playback/FloatingLyricService.java，只管理窗口生命周期、数据接入、触摸和绘制调度。
- 同包按职责提取 FloatingLyricInteractionController、FloatingLyricLayoutPolicy、FloatingLyricMarquee 等小组件；纯计算部分不依赖 Android，便于 JUnit 验证。
- 复用现有 View/Canvas 和 drawable，不引入新 UI 库，不重写播放服务。
- android/app/src/main/res/drawable/lyric_lock.xml、lyric_unlock.xml 仅在确需统一时使用项目已有同族资源调整。
- AndroidNativePlaybackPlugin/PlaybackManager 仅按需要扩展配置字段；保留既有播放、歌词时间与通知动作语义。

### 配置

- src/assets/data/lyricConfig.ts、src/types/desktop-lyric.d.ts：统一默认颜色、显式 AUTO_DEFAULT/USER_DEFINED 与配置版本。
- 新增共享桌面歌词设置模块，封装现有 android-desktop-lyric-config 键的读取、校验、迁移、保存及桥接 payload。
- src/components/Setting/config/lyric.ts、src/core/player/PlayerController.ts、src/plugins/androidNativePlayback.ts 使用同一模块；排查 FontManager/预览等相关读写，不引入另一配置键。
- 设置页复用 Naive UI 增加自动字号/恢复自动入口，滑块修改明确转 USER_DEFINED。

### 播放页

- src/components/Player/FullPlayer.vue、FullPlayerMobileLandscape.vue：建立安全区内顶栏/内容/底栏的真实流式布局，消除固定高度扣减和负向位置补丁。
- 按可用宽高与比例组织双栏，封面由剩余高度与元信息约束；内容允许局部滚动。复用 PlayerCover、PlayerData、PlayerLyric、PlayerSlider、现有菜单与播放动作。
- PlayerMenu/PlayerControl/PlayerRightMenu/歌词菜单仅作必要的响应式组合调整，核心按钮不能因为空间不足消失。
- 复用 useMobilePlayerControls 的交互保护；根据最终组件组织扩展其横屏使用，检查旋转切换清理。
- useDevice/core/layout/viewport 仅在现有可用视口策略不足时小范围扩展，不按设备型号或截图分辨率分支。

## 状态与动画设计

统一状态 Idle → ControlsVisible → Dragging → ControlsVisible；ControlsVisible 超时 → Idle；点击锁定 → Locked；独立解锁入口 → ControlsVisible。
状态控制器拥有唯一隐藏任务，交互重置、锁定与销毁都会取消它。背景与按钮共用可取消的透明度动画；锁定先短反馈，再快速淡出，不反转新状态。
Locked 主窗口继续触摸穿透，保留独立解锁窗口，普通触摸不会调用按钮或拖动。触控区目标至少 48dp。
歌词层与控制层分区绘制并裁剪；打开控制栏不再替换整块歌词，透明态不保留控制背景。保留用户主动启用的文字遮罩设置。

## 字号与滚动设计

自动字号只在安全区域、窗口尺寸、密度、fontScale、布局或样式变化时计算，综合宽度和多行高度预算并限幅；不依赖当前文本或当前行是否有翻译。
用户字号按 sp 尊重保存值，字体放大导致空间不足时调整窗口和内容布局，不偷偷缩小用户字号。
缓存文本布局与逐字宽度；缓存键包含歌词数据版本、行、文本、字体、字号、可用宽度与方向。帧内只更新偏移和高亮进度。
短句静止；长句从标准起点停留后在裁剪区滚动，LTR offset 为负；RTL 使用方向一致的布局。
滚动时间由溢出距离/目标速度限幅，再结合当前行有效时间预算。极短句时优先可读性，记录无法完整展示的验收边界，不能无限加速。
切歌、歌词替换、seek、样式变化重置滚动；pause 冻结、resume 继续；旧动画不会与新行竞争。不修改歌词 timing 数据。

## 位置与尺寸设计

以安全可移动区域内的 overlayTopLeft 为统一锚点，保存 normalizedX/normalizedY，分母为扣除 overlay 宽高后的可移动距离；零移动范围有明确定义。
使用当前窗口的 metrics/insets；API 29 路径使用实际窗口 inset 与显示尺寸兼容处理，避免误把系统坐标原点和内容原点混用。
旋转、inset 与尺寸变化重算安全区域，再从归一化锚点投影并钳制；解锁窗口跟随同一定位策略。
旧 x/y 一次性转换后保留兼容信息，不清空用户设置。旧坐标没有历史屏幕尺寸，无法精确还原原始比例，迁移时按当前区域钳制转换并明确记录限制。
宽度仅随视口/设置变化，歌词长短不改窗口宽度；高度根据固定行预算和控制栏预算调整，并保持锚点稳定。

## 设置兼容与迁移

继续复用 localStorage + 原生 SharedPreferences 恢复快照，不修改数据库 schema。
现有配置若有有效字号但无 mode，保守迁移为 USER_DEFINED，保留数值；历史配置自动写入默认值的情况无法可靠识别，不以是否等于 16/24 判断。用户可主动恢复自动。
现有有效颜色一律保留，包括旧默认红色；无历史颜色才使用 #6BB2FF。
显式配置版本迁移保证幂等，前端为设置来源、原生为离线启动快照；桥接发送模式而不是将自动计算值当作用户值保存。

## 验证与交付

- 先运行设置迁移、交互状态、字体策略、位置投影及 marquee 的相关单元测试，再回归现有 viewport、移动控件与歌词调度测试。
- pnpm format、pnpm lint、pnpm typecheck:web、pnpm build:android；检查全库格式化影响，只保留任务相关变更。
- Android :app:assembleDebug、:app:testDebugUnitTest、:app:lintDebug；读取实际 Lint 报告，不能用任务 exit code 代替零错误结论。检查点已有 205 errors/33 warnings 的历史记录，本次需重新比较，不能声称已通过或静默忽略。
- 检查可用 adb 模拟器后执行隔离测试包验收，不覆盖正式应用或清除用户数据；按需求矩阵测试横竖屏、系统栏、字体缩放、长短/英文/日文/emoji 歌词与重复旋转。缺少设备的场景标明 BLOCKED/未验证。
- 补 README、CHANGELOG 与专项检查点，保留实际命令结果、APK 路径、已验证范围与剩余风险。
- 本专项作为一个逻辑阶段完成后提交 dev 并停止；不自动推送、打 Tag 或发布正式版本。

## 实施前确认

仓库 AGENTS.md 的“复杂任务先规划”明确要求：“在文档中梳理思路 -> 展示给用户 -> 用户同意 -> 开始写代码”。
本文件为该要求的审阅产物；获得同意后按状态、配置、滚动、交互、定位、横屏、回归的依赖顺序实施。

## 实施与验收记录

用户批准后已完成本方案的代码修改及 debug APK 构建。实现与实际验证范围、失败及修正过程、产物摘要和剩余限制见 AGENT_CHECKPOINT.md 的“桌面歌词与手机横屏专项”章节。默认/手动字号统一持久化，锁定状态无控制背景，长句固定字号平移；手机横屏保留共享菜单和控制功能。

本地源码与相关测试已验证；全项目 Android Lint 仍有历史问题，真机 cutout、Android 10 路径与完整系统导航矩阵尚未验证。安装包为 debug 测试产物；本阶段本地提交后停止，不自动发布。
