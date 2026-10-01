# Android 稳定性改造执行计划

## 目标

在 `codex/android-stability-update` 分支完成 UI 自适应、弱网播放恢复、GitHub Releases 检查与应用内更新，并在手机/平板 MuMu 终端验证。

## 当前基线

- 基线提交：`d834171d`
- 上游分支：`origin/dev`
- 工作树：隔离 worktree，初始无修改
- 技术栈：Vue 3、TypeScript、Naive UI、Capacitor 8、Android Java、Media3 ExoPlayer
- 版本：`3.0.8`
- Node：`24.20.0`
- pnpm：`10.28.1`
- JDK：`C:\Program Files\Zulu\zulu-21\`，尚未加入当前 PATH
- Android SDK：`D:\Scoop\apps\android-clt\current`
- MuMu：两台 Android 15 / API 35 模拟器在线

## 里程碑

### M0 基线审计与复现

状态：进行中

- [x] 同步 `dev` 并建立隔离分支
- [x] 采集双终端基础参数
- [x] 安装依赖并运行 typecheck/lint
- [x] 建立 Android 构建基线
- [x] 手机安装基线 APK，采集启动、设置和更新页截图
- [ ] 使用并存 Debug 包名安装到平板，避免清除现有签名版本数据
- [ ] 完成播放页 UI 与播放短暂停顿复现

验收：每个问题有复现状态、证据、候选根因；所有未运行检查有明确原因。

### M1 UI 自适应

状态：未开始

- [ ] 统一视口、设备形态、方向、缩放和安全区模型
- [ ] 修复播放页/歌词页重叠和矮屏溢出
- [ ] 修复设置页、弹层和底部导航边界问题
- [ ] 增加可复用布局规则和边界测试
- [ ] 双终端多 density/方向/字体回归

### M2 播放连续性

状态：未开始

- [ ] 建立去敏状态时间线与缓冲诊断
- [ ] 验证缓存、预取和 URL 恢复链路
- [ ] 实现有界重试、退避和断点恢复
- [ ] 覆盖短时断网、限速、seek、切歌和主动暂停

### M3 应用内更新

状态：未开始

- [ ] 单一仓库配置指向 `puman233/SPlayer-for-Android-Fork`
- [ ] 实现并测试 SemVer 与稳定/预发布通道
- [ ] 按 `Build.SUPPORTED_ABIS` 选择 Release APK
- [ ] 原生下载、校验、取消/重试和系统安装器
- [ ] 设置页状态与错误文案

### M4 文档与最终回归

状态：未开始

- [ ] README 与 CHANGELOG
- [ ] 完整质量门和双终端回归
- [ ] 最终报告、回滚说明和提交清单

## 当前下一步

1. 为 Debug 构建增加并存包名，避免覆盖正式签名版本。
2. 安装至两个 MuMu 终端，继续采集播放页 UI 证据。
3. 核实更新仓库硬编码与现有播放恢复链路，开始第一批修复。
