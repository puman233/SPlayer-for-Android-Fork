# 同版本 APK 修补

`Android Same-Version Patch` 只更新已公开正式 Release 的四 ABI APK，不创建版本或 Release，不移动 Tag，也不增加 versionCode。自动更新不会因为这次修补出现更高版本；已安装用户重新下载并覆盖安装即可。

工作流入口为 `.github/workflows/android-patch.yml`，与普通发布共享同一版本的并发锁。

1. 将修补源码提交到 dev，等待该完整 40 位提交的 Android CI 成功。
2. 在 dev 上运行 `mode=prepare`，指定现有 Tag 和源码提交。沿用正式签名 Secrets，生成四 ABI 候选包；下载现有四 ABI 作为备份。新旧包必须具有相同包名、versionName、versionCode、证书和 ABI；检查 ZIP、非 debuggable 和关闭 WebView debugging。
3. 下载准备运行的 `android-patch-bundle` artifact。包含 `old/`、`new/`、更新说明和来源清单。保留备份并完成手机、平板的实际升级检查，不能清除正式包数据。
4. 用相同 Tag、源码提交运行 `mode=apply`，填写成功准备运行的 ID。再次验证来源、候选 APK、原附件 digest、Release 身份与正文、Tag 引用未变；任何不一致均停止。
5. 上传并校验全部暂存附件后，给旧附件改备份名，再让新附件恢复标准名称。四 ABI 和说明全部验证成功后才删除本次旧附件备份；artifact 仍保留 90 天。正文保留已有下载表格和说明，追加源码、工作流、时间、签名与各包 SHA-256。

GitHub 不提供多附件原子替换，短暂切换期间可能存在混合附件或标准名称暂不可用。脚本失败时先移开本次新附件，再恢复旧附件名称和本次修改的正文。中断后重跑相同准备产物会先恢复旧包；已提交完成但旧备份清理失败时，重跑只核对并清理备份。未知附件或外部正文改动不会被自动删除或覆盖。

如果网络持续不可用导致自动回滚失败，停止新的发布操作，使用已下载的 `old/`、manifest 中的原资产 ID 和原正文恢复；不能创建另一个 Release 或重打 Tag 来绕过问题。恢复后重新验证四个标准名称、包哈希、签名与 Release/Tag 身份，再执行后续修补。

## v3.0.11 已执行修补（2026-10-04）

正式 APK 源码为 `99c2c3ce3290668ca4d7123e71f551590d65e4de`，准备 [运行 37135681053](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37135681053)，替换 [运行 37137618497](https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37137618497)。四 ABI 产物通过本地与云端验证，手机、平板正式包覆盖升级通过；既有 Release ID 402442837 和原 Tag 引用均保持不变。源码、最终哈希、备份位置及验证边界见 [执行检查点](../AGENT_CHECKPOINT.md)。
