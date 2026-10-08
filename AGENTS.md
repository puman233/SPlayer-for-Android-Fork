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

## 长期开发与验收

- 开始检查 `git status`、当前分支及相关目录规则，默认使用 `dev`。不得擅自创建、切换、删除分支或重置、清理、覆盖已有修改。
- 修改前阅读现有调用链并定位根因。检查共享代码的调用方，保留成熟功能与 UI 风格；无关问题记录但不扩大修改。
- 播放、歌词、下载、网络及同步检查生命周期、异常和后台行为。UI 检查手机竖横屏、平板、小屏、密度变化与系统 Insets。
- 未明确授权不改 README、LICENSE、工作流、签名配置、正式版本或依赖锁文件；必要修改经过可审计的人工审查。不得删除测试、降低检查强度或掩盖报错。
- 完成执行 diff 审核、`pnpm lint`、`pnpm typecheck`、`python3 -B .github/scripts/development-checks.py tests`、发布/工作流脚本测试、Android 构建/单元测试/Lint、相关设备验收。环境限制如实标注 BLOCKED；未实际验证标注 IMPLEMENTED，仅实际通过标注 VERIFIED。
- 常规 CI 测试包使用 `.debug` 隔离包名；MuMu 在用户明确要求时使用原签名对原应用执行 `adb install -r`，保留数据和测试后应用，不卸载、不清空数据。
- 3.0.16 开发测试使用 `-PpreviewVersion=3.0.16-beta.N -PpreviewVersionCode=<递增整数>`。每次可安装迭代递增 N 和构建号；默认正式版本不随测试构建改变，正式发布禁止测试参数。
- 测试构建授权不等于 Tag/Release 授权。CI APK、截图、日志和报告仅保留临时 Artifacts。正式发布必须明确手动授权、通过当前提交 CI 和 `android-release` Environment 审核，沿用原签名与历史备份保护。
- 工作流、保护项与管理员设置参见 `docs/android-development.md`。后续执行先审计现有机制，优先复用，禁止绕过保护。
