# 桌面歌词触碰与 2.5 秒渐隐验收

## Phase

Phase 3 桌面歌词修订，状态 VERIFIED（双端模拟器范围）。本轮只修订桌面歌词，不进入后续页面阶段。

## Root Cause

手机当前运行 top.imsyy.splayer.android.lyricsverify；安装 APK 的 SHA-256 为 7abe2a2b2e25b2348ea5bf4fb80812fcc1c1efb97721013cf42444127b366092，与旧阶段 APK 完全一致。上一轮触碰切换交付在 .phase1verify，没有覆盖用户正在使用的旧包。旧包仅触碰展开，因此再次触碰不会隐藏。

上一轮测试直接向 View 分发事件，无法证明系统窗口输入路由；本轮使用窗口实际 displayId 和屏幕坐标执行 Android input tap，验证实际系统触碰。

## Changes

- 复用已有状态机，普通触碰切换控制显隐；按钮操作执行并刷新计时。
- 无操作 2500ms 后启动现有 240ms 渐隐动画，控件与背景同步完全透明；歌词保留显示。
- 更新当前使用的同名 .lyricsverify 包，原位安装保留应用数据。仪器测试备份并恢复桌面歌词偏好，不清理正式应用数据或改变原有权限。
- 辅助描述同步改为“点击切换控制显隐”。

## Responsive Strategy

桌面歌词继续使用现有手机五按钮、平板六按钮与歌曲信息；字号、归一化位置、安全区域和锁定/独立解锁逻辑不变。平板播放器布局没有修改。

## Validation

- pnpm format、pnpm lint（零错误/警告）、pnpm typecheck、pnpm build:android 通过；11 项相关 TS 测试通过。
- 四 ABI debug APK、仪器包与 Gradle 构建通过；19 项 JVM 测试无失败。
- 手机与平板各两项仪器测试通过，耗时分别为 31.979s、30.370s。覆盖横竖屏布局、实际系统触碰展开/再次触碰隐藏、播放按钮不误隐藏、超时前仍显示、2.5 秒后存在中间透明度帧且最终完全透明、锁定穿透与独立解锁、字号/滚动/暂停/seek、旋转和配置恢复。
- 截图来自窗口所属物理显示面，已目视检查手机与平板控制展开、触碰隐藏、超时透明。测试进程已停止，原有桌面歌词偏好已恢复。
- 交付包 top.imsyy.splayer.android.lyricsverify，3.0.11-debug；文件 SPlayer-desktop-touch-2500-arm64-debug.apk，64,277,767 字节，ZIP 3715 项；SHA-256 cbf582e50e8d4569911b3e021c593488130cfa5183d53437058d366ecdbe1e23。

## Screenshots

证据目录：C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/desktop-touch-2500。

- phone-controls.png / phone-touch-hidden.png / phone-timeout.png：手机触碰展开、隐藏和超时。
- tablet-controls.png / tablet-touch-hidden.png / tablet-timeout.png：平板对应效果。
- phone-locked.png / tablet-locked.png：锁定纯歌词。
- phone-instrumentation.log / tablet-instrumentation.log / build-android.log / gradle.log：测试与构建证据。

## Known Issues

Android Lint 历史遗留 187 errors / 27 warnings，零错误目标仍 BLOCKED；现有 Vite/Gradle 构建提示保留。真机尚未实际验证。锁定时普通触碰按原设计穿透，需要独立解锁按钮恢复交互。

## Approval Required

当前效果是否通过？按用户要求，等待同意后才同步 dev 仓库、通过现有工作流发布下一版本。本轮仅本地提交，没有 push、打标签或触发工作流。
