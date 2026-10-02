# Android 稳定性改造最终报告

## 交付状态

- 集成分支：`dev`
- 基线：`origin/dev` / `d834171d`
- Debug 交付：`VERIFIED`
- 正式 Release 签名构建与证书核验：`VERIFIED`；递增版本升级安装：`BLOCKED`，本次保持 30014 / 3.0.8

## 已完成范围

- 手机、平板、横竖屏、矮屏、字体缩放和安全区自适应。
- 手机 Cover / Lyrics / 评论分页共用 Pager 外单一播放底栏，切页不再改变控制区坐标。
- Media3 弱网有界重试、三档退避、断点恢复、暂停意图保护和去敏诊断。
- GitHub Releases 稳定通道、API 限流回退、ABI 匹配和包体大小展示。
- APK 下载进度、取消、清理、SHA-256、包名、版本号和签名证书校验。
- 未知来源授权与最终安装确认保留给 Android 系统和用户。

## 最终验证

- `pnpm format`：通过；仅产生的历史文件格式噪声已精确撤销。
- `pnpm typecheck`：通过。
- `pnpm lint`：通过，0 warning。
- TypeScript：8 个 suite、42 项测试全部通过。
- `pnpm build:android`：通过。
- Android：`testDebugUnitTest assembleDebug` 通过，四 ABI 产物完整。
- 手机和平板：x86_64 Debug 包覆盖安装、冷启动和前台 Activity 通过。
- 手机：1080×1920 / 480 dpi 完整播放页无裁切。
- 手机：1080×2400、1080×2520 下 Cover / Lyrics 的时间、Slider、五键控制和分页点位置一致，三键导航安全区无侵入。
- 平板：1920×1080 / 280 dpi 首页、侧栏和播放栏无越界。
- 离线缓存：飞行模式保持开启时从 0 ms 播放至 194037 ms，自然切换下一首。
- 更新安装器：实际 FileProvider URI 进入系统 PackageInstaller，未授予未知来源权限、未确认最终安装。

## Debug APK

当前主项目路径：`C:/Users/ihyj/SPlayer/SPlayer-for-Android-Fork`。

正式签名产物目录：`android/app/build/outputs/apk/release/`，版本 30014 / 3.0.8。

| 文件                          | SHA-256                                                            |
| ----------------------------- | ------------------------------------------------------------------ |
| `app-arm64-v8a-release.apk`   | `5d8e2c3dbd87930d38ba38ef638bbc9c2d8b1df7c75388ec76432a7c0a102126` |
| `app-armeabi-v7a-release.apk` | `042bb2331ab67aa1fc5b3336472e5d64f31ec3fad18944be8bfa6c69e5749723` |
| `app-x86_64-release.apk`      | `d5dd96eb9f4aff33f571e311580c116f49fa263c903cfb0e283a1c774e8f42cd` |
| `app-x86-release.apk`         | `3066b69814568a4f8074b3534f5b7656b7b8c1869d551e8b291a14164265a9f5` |

目录：`android/app/build/outputs/apk/debug/`

| 文件                        |     字节 | SHA-256                                                            |
| --------------------------- | -------: | ------------------------------------------------------------------ |
| `app-arm64-v8a-debug.apk`   | 64256649 | `fadd16e08395a84095c30a1601ab1c0b03c4886a213581d67f8f7471fe87d472` |
| `app-armeabi-v7a-debug.apk` | 63434759 | `0c99c56c91c74d706f30bef2080638af290377a565d887769e9e7538cb6881e0` |
| `app-x86_64-debug.apk`      | 69808400 | `a8371cb0f30bb65d9cb24901d779dcc1ccfa231921747183a3671b3cbecbd734` |
| `app-x86-debug.apk`         | 66565155 | `a96b24021f5f82e4772a454a2612efb0a7166c396e712cc658162c83a9e56843` |

Debug 包名为 `top.imsyy.splayer.android.debug`，不会覆盖正式版用户数据。

## 提交清单

1. `00ba2444` `docs: 建立 Android 稳定性改造基线`
2. `bcb5999c` `build: isolate Android debug installs`
3. `6dc0d698` `fix: make Android update checks resilient`
4. `10c52378` `feat(update): add ABI-aware in-app Android updater`
5. `0c6a8425` `fix(ui): stabilize adaptive Android layouts`
6. `0a6b744e` `fix(playback): recover from transient network failures`
7. `29033fed` `fix(update): verify and harden Android upgrades`
8. `e935da92` `fix(update): enforce stable release channel`
9. `407635cf` `docs: finalize Android stability delivery`
10. `bcea4841` `docs: record project consolidation and signed release verification`
11. `ecffa396` `refactor(player): share mobile bottom controls`

## 回滚

优先按需对单个逻辑提交执行 `git revert <commit>`，保留公开历史和其他阶段成果。若整体回滚，应从最新提交开始按逆序逐个 revert；不要使用 `git reset --hard`、`git clean -fd` 或删除用户数据库。

更新功能回滚后，应同时移除对应前端入口、Capacitor 插件注册和 Android 原生插件；播放与 UI 提交相互独立，可分别回滚。

## 已知边界

- 精确带宽/延迟整形仍受 MuMu 无 root、无 `tc` 且当前无可控代理阻塞；短断网、长断网、HTTP 错误和离线完整缓存均已覆盖。
- 正式 Release 已使用外部 JKS 构建且证书与既有发布证书一致；本次仍为 30014 / 3.0.8，未验证递增版本升级安装。
- 系统未知来源授权和安装确认属于用户安全操作，不做自动授权或静默安装。
