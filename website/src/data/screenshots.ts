/**
 * 界面预览的真实 App 截图清单。
 *
 * 截图文件放在 `website/public/screenshots/`，全部由真机 / 实际运行画面导出，
 * 只做过等比例缩放与 WebP 压缩，没有裁改界面内容。
 *
 * 新增截图：把文件放进该目录，然后在下面的数组里登记文件名与真实像素尺寸。
 * `width` / `height` 用于写进 <img> 属性，避免图片加载时布局跳动。
 *
 * 首页右侧使用两张：`hero: true` 的手机竖屏截图，以及第一张宽屏（category: "desktop"）截图。
 * 数组顺序同时也是「界面预览」的展示顺序。
 */
export type ScreenshotCategory = "mobile" | "landscape" | "desktop";

export interface ScreenshotEntry {
  /** public/screenshots/ 下的文件名 */
  readonly file: string;
  /** 展示标题，同时用于生成图片的 alt 文本 */
  readonly title: string;
  /** 可选的一句话说明 */
  readonly caption?: string;
  /** 图片真实像素宽度 */
  readonly width: number;
  /** 图片真实像素高度 */
  readonly height: number;
  readonly category: ScreenshotCategory;
  /** 标记为首页右侧使用的手机截图 */
  readonly hero?: boolean;
}

export const SCREENSHOTS: readonly ScreenshotEntry[] = [
  {
    file: "desktop-home.webp",
    title: "桌面界面",
    caption: "宽屏下的侧栏、主页与底部播放栏",
    width: 1600,
    height: 904,
    category: "desktop",
  },
  {
    file: "mobile-home.webp",
    title: "手机首页",
    caption: "推荐、专属歌单与迷你播放栏",
    width: 720,
    height: 1584,
    category: "mobile",
  },
  {
    file: "desktop-lyrics.webp",
    title: "宽屏歌词",
    caption: "封面信息与逐字歌词并列",
    width: 1600,
    height: 904,
    category: "desktop",
  },
  {
    file: "mobile-player.webp",
    title: "手机播放页",
    caption: "封面、音质标签与播放控件",
    width: 720,
    height: 1584,
    category: "mobile",
  },
  {
    file: "desktop-floating-lyrics.webp",
    title: "桌面歌词",
    caption: "悬浮歌词窗，可拖动、锁定与播控",
    width: 1600,
    height: 904,
    category: "desktop",
  },
  {
    file: "mobile-lyrics.webp",
    title: "手机歌词页",
    caption: "逐字高亮、翻译与罗马音",
    width: 720,
    height: 1584,
    category: "mobile",
    hero: true,
  },
  {
    file: "landscape-player.webp",
    title: "横屏播放",
    caption: "手机横屏时封面与歌词分栏排布",
    width: 1800,
    height: 818,
    category: "landscape",
  },
];
