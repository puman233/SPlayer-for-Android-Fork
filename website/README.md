# SPlayer for Android Fork · 项目主页

独立的 Vite + TypeScript 静态网站，无运行时 UI 框架、CDN 字体、追踪服务或后端。
线上地址：<https://puman233.github.io/SPlayer-for-Android-Fork/>。
仅展示项目、界面参考、下载、更新、帮助和开源来源，不提供商业化入口。
网站不参与 APK 构建、签名和发布。

## 开发与检查

要求 Node.js 24+（生成器与测试共用 TypeScript 数据校验），pnpm 10。

```sh
cd website
pnpm install --frozen-lockfile
pnpm dev
pnpm test
pnpm lint
pnpm typecheck
pnpm build      # 类型检查、生产构建、项目子路径/资源校验
pnpm preview
```

`pnpm check:site` 可单独检查已经构建的产物；构建流程自动执行相同检查。

开发与预览均需访问 `/SPlayer-for-Android-Fork/` 子路径；开发端口 5174，预览端口 4174。
`website/pnpm-workspace.yaml` 将网站与应用依赖隔离，不能删除。
Markdown 沿用应用相同版本的 marked 16.4.2 和 DOMPurify 3.4.16，随站点打包且按需加载，不引用 CDN。依赖由本目录锁文件约束；锁文件失败时 CI 应失败，不自动浮动安装。

## 内容与设计

- `index.html`：首屏、功能、下载、更新、FAQ、Fork 来源与许可证。
- `src/components/`：原生 DOM 导航、系统/浅色/深色主题、图片 dialog、渠道及线路选择。
- `src/styles/`：颜色/字号/间距变量、布局与组件；系统字体、克制蓝色强调、无持续动画。
- 手机下载区显示架构卡片，截图横向滚动；平板和桌面使用自适应列。
- 导航支持 Escape，图片预览支持左右方向键、缩放、Escape 和焦点返回。
- 原生 details 展开 FAQ、摘要与发布说明；正文支持 GFM 标题、列表、表格、代码、引用与链接。DOMPurify 过滤活动 HTML、事件、嵌入内容和外部图片，链接二次校验，表格与代码块可键盘横向滚动。

## 截图与图标维护

沿用仓库原有 WebP 截图和 Android launcher 导出的图标，没有生成虚构产品画面。
已有截图缺少对应 APK 版本、设备与采集证据，页面因此明确标为“既有界面参考”，不能当作当前发布版本验收。
替换截图需记录 APK tag/摘要、设备/方向、采集日期、来源及再分发依据，再更新 `src/data/screenshots.ts` 的标题、尺寸和分类。
同一文件用于 Hero 和画廊，不改变截图内容；首屏手机图优先加载，其余懒加载，所有图片有固有尺寸。

图标沿用 `public/icons/` 的既有资源，原始文件为
`android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`；不更改品牌或引入字体图标。
应用通知图标字体的部分授权证据仍未确认，见仓库 CHANGELOG 和对应 Release 许可材料；本网站不复制、加载该字体，不扩大既有风险接受范围。

## 版本与下载数据

链路：公开 GitHub Releases → Pages 构建生成 → 同源 `latest-release.json` → 浏览器。

```sh
pnpm metadata
```

`src/services/releaseModel.ts` 是浏览器、生成器和 Node 测试共用的校验器。
清单 schemaVersion 为 2，包含 `generatedAt`、`stable`、`beta`；每个渠道包含 tag、发布时间、发布页、正文、APK 名称/字节数/地址和发布源提供的可选 SHA-256。
只保留本仓库 HTTPS 发布页和对应 tag 的 APK 地址，过滤 draft、未上传、debug、unsigned、未知架构、非法标签与无效日期。
Beta 要求 `prerelease: true` 且 tag 为 `x.y.z-beta.N`，稳定版要求 `prerelease: false` 且无 Beta 后缀；各渠道按发布时间选择，不依赖列表顺序。
生成器遍历分页，先写临时文件再原子替换；请求失败只保留校验通过且含稳定版的旧清单，保留原始生成时间；没有有效清单时拒绝部署。
Token 只用于生成器请求头，绝不写入输出。浏览器不使用 Token。

