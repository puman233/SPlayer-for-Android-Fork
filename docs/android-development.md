# Android 开发、测试与发布

## 使用方式

在 `dev` 提出功能或修复要求，Codex 按 `AGENTS.md` 完成定位、最小修改、检查和设备验收，提供结果后按用户授权提交和同步。测试构建、正式发布是独立授权。无需重复粘贴执行规则。

测试使用 `3.0.16-beta.N`，N 与 Android 构建号逐次递增。默认正式源码版本保持原样；本地或手动 Preview 通过 Gradle 属性覆盖测试版本。例如 `./gradlew :app:assembleDebug -PpreviewVersion=3.0.16-beta.1 -PpreviewVersionCode=30024`。CI 使用 `.debug` 包名隔离真实数据。用户明确要求覆盖 MuMu 原版时，需原 applicationId 和原签名，执行 `adb install -r`；禁止卸载或清空应用数据。

## 工作流触发与保护

| 工作流 | 触发 | 行为与权限 |
| --- | --- | --- |
| Android CI | dev push、针对 dev 的 PR、手动、Reusable Workflow | 变更门禁、actionlint、完整 TS 类型/单元测试、ESLint、Gradle 单元测试/Lint、一次 Debug 与测试 runner 构建；contents read |
| Android Preview | dev 手动，指定测试版本与构建号 | 复用 Android CI，7 天 APK Artifacts，含 SHA、UTC 时间、变体、ABI、SHA256；不创建 Tag/Release、不更改官网 |
| Android UI Test | 成功的 dev push Android CI，或手动指定成功 Run ID | 验证产物来源后复用 CI APK；API35 手机、平板、小屏真实后台歌词、悬浮窗和旋转布局测试；actions read、contents read |
| Android Release | 保留版本 Tag 事件、手动指定已有 Tag | Tag 事件缺少明确授权会失败；正式发布必须手动勾选授权、通过 exact commit 的 dev push CI 和 android-release Environment。仅发布 job 获得 contents write |
| Website (GitHub Pages) | website dev 修改、正式 Release 成功、人工 Release 编辑、手动 | 保持独立 concurrency、静态 metadata/fallback；失败 Release 不更新，Preview 不触发 |

受保护的 README、LICENSE、AGENTS、工作流、签名/版本 Gradle 配置、package、锁文件及大量业务删除进入 `change-review` Environment。普通源码修改自动检查；官网单独修改不重新构建 Android。新增检查器首次引入用当前版本，后续 PR 门禁优先执行基准分支检查器。密钥、本地配置和 APK 直接阻断。门禁不会输出疑似凭据内容。

## 管理员必须配置

1. 建立 `change-review` 和 `android-release` Environments，设置 required reviewers、禁止自审/管理员绕过；将允许部署分支限定为 dev 和授权版本 Tag。`development` 不需要审批。未配置 required reviewers 的 Environment 不会自动提供人工保护，不能视为完整启用。
2. 将四项现有 ANDROID 签名 Secrets 迁入受保护 `android-release` Environment。添加该 Environment 的 `ANDROID_RELEASE_CERT_SHA256` variable（可信原发布证书的 64 位 SHA256，不是新生成的证书）。保持原密钥。
3. dev Ruleset 禁止强制推送，要求 PR/审核与 Android CI 审核、构建检查；用 CODEOWNERS/规则保护 `.github/**`、AGENTS、LICENSE、版本及签名配置。不要把源码可修改的门禁当作管理员权限隔离。
4. 如 UI 验收是发布必需项，在受保护 Environment 配置 `ANDROID_UI_REQUIRED=true`。发布门禁会要求当前提交手机、平板、小屏对应的有效 UI Artifacts，且其所属 Android UI Test 整体成功；失败或过期需要重新验收。workflow_run 从默认分支加载定义，仓库默认分支为 dev，可自动触发；不操作其他分支。
5. Actions 默认 token 设 read-only，启用所用 Actions；官网继续使用现有 github-pages Environment。真实账号/网络、ROM 省电策略、长期后台和真实音源可用性另行人工验收。

## 正式发布契约

正式签名缺失、调试证书、预期指纹不一致、源码/Tag/APK 版本不一致、四种 ABI 缺失、未经授权或当前提交 CI 未成功均阻断发布。本地验证产物的 SHA256SUMS 作为 30 天 Artifact 保存；发布后校验四附件大小和 API 摘要。保持既有草稿恢复、重复 Tag 跳过、旧附件备份和明确 Release ID/repair SHA 校验；取消/重试不删除历史 Release，不移动 Tag。官网只在整个 Release job 成功后同步。

本地检查：`python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'`；actionlint 检查 Actions YAML；`pnpm lint`、`pnpm typecheck`、`python3 -B .github/scripts/development-checks.py tests`、`pnpm build:android` 后在 android 执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。设备测试必须断言实际行为，不把模拟器启动当验收；相关截图与报告仅临时保存。

后台歌词通过原生 ExoPlayer 进度和状态直接驱动，WebView 延迟消息不得覆盖原生时钟；远程 JS 播放模式仍使用既有回传。冷启动和回前台查询实际窗口状态，恢复已开启但丢失的悬浮窗；按钮在窗口实际附着后才显示开启。设备测试使用生成的测试音频与同目录 LRC，连续五分钟留在后台，验证两次自动切歌、进度与歌词数据，无需真实账号。

手机详情头部使用自然高度和实测列表偏移，覆盖长标题、放大字体、隐藏封面、折叠与滚动。UI 工作流执行八项测试，保存详情页和播放器截图；歌手专辑网格同时检查实际滚动与卡片宽度。当前测试迭代为 `3.0.16-beta.5`，构建号 `30028`。
