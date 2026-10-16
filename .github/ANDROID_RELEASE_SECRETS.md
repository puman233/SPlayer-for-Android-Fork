# Android CI 与自动发布

## 首次配置

在 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret** 配置以下四项（复用原工作流的名称）：

| Secret                    | 内容                            |
| ------------------------- | ------------------------------- |
| ANDROID_KEYSTORE_BASE64   | 正式 keystore 文件的完整 Base64 |
| ANDROID_KEYSTORE_PASSWORD | keystore 密码                   |
| ANDROID_KEY_ALIAS         | 正式签名私钥别名                |
| ANDROID_KEY_PASSWORD      | 私钥密码                        |

必须复用已发布 APK 的正式签名私钥，否则无法覆盖升级。禁止使用 Android Debug keystore。PowerShell 编码示例（仅在可信本机执行，不提交输出）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\private\release.keystore"))
```

Linux：`base64 -w 0 /private/release.keystore`。

无需个人访问令牌。GitHub 自动提供 GITHUB_TOKEN；CI 仅 contents: read，发布 job 才有 contents: write。仓库/组织策略需允许声明的写权限和引用的 Actions。

## 实际工具链

- JDK 21（Capacitor 8 源码要求）、AGP 8.13.0、项目 Gradle Wrapper 8.14.3。
- Node 24、pnpm 10.28.1，冻结锁文件安装，先执行 pnpm build:android 生成 Web、Capacitor 和内嵌 API 资源。
- SDK/target 36、Build Tools 36.0.0、NDK 28.2.13676358、CMake 3.22.1。
- Application module 为 :app，无 flavor；四种 ABI：armeabi-v7a、arm64-v8a、x86、x86_64，不生成 universal。
- CI：`cd android && ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。
- Release：先 `:app:verifyReleaseConfiguration`，再 `:app:assembleRelease`。将来增加 flavor 时聚合任务构建所有 release 变体，版本与签名检查要求每个变体都符合。

CI 在 dev push/PR 运行，无需 Secrets，不创建 Release。Gradle 缓存仅 dev 写入，Tag/PR 只读。

## 发布步骤

1. 修改 android/app/build.gradle 的 versionName 和递增 versionCode，并同步 package.json.version。当前值为 3.0.10 / 30016。
2. 提交推送 dev，等待 CI 通过。
3. 对已验证提交创建、推送 Tag，例如 `git tag v3.0.10` 和 `git push origin v3.0.10`。若对应 Release 已存在，应使用新版本。

Tag 去掉 v 后必须与 Android 和前端版本完全相同；不会自动改版本。预发布示例为 v3.0.10-rc.1，两处版本必须精确为 3.0.10-rc.1。非版本形式的 v\* Tag 明确报错。

从 `android/app/build/outputs/apk/**/output-metadata.json` 读取所有 release 最终 APK，校验真实清单版本/包名、非 debuggable 属性、签名证书 SHA-256 与正式 keystore 相同，拒绝标准 Android Debug 证书。不会 glob 整个 build 目录；独立 `release/` 目录仅包含已验证 APK，附件 glob 为 `release/*.apk`。保留 Gradle 最终文件名，例如 `app-arm64-v8a-release.apk`；flavor 的名称也保留在原文件名中，发生重名时停止发布。

发布正文默认简体中文，覆盖当前版本全部用户可感知变化，而非仅选取 Highlights。`CHANGELOG.md` 保存完整项目记录；`.github/release-notes/<tag>.json` 保存整理后的 `changes` 和内部 `coverage` 映射；构建、测试、签名与验收证据留在内部报告，不进入正文。每个 ReleaseChange 包含唯一 `id`、`category` 和中文 `summary`；分类可选主要更新、新增功能、修复与优化、Android、已知问题、注意事项，空分类不生成标题，同一变化不能重复。

模板 `.github/RELEASE_TEMPLATE.md` 仅负责整理后的章节和唯一的 previous_tag...current_tag compare 链接，不固定插入 ABI 表。默认从 GitHub 已公开 Release 中选择当前 Tag 最近的祖先版本，排除草稿和当前版本；确有需要时可在 JSON 显式指定 `previous_tag`，仍须通过 Git 祖先关系校验。无法确定时停止，不猜测。版本范围、当前 Tag 的 CHANGELOG 条目及全部范围内提交共同构成来源快照。

Coverage Audit 要求逐来源分类为 `user-visible`、`internal` 或 `ambiguous`。用户变化必须映射到存在的 ReleaseChange ID，并记录代码或事实证据；内部来源必须说明排除原因且不得映射到正文；任何来源缺失、来源过期、未确认条目或无来源的公开条目都会阻止生成。恢复时使用当前发布工具中的数据，审计的应用源码仍来自原 Tag；Tag、提交或 CHANGELOG 变化后必须重新审核映射。生成器返回完整内部矩阵；Actions 中另写入 `RUNNER_TEMP/release-notes-coverage.json`，不拼入公开正文。分类和语义完整性仍需维护者逐项审核，自动检查不能证明自由文本的全部语义。

脚本不复制整个 CHANGELOG，也不追加 GitHub 自动 PR/贡献者记录。正文必须通过中文、唯一链接、标题、重复内容、内部信息及无证据比例/绝对化表述检查；任何检查失败时，在创建、上传、更新之前停止。恢复已有草稿也使用经过检查的项目正文。同版本修复的内部提交信息保留在执行记录中。

Release 标题为 Tag，使用项目生成的 `--notes-file`，不使用 `--generate-notes`；包含 alpha/beta/rc（忽略大小写）时为 prerelease，否则正式版本。使用 --verify-tag，不创建或移动 Tag。修改模板或精选说明后运行 `python3 -B -m unittest discover -s .github/scripts -p 'test_*.py'`，其中包含正文质量正反例和发布保护回归。

## 重跑与签名安全

同一 Tag 串行执行，已公开 Release 跳过。先创建草稿，全部附件上传成功才公开。重跑检查草稿目标提交和已有附件 SHA-256，只上传缺失附件，不覆盖已有附件。

如果发布脚本自身需要修复，先把修复推送到 `dev` 并等待 CI 通过，然后在 Actions → Android Release → Run workflow 选择 `dev`，填入原 Tag（例如 `v3.0.9`）。手动恢复使用原 Tag 的 Android/前端源码和当前工作流版本的环境配置及发布工具；草稿目标仍是原 Tag 提交，不移动或重建 Tag。普通 Tag push 则使用该 Tag 中的工作流工具。已公开版本仍然跳过。

若重建 APK 与草稿附件字节不同（签名轮换或构建不能完全复现），停止发布。维护者检查后人工删除错误草稿/附件再重跑；禁止覆盖已公开版本。

keystore 只写 runner 临时目录，密码通过环境变量传入，不写 key.properties、不进入 Gradle 缓存或附件，always 步骤清理。本地继续支持已忽略的 android/key.properties；无材料的 release 不再回退 debug 签名，unsigned 包无法发布。

参考：[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Gradle Actions](https://github.com/gradle/actions/blob/main/setup-gradle/action.yml)、[GitHub CLI Release](https://cli.github.com/manual/gh_release_create)。
