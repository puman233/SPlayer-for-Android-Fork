# 架构决策

## D-001 使用隔离工作树

- 状态：已采纳
- 决策：从干净的 `dev` 创建 `codex/android-stability-update` 工作树。
- 理由：隔离长期修改，保留用户原始检出，并使阶段提交可独立回滚。

## D-002 以实际可用空间而非原生像素判断布局

- 状态：待实现验证
- 决策：CSS 布局基于逻辑视口、安全区、方向和可用高度；原生 density 作为测试维度而非直接缩放因子。
- 理由：WebView CSS px 已是逻辑像素，直接按 dpi 倍乘会造成二次缩放。

## D-003 在现有原生播放链路上增强

- 状态：待实现验证
- 决策：复用 Media3 ExoPlayer、SimpleCache、预取和 URL resolver，不建立第二套播放器。
- 理由：避免队列、MediaSession、缓存与前端状态产生双重真相。

## D-004 系统安装器保留最终确认

- 状态：已确定
- 决策：应用内只负责选择、下载、校验 APK 并唤起系统安装器，不实现静默安装。
- 理由：符合 Android 安全模型和任务边界。

## D-005 Debug 包与正式包并存

- 状态：已验证
- 决策：Debug 构建使用 `applicationIdSuffix` 与 `versionNameSuffix`，不覆盖正式签名安装。
- 理由：平板已有签名不同的正式包；并存安装可保留数据并允许双端持续调试。

## D-006 GitHub API 限流回退

- 状态：已验证
- 决策：更新检查优先读取 GitHub Releases API；Android 遇到匿名额度限制时，使用原生 HTTP 读取同仓库官方 Releases Atom 源。
- 理由：匿名 API 额度按出口 IP 共享，模拟器和公共网络容易耗尽；Atom 源可继续提供版本、发布日期和更新日志，且不依赖不稳定的页面抓取。
