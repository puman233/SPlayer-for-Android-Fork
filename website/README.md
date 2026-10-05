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
│  ├─ icons/          复用仓库现有的 App Icon 与 favicon（不重新设计 Logo）
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

## 下载数据

- 接口：`https://api.github.com/repos/puman233/SPlayer-for-Android-Fork/releases/latest`
- 只读取 `tag_name`、`published_at`、`html_url`、`assets[].name`、`assets[].size`、
  `assets[].browser_download_url`
- **不读取也不展示 `download_count`** 或任何下载量统计
- 请求超时 7 秒，结果在 `sessionStorage` 缓存 30 分钟，失败不重试
- 失败时 `index.html` 里的静态 fallback（架构说明 + 前往 Releases 的入口）原样保留

## 部署

`.github/workflows/pages.yml` 只负责这个站点：`dev` 分支上 `website/**` 或该工作流文件变化时触发，
构建 `website/` 并发布到 GitHub Pages。它与 `android-ci.yml`、`release.yml` 完全独立。

## 许可

与仓库一致，AGPL-3.0。
