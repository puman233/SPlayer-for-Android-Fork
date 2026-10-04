# 手机横屏参考布局与两秒隐藏

本轮先通过 `b802f47d` 回退上一轮 `60f5f936`，再按用户的新范围实施：仅手机横屏调整布局，平板保持原来的 composition。竖屏与桌面歌词不在修改范围。

## 布局与交互

- 左侧封面、标题、紧凑歌手/专辑信息与标签；右侧歌词、翻译、罗马音及上下文。
- 底部中央播放/进度，两侧独立操作组；没有整块底部控制背景。
- 初次进入显示控件，2 秒无操作后隐藏。点击空白恢复并重新计时。
- 隐藏后保持布局尺寸，通过 visibility、pointer-events 与 inert 防止误触。
- 拖动、顶部歌词工具、更多菜单和队列开启期间保持显示；结束后重新计时。
- 字号保持用户设置，封面根据实际剩余空间计算。超长信息可滚动阅读。
- 共享组件新增的布局参数默认关闭；新的弹层 hold 仅由手机横屏提供。平板保留原分支、样式和触发方式。

## 实际验证

- MuMu 手机：1080×1920、density 480，横屏 WebView 640×360 CSS px。
- MuMu 平板：1080×1920、density 280，横屏 WebView 1098×618 CSS px。
- `PhoneLandscapePlayerTest`：真实 MotionEvent 展开/seek/保持，窗口 PixelCopy 截图，两秒隐藏、顶部工具、长文本、100/130% WebView 字体、DefaultLyric/AMLL/无歌词及旋转状态。
- 平板路径确认没有 `.full-player-mobile-landscape`，保留 `.player-content .content-left`。
- `PlayerDeviceLayoutTest`：两端横竖屏往返及布局边界回归。
- 23 项 TS 回归通过；ESLint、Vue 类型检查、Android Web 构建及四 ABI debug APK 构建通过。
- JVM 缓存报告 18 项无失败。Android Lint 任务完成，但历史 187 errors / 27 warnings 仍存在，不计为零错误通过。

## 复现

```powershell
pnpm format
pnpm lint
pnpm typecheck:web
pnpm exec tsx --test src/composables/useMobilePlayerControls.test.ts src/core/layout/viewport.test.ts src/core/player/androidLyricScheduler.test.ts src/core/player/floatingLyricSettings.test.ts
pnpm build:android
.\android\gradlew.bat -p android --no-daemon '-PverificationSuffix=.phase1verify' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb -s DEVICE install -r android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb -s DEVICE install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -e label phone -e class top.imsyy.splayer.android.playback.PhoneLandscapePlayerTest,top.imsyy.splayer.android.playback.PlayerDeviceLayoutTest top.imsyy.splayer.android.phase1verify.test/androidx.test.runner.AndroidJUnitRunner
```

平板使用 `label tablet`。测试限制在独立包，不覆盖正式应用或清除其数据；临时 WAV 在 finally 中删除，系统字体和显示密度不改动。

证据位于 `C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/phone-only`。之前 `final-phone`、`final-tablet` 是已回退方案的截图，不作为本轮效果。

## 验证边界

MainActivity 原有 WebView textZoom 固定 100%，显式 130% 压力测试不代表系统字体已自动跟随。真实硬件 cutout、折叠屏、Android 10 与在线账号功能未在本轮扩展验收。当前停止等待用户视觉确认，不进入下一页面、不 push、不 Release。
