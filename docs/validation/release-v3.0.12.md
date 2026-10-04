# v3.0.12 发布验收

## Phase

用户确认后同步 dev，沿用现有 Android Release 工作流发布下一版本。状态：VERIFIED（发布及产物范围）。

## Root Cause

首轮正式构建与签名校验通过，发布工具创建草稿后立即查询未读到草稿而停止。随后实际确认草稿目标提交正确且无附件，通过现有失败作业重跑恢复成功；没有覆盖附件或改动发布工具、签名配置。

## Changes

前端与 Android 版本统一为 3.0.12 / 30018，更新日志纳入已验收的手机横竖屏、歌词扩展、桌面触碰与 2.5 秒渐隐、超时恢复。代码提交 c5d0cd409390fb75ba9476e8a72707b5337650ef 已同步至 dev，v3.0.12 Tag 指向同一提交。

## Responsive Strategy

手机播放器使用已验收布局，平板播放器保留原布局；本次版本准备没有新增布局修改。

## Validation

- 15 项发布保护测试、format、lint、Android 资源构建、四 ABI debug 与 JVM 测试通过。
- [Android CI](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37199896277) 成功后推送 Tag。
- [Android Release](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37200119556) 第二次运行成功，四 ABI 正式 APK 已公开。
- 下载全部公开附件，验证 ZIP CRC、附件尺寸与 API SHA-256、原生库 ABI、非 debuggable、包名 top.imsyy.splayer.android、versionName 3.0.12 / versionCode 30018，全部通过。
- 四包证书 SHA-256 均为 d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea，与下载的 v3.0.11 正式 arm64 包一致。

| ABI         |       字节 | SHA-256                                                          |
| ----------- | ---------: | ---------------------------------------------------------------- |
| arm64-v8a   | 60,021,608 | 5d2a108b896468ecd749520956702c33af1fe65b0aee9f1a37071fef01e89283 |
| armeabi-v7a | 59,225,950 | be1e825aad181911e6ca43577c6ce786f6d967ef0acc8d59ad9d86fce214769b |
| x86         | 62,328,166 | f9605bf963d6a01c2aa891b5bcbf9a5f2782456738ed9124b5f65be594362aa9 |
| x86_64      | 65,575,255 | 8986787a974784cb6909818049aca816d4a5b18f23f429556e2e004337a67b64 |

## Screenshots

本次版本准备只修改版本与文档；双端 UI 截图与实际系统触碰验证见 [桌面歌词验收](desktop-touch-2500.md) 和 [歌词扩展验收](lyrics-expansion-toggle.md)。

正式安装包及日志证据：C:/Users/ihyj/.codex/visualizations/2026/10/04/01a10549-90ac-7e40-adfe-0e909a47e44b/release-v3.0.12。安装包位于 apks/app-ABI-release.apk；核验记录 artifact-verification.json，工作流日志 ci.log / release-success.log，首轮失败日志 release-first-failure.log 保留。

## Known Issues

Android Lint 历史 187 errors / 27 warnings 与既有构建提示保留；本次没有扩大该修复范围。UI 运行验证在双端模拟器隔离包完成，公开正式包已核验签名与清单，未覆盖用户正式安装或清除正式数据，真机升级尚未实际测试。

## Approval Required

用户已确认。本次同步与发布完成：[v3.0.12](https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.12)。没有进入后续 UI 阶段。
