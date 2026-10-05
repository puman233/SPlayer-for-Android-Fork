# SPlayer for Android Fork · 项目主页

这个目录是 [SPlayer for Android Fork](https://github.com/puman233/SPlayer-for-Android-Fork) 的
GitHub Pages 项目主页，独立于 Android 应用本体，不参与 APK 构建，也不影响 Android CI 与 Release。

线上地址：<https://puman233.github.io/SPlayer-for-Android-Fork/>

## 技术栈

- Vite + TypeScript，无 UI 框架、无状态管理、无第三方 CDN
- 样式为手写 CSS（`src/styles/`），系统字体，自动跟随系统深色模式
- 唯一的运行时网络请求是 GitHub Releases 公开接口，失败时保留静态内容

## 本地开发

```bash
pnpm install
pnpm dev        # http://localhost:5174/SPlayer-for-Android-Fork/
pnpm typecheck  # TypeScript 检查
pnpm lint       # ESLint
pnpm build      # 类型检查 + 产出 dist/
pnpm preview    # 预览构建结果，端口 4174
```

本地预览要带上 `/SPlayer-for-Android-Fork/` 子路径，因为 `vite.config.ts` 里的
`base` 就是部署路径，这样能提前发现资源 404。

`website/pnpm-workspace.yaml` 让 `website/` 成为独立的 pnpm 项目。**不要删除它**：
仓库根目录的 `pnpm-workspace.yaml` 没有包含 `website`，没有这个文件时在 `website/`
里执行 `pnpm install` 会向上找到根的 workspace，从而影响 Android 应用的依赖。

首次执行 `pnpm install` 后建议把生成的 `pnpm-lock.yaml` 一起提交，Pages 工作流检测到
锁文件就会自动改用 `pnpm install --frozen-lockfile`。

## 目录结构

```text
website/
├─ public/
│  ├─ icons/          由 Android App 图标生成，见下方说明
│  └─ screenshots/    真机截图目录，见下方说明
├─ src/
│  ├─ components/     导航、下载区块、界面预览
│  ├─ data/           截图清单
│  ├─ services/       GitHub Release 读取
│  ├─ styles/         设计变量、基础样式、布局、组件
│  ├─ utils/          格式化与 APK 架构匹配
│  └─ main.ts
├─ index.html
└─ vite.config.ts
```

## 界面截图

`public/screenshots/` 里是真实 App 运行截图，只做过等比例缩放与 WebP 压缩，没有裁改界面内容：

| 文件                           | 内容                                 | 尺寸     |
| ------------------------------ | ------------------------------------ | -------- |
| `desktop-home.webp`            | 桌面界面（侧栏 + 主页 + 底部播放栏） | 1600×904 |
| `mobile-home.webp`             | 手机首页                             | 720×1584 |
| `desktop-lyrics.webp`          | 宽屏歌词                             | 1600×904 |
| `mobile-player.webp`           | 手机播放页                           | 720×1584 |
| `desktop-floating-lyrics.webp` | 桌面歌词悬浮窗                       | 1600×904 |
| `mobile-lyrics.webp`           | 手机歌词页（首页右侧使用）           | 720×1584 |
| `landscape-player.webp`        | 手机横屏播放                         | 1800×818 |

新增或替换截图：

1. 把文件放进 `public/screenshots/`。
2. 在 `src/data/screenshots.ts` 的 `SCREENSHOTS` 数组里登记文件名、标题和真实像素尺寸。

```ts
export const SCREENSHOTS: readonly ScreenshotEntry[] = [
  {
    file: "mobile-home.webp",
    title: "手机首页",
    caption: "推荐、专属歌单与迷你播放栏",
    width: 720,
    height: 1584,
    category: "mobile",
  },
];
```

`category` 决定排版：`mobile` 占一列，`desktop` 占两列，`landscape` 独占一行。
`hero: true` 的手机截图会出现在首页右侧，宽屏位置自动取第一张 `desktop` 截图。
数组顺序同时是展示顺序。清单为空时整个「界面预览」区块会收起，不会出现占位文案。

## 站点图标

`public/icons/` 里的图标全部由 **Android App 的 launcher 图标**生成，不另外设计 Logo：

- 源文件：`android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- 处理：只裁掉自适应图标的透明留白并等比例缩放，不改动图形本身
- `apple-touch-icon.png` 用 App 图标的背景色 `#dcefdf` 补满方形，避免 iOS 二次圆角

| 文件                   | 尺寸     | 用途                      |
| ---------------------- | -------- | ------------------------- |
| `app-icon-512.png`     | 512      | 首页 Hero、`og:image`     |
| `app-icon-96.png`      | 96       | 页头品牌标记（显示 28px） |
| `favicon-192x192.png`  | 192      | favicon、webmanifest      |
| `favicon-32x32.png`    | 32       | favicon                   |
| `apple-touch-icon.png` | 180      | iOS 主屏图标              |
| `favicon.ico`          | 16/32/48 | 浏览器标签页              |

App 图标换新后，用同样的方式从新的 `ic_launcher.png` 重新导出即可。

## 下载数据

Release 数据有两层，职责分开：

**1. 静态 metadata（首选来源）**

构建阶段由 `scripts/generate-release-metadata.mjs` 读取 GitHub API，生成
`public/latest-release.json`，随站点一起发布：

```bash
pnpm metadata   # 本地手动重新生成
```

只写入 `tag`、`publishedAt`、`updatedAt`、`htmlUrl`、`generatedAt` 和
`assets[].name / size / downloadUrl`；**不写入 token、download_count、author、uploader**。

**2. 浏览器读取顺序**（`src/services/github.ts`）

1. 本次页面会话的请求备忘
2. 本站 `latest-release.json`（约 1KB、同源、`cache: "no-store"`，因此同一个 tag
   下重新上传 APK 后能立刻拿到新数据）
3. `sessionStorage` 缓存（两个来源都失败时的兜底）
4. GitHub API `releases/latest`
5. 都失败 → `index.html` 里的静态 fallback

之所以不让浏览器直接依赖 GitHub API：用户不一定能稳定访问 `api.github.com`，
而且未认证的 API 每个 IP 每小时只有 60 次。

**下载线路**（`src/services/downloadRoutes.ts`）只负责「从哪条线路下载」，
先取 `assets[].downloadUrl`，再按当前线路拼 URL；代理全部失败时回落到 GitHub 原始地址，
下载按钮不会消失。

## 部署

`.github/workflows/pages.yml` 只负责这个站点，与 `android-ci.yml`、`release.yml` 完全独立。
触发方式：

- push 到 `dev` 且变更命中 `website/**` 或该工作流文件
- `workflow_run`：`Android Release` 运行结束后（**这是 Release 更新后能自动同步的主路径**）
- `release`：`published` / `released` / `edited`，覆盖人工在网页上编辑 Release
- `workflow_dispatch`：手动触发

> 为什么必须挂 `workflow_run`：`release.yml` 用 `GITHUB_TOKEN` 创建/更新 Release，
> 而 `GITHUB_TOKEN` 触发的事件不会再启动新的 workflow；并且「替换已发布 Release 的附件」
> 本身不产生任何 `release` 事件。只监听 `release` 会漏掉重新上传 APK 的情况。

## 许可

与仓库一致，AGPL-3.0。
