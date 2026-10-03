# Android 自动发布工作流检查点

日期：2026-10-03；分支：dev。

## IMPLEMENTED

- Android CI：dev push/PR，JDK 21、pnpm 10.28.1、Gradle 缓存，资源构建、发布保护测试、ESLint、debug 编译/单元测试/Android Lint。
- Tag Release：v\* 触发，检出 Tag，正式签名 Secrets 注入，Gradle 版本校验，所有 release ABI/flavor 元数据收集，真实 APK 清单与证书校验。
- Release 幂等处理：同 Tag 串行，已公开 Release 跳过，草稿上传完成才公开；已有附件校验 SHA-256，不覆盖。
- 已替换原手动 android-release.yml；配置说明在 .github/ANDROID_RELEASE_SECRETS.md。

## VERIFIED

- 三个 Actions YAML 语法、触发条件、CI 只读/发布写权限及格式检查。
- Python 语法与 12 项发布保护测试，包括草稿 API 回退、分页、权限错误及恢复时的 Tag 提交检查。
- pnpm lint：通过；pnpm build:android：通过；pnpm format：通过。清理构建生成及全库格式化带来的无关已跟踪文件变化。
- 实际 Gradle 命令 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug：通过（首次 offline 因测试依赖缺缓存失败，联网解析后通过）。
- 临时测试证书的 :app:verifyReleaseConfiguration :app:assembleRelease：通过，四个 ABI 均生成 APK。
- 实际收集脚本：四个 APK 清单、版本与签名验证通过（本机验证只适配了 Windows 工具路径，CI 原脚本使用 Linux 工具）。
- Gradle 负向验证：v1.3.0 与 versionName=3.0.8 不一致时明确失败。
- 没有已跟踪 keystore/key.properties；临时测试签名材料与测试复制附件已清理。

## BLOCKED

- 已确认四项 Secrets 已配置。dev CI 37095215774 实际通过；v3.0.9 Tag 指向 d6cf53c2。正式 runner 的签名构建及四 ABI APK 校验通过。
- 首次 Release 37095509169 因按 Tag API 未返回新草稿而失败，留下空草稿（未公开、无附件）。已实现分页草稿查询及从 dev 手动恢复原 Tag 的入口，待修复版 CI 与恢复发布验证。
- 本机 build 目录中的 release APK 使用临时测试证书，不属于正式发布产物。

## 下一步

v3.0.8 已发布，因此使用 v3.0.9 / versionCode 30015。先推送 dev 验证 CI，再推送 v3.0.9 Tag；当前提交后将继续检查线上运行和附件。
