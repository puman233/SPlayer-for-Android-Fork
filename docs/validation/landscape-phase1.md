# 横屏播放器 Phase 1 验证

## 设计与范围

横屏内容区域与临时控制层独立。内容区为约 36% / 64% 两栏，左侧封面只使用扣除歌曲信息后的剩余高度，右侧继续使用现有 DefaultLyric 或 AMLL。顶部保留收起入口；音质、来源与歌词工具通过 Teleport 移入控制层。

本阶段只改变横屏 composition 和必需的共享接口。竖屏、首页、MiniPlayer、卡片、原生播放/MediaSession 与桌面歌词服务没有单独重构。

## 本地检查

```powershell
pnpm format
pnpm lint
pnpm typecheck:web
pnpm exec tsx --test src/composables/useMobilePlayerControls.test.ts src/core/layout/adaptiveWindow.test.ts src/core/layout/viewport.test.ts src/core/player/androidLyricScheduler.test.ts src/core/player/floatingLyricSettings.test.ts
pnpm build:android
.\android\gradlew.bat -p android --no-daemon '-PverificationSuffix=.phase1verify' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

PowerShell 中 Gradle 的 `-PverificationSuffix=.phase1verify` 必须作为一个完整参数传递。

## 双端实际 WebView 验收

对两台配置分别安装对应 ABI 的 debug APK 与 `app-debug-androidTest.apk`，然后运行：

```powershell
adb -s <serial> shell am instrument -w -e label phone -e class top.imsyy.splayer.android.playback.LandscapePlayerTest top.imsyy.splayer.android.phase1verify.test/androidx.test.runner.AndroidJUnitRunner
adb -s <serial> pull /sdcard/Android/data/top.imsyy.splayer.android.phase1verify/files/phase1 <evidence-directory>
```

第二台使用 `-e label tablet`。仪器测试强制检查 `.phase1verify` 包名，不能对正式应用运行。测试使用固定歌曲信息、歌词和私有目录静音 WAV，不依赖账号或在线音源；结束时停止音频并删除 WAV。

覆盖内容：

- 实际竖屏 → 横屏 → 竖屏 → 横屏切换，保留歌曲与循环/随机状态。
- 初始 Idle、原生 MotionEvent 点击恢复、超时隐藏，以及隐藏后的 `inert` 和 `display:none`。
- 控件展开前后内容尺寸不变，正方形封面、元信息与歌词边界不重叠；控制层和播放按钮在可用窗口内。
- 快捷菜单打开超过隐藏时限仍保持显示。
- 原生 MotionEvent 拖动进度超过 3 秒保持显示，松手后恢复计时；读取原生 `positionMs` 验证真实 seek。
- WebView textZoom 100% / 130%，长标题、多歌手、长专辑，以及 DefaultLyric、AMLL 和无歌词状态。

多显示面模拟器使用目标应用 Window 的 PixelCopy 截图与目标 WebView 的 MotionEvent 输入。截图取自实际 Android 应用渲染，不是浏览器模拟尺寸或设计图。

## 验证边界

当前 MainActivity 固定 WebView textZoom 为 100%，因此本阶段大字压力测试显式设置 textZoom 为 130%，结束后恢复 100%。这不代表系统字体缩放已被产品自动采用。真实硬件 cutout、折叠屏与 Android 10 不在本机模拟器覆盖范围内。

Android Lint 任务退出成功仍须检查 XML 中的错误/警告数量；已有原生报告问题不等于 ESLint 失败，也不能作为全项目 Lint 零错误通过的证据。当前结果、截图路径和实际设备配置见 AGENT_CHECKPOINT.md。
