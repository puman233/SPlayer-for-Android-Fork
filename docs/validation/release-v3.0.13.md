# v3.0.13 发布验收

用户明确授权 MuMu 双端验证后提交、推送和发布。版本为 3.0.13 / 30019，Tag 指向 `ad70fccd2391947d63eac0c4ffc5b82f808838b4`。

## 双端验收

通过生产下载引擎实际下载官方 v3.0.12 x86_64 APK，共 65,575,255 字节。手机 gh.llkk.cc 8.483 秒、原地址 4.048 秒；平板代理 6.330 秒、原地址 4.682 秒。四次真实下载均校验官方 SHA-256、长度、ZIP、包名、版本、签名存在和进度，结果全部通过。本网络原地址更快，代理可用不等于本次提速。

两端各 5 项下载回退/取消/专辑详情布局回归通过。最终 v3.0.13 隔离包播放器回归手机 56.887 秒、平板 7.917 秒通过。补测首轮漏传设备标签导致错误断言分支，修正测试命令后双端通过，代码未因此改动。正式应用数据未清除或覆盖。

## 构建与发布

格式工具执行并恢复其无关格式改动；ESLint 零警告、15 项发布保护测试、更新资产测试、Android 资源及四 ABI 原生构建、原生 JVM 测试通过。

[Android CI](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37207888706) 成功，包括配置中的 Android Lint 检查；既有 Lint 基线问题不代表全部消失。

[Android Release](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37208138793) 首轮构建、签名与版本检查成功；发布工具创建草稿后立即读取未发现而停止。实际核对草稿目标提交和零附件后，已重跑失败作业恢复已有草稿，没有删除 Tag、覆盖附件或重复创建 Release。第二次运行成功，四 ABI 正式 APK 已公开。

文案继续使用现有 ABI 推荐表、更新日志和 Full Changelog 模板；附件沿用 `app-ABI-release.apk`。代理仅发送公开 APK 下载请求，GitHub 凭据仅用于本机官方 API 验收，不写入源码或 APK。

## 新版本公开后双端实网验收

双端分别通过代理及原地址完整下载刚发布的 v3.0.13 x86_64 正式 APK，65,576,443 字节，SHA-256 `7ca81b61a8e3a20fef204264de6d707268c4c86017bd616343ffd8d67a562f97`。四次真实下载全部通过，临时文件清理，未执行正式包覆盖安装。

| MuMu | gh.llkk.cc | GitHub 原地址 | 结果                             |
| ---- | ---------: | ------------: | -------------------------------- |
| 手机 |   7.862 秒 |      4.299 秒 | 官方 SHA / 版本 / 完整包校验通过 |
| 平板 |   8.223 秒 |      4.179 秒 | 官方 SHA / 版本 / 完整包校验通过 |

本网络原地址更快，代理实网可用但本次没有速度提升，速度取决于网络与第三方服务。

## 正式产物检查

独立下载四个公开附件，验证 CRC、官方 SHA-256、精确尺寸、单 ABI、非 debuggable、包名 `top.imsyy.splayer.android`、版本 3.0.13 / 30019，以及 apksigner 校验。四包证书 SHA-256 均为 `d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea`，与 v3.0.12 一致。Release 为正式版，名称 v3.0.13，文案与附件沿用现有格式。

| APK                         |       字节 | SHA-256                                                          |
| --------------------------- | ---------: | ---------------------------------------------------------------- |
| app-arm64-v8a-release.apk   | 60,022,796 | d96f4b03cdfd9922c041eea259faa88c32d90e9311eae12e0f8464e66f0ffcc5 |
| app-armeabi-v7a-release.apk | 59,227,138 | cb0934ac50a63ebb8fd711d1f271de487d007bf7bdd11b7e4c22703e78c29601 |
| app-x86-release.apk         | 62,329,354 | b3c521fa862b527bb773375d31935107aa627543b07d666c7e3e099ffbddc59a |
| app-x86_64-release.apk      | 65,576,443 | 7ca81b61a8e3a20fef204264de6d707268c4c86017bd616343ffd8d67a562f97 |

本地正式安装包目录：`C:/Users/ihyj/SPlayer/SPlayer-for-Android-Fork/android/build/release-v3.0.13/`，包含四个 APK、metadata.json、verification.json。实网日志：`android/build/live-release-phone.log` 和 `android/build/live-release-tablet.log`；构建与检查日志保存在 android/build/task-\*.log。测试包已停止，正式用户数据未覆盖，未知来源安装权限未改动。

发布地址：[v3.0.13](https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.13)。历史 Lint 基线、第三方服务速度、API 29–34/36 与真实硬件升级不包含在本次全部通过范围内。
