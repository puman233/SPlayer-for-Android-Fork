# 开发约束

- 项目面向 Android，使用 Vue 3、TypeScript、Naive UI、Capacitor 和原生 Android 工程。
- 使用 `pnpm`，依赖安装使用 `pnpm install --frozen-lockfile`。
- 复用现有 UI、图标、状态管理与服务；避免无关重构和全仓库格式化。
- 保持现有代码风格，注释简洁；修改业务代码前先定位共享核心与根因。
- 完成修改后运行 `pnpm lint`、`pnpm build:android`，按变更范围执行相关测试。APK 编译在 `android/` 下运行 `./gradlew assembleDebug`（Windows 使用 `gradlew.bat`）。
- 保留 Android CI、Release 及其依赖，未经要求不修改工作流、依赖或版本号。
- 仓库只保存源码、必要配置和正式文档。不提交本机绝对路径、密钥、本地环境、构建产物、临时计划、代理日志或设备验收报告；不创建永久代理检查点。
- 测试截图、APK、日志及临时报告放在临时目录；README 描述当前产品与构建方法，CHANGELOG 记录面向用户的版本变化。
- 不覆盖用户已有修改，不清理用户数据。提交前检查 `git status` 与 diff，排除无关文件；commit、push、Tag 和 Release 需用户明确要求。