浏览器先取同源快照；快照缺失/超过一天时尝试 GitHub API 前 100 个公开 Releases。
API 失败保留旧快照，两个来源都不可用时使用最多七天的有效会话缓存，否则显示错误及真实 Releases 入口。
快照和缓存始终显示生成时间及“可能不是最新”，不把其称为本次实时确认。
浏览器 API 仅是应急回退，超过前 100 条的旧稳定版可能不在其中；仍可通过 Releases 入口访问，构建生成器无此限制。

默认渠道为稳定版。页面启动时向 https://api.country.is/ 发起一次国家代码查询，不请求设备定位、不持久化 IP；仅 CN 自动默认配置中的首条可用加速线路（当前 gh-proxy.org），港澳台/海外/无效响应/1.8 秒超时使用 GitHub。地区切换不弹窗、不显示地区提示。手动选择始终优先且在本页跨渠道保留；换渠道恢复/隐藏对应架构行并重新应用所选线路，不沿用另一个版本的附件。
不根据 User-Agent 推测精确 ABI。可随时手动切换线路或运行自动测速，不把响应速度当作文件安全证明。IP 查询取决于网络出口与服务可达性；它只能选择默认线路，不能保证第三方服务可用。
用户须核对发布源摘要；没有摘要就显示缺失说明，不能生成或推测摘要。

## 部署与恢复

仅 `.github/workflows/pages.yml` 构建/部署网站，不修改 Android CI 和发布工作流。
触发：`dev` 网站变更、成功的 Android Release workflow_run、公开 Release 的发布/编辑/撤回/删除及手动执行。
workflow_run 用于覆盖 GITHUB_TOKEN 发布不会再触发 release workflow 的情况。
构建阶段先生成数据，再冻结安装、运行数据测试/lint/build，最后上传 Pages 产物。
Release 附件变更后若没有触发部署，手动运行 Website workflow 重新生成；检查生成时间和 GitHub 发布页一致性。
API 故障时部署可保留有效旧快照，恢复后重新执行；不能通过修改生成时间假装数据新鲜。

回滚只回退经审查的网站与 Pages 工作流修改，不触碰 APK、发布记录或用户数据。
本地构建和子路径预览不等于云端部署通过；上线后仍需检查 HTTPS、路径、所有资源、版本与附件。

## 真实浏览器验收

安装好的 `agent-browser` CLI 使用隔离 Chromium（不属于网站依赖）：

```sh
# 另一个终端保持 pnpm preview 运行
node scripts/browser-check.mjs
```

Windows 需将 `AGENT_BROWSER_BIN` 设置为 CLI 原生 exe 路径。
可选环境变量：`BROWSER_SITE`、`BROWSER_SESSION`、`BROWSER_REPORT_DIR`（仓库外截图/报告目录），
以及 `AGENT_BROWSER_SOCKET_DIR`（可写的隔离状态目录）。
脚本测试 320/360/390/412/768/1024/1440 及 844×390，明暗主题、菜单、渠道/线路、FAQ、摘要、图片预览、焦点、资源路径和失败模拟。
额外运行 `node scripts/site-quality-check.mjs`：模拟 CN/US/HK/MO/TW/失败/延迟、Markdown 安全过滤与排版、展开内容无障碍、测速请求取消。浏览器驱动与 CDP fixture 由 `scripts/browser-driver.mjs` 共用；默认阻断真实国家查询以保持确定性，测试不会对外下载 APK。
模拟期间不下载 APK。测试结束移除网络拦截；脚本中断后可用对应 session 的 `agent-browser network unroute` 清理。
`pnpm test` 不需要浏览器，覆盖数据/schema/生成器与故障保留；Pages CI 自动运行它。
自动无障碍检查只覆盖可自动检测项，不能代替屏幕阅读器与人工视觉验收。

设计与审计依据见 [QUALITY.md](QUALITY.md)。项目许可沿用仓库 AGPL-3.0，保留直接上游和原项目署名。新增网站依赖的原始许可随 `public/THIRD-PARTY-NOTICES.txt` 发布，并在许可区提供入口。
