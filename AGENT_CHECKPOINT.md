# Android 自动发布工作流检查点

日期：2026-10-03；分支：dev。

## IMPLEMENTED

- Android CI：dev push/PR，JDK 21、pnpm 10.28.1、Gradle 缓存，资源构建、发布保护测试、ESLint、debug 编译/单元测试/Android Lint。
- Tag Release：v\* 触发，检出 Tag，正式签名 Secrets 注入，Gradle 版本校验，所有 release ABI/flavor 元数据收集，真实 APK 清单与证书校验。
- Release 幂等处理：同 Tag 串行，已公开 Release 跳过，草稿上传完成才公开；已有附件校验 SHA-256，不覆盖。
- 已替换原手动 android-release.yml；配置说明在 .github/ANDROID_RELEASE_SECRETS.md。

## VERIFIED

- 三个 Actions YAML 语法、触发条件、CI 只读/发布写权限及格式检查。
- Python 语法与 8 项发布保护测试。
- pnpm lint：通过；pnpm build:android：通过；pnpm format：通过。清理构建生成及全库格式化带来的无关已跟踪文件变化。
- 实际 Gradle 命令 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug：通过（首次 offline 因测试依赖缺缓存失败，联网解析后通过）。
- 临时测试证书的 :app:verifyReleaseConfiguration :app:assembleRelease：通过，四个 ABI 均生成 APK。
- 实际收集脚本：四个 APK 清单、版本与签名验证通过（本机验证只适配了 Windows 工具路径，CI 原脚本使用 Linux 工具）。
- Gradle 负向验证：v1.3.0 与 versionName=3.0.8 不一致时明确失败。
- 没有已跟踪 keystore/key.properties；临时测试签名材料与测试复制附件已清理。

## BLOCKED

- 已确认 GitHub 四项正式签名 Secrets 的名称已配置；不会读取或导出其内容。GitHub 托管 runner 和真实 Release API 创建/上传待 v3.0.9 首次发布验证。
- 本机 build 目录中的 release APK 使用临时测试证书，不属于正式发布产物。

## 下一步

v3.0.8 已发布，因此使用 v3.0.9 / versionCode 30015。先推送 dev 验证 CI，再推送 v3.0.9 Tag；当前提交后将继续检查线上运行和附件。
