/**
 * 下载线路集中配置。
 *
 * 线路全部固定在本文件里，不从任何远程接口 / Gist / CDN 动态获取，避免供应链问题。
 * 下线某条线路只需把 `enabled` 改成 false，不需要删代码。
 *
 * `autoProbe` 依据 2026-10-05 的真机浏览器实测结果：
 *   - GitHub 原始线路：release 下载路径在本机网络不可达（curl 直连 21s 超时，code=000），
 *     且 GitHub Release 资源本身不返回 Access-Control-Allow-Origin，
 *     浏览器 fetch 永远无法读取响应 → 不进入自动池，仅作为原始来源与手动线路保留。
 *   - 加速线路 1 / 2 / 4：Range 返回 206 + Content-Range，且带 ACAO: * → 进入自动池。
 *   - 加速线路 3：域名无法解析（NXDOMAIN）→ enabled: false。
 *
 * 关于 `no-cors`：本文件与探测逻辑都不会把 opaque response 当作测速成功；
 * opaque 仅在可读探测失败后用来区分「被 CORS 拦」与「根本连不上」，且只影响标签。
 */

export interface DownloadRouteConfig {
  readonly id: string;
  /** 界面主标签 */
  readonly label: string;
  /** 域名，仅作为小字提示，不做视觉主体 */
  readonly domain: string;
  /** 代理前缀；null 表示使用 GitHub 原始地址 */
  readonly baseUrl: string | null;
  readonly enabled: boolean;
  /** 是否参与自动探测与自动推荐 */
  readonly autoProbe: boolean;
  /** 延迟接近时的固定稳定性优先级，数字越小越优先 */
  readonly priority: number;
  /** 是否为 GitHub 原始线路 */
  readonly original: boolean;
}

export const DOWNLOAD_ROUTES: readonly DownloadRouteConfig[] = [
  {
    id: "github",
    label: "GitHub",
    domain: "github.com",
    baseUrl: null,
    enabled: true,
    autoProbe: false,
    priority: 0,
    original: true,
  },
  {
    id: "gh-proxy",
    label: "加速线路 1",
    domain: "gh-proxy.org",
    baseUrl: "https://gh-proxy.org/",
    enabled: true,
    autoProbe: true,
    priority: 1,
    original: false,
  },
  {
    id: "gh-monlor",
    label: "加速线路 2",
    domain: "gh.monlor.com",
    baseUrl: "https://gh.monlor.com/",
    enabled: true,
    autoProbe: true,
    priority: 2,
    original: false,
  },
  {
    // 2026-10-05 实测：gh.jasonzeng.dev 本地与公共解析均返回 NXDOMAIN，
    // 域名当前不可用。保留配置，恢复解析后把 enabled 改回 true 即可重新上线。
    id: "gh-jasonzeng",
    label: "加速线路 3",
    domain: "gh.jasonzeng.dev",
    baseUrl: "https://gh.jasonzeng.dev/",
    enabled: false,
    autoProbe: false,
    priority: 3,
    original: false,
  },
  {
    id: "gh-imciel",
    label: "加速线路 4",
    domain: "ghproxy.imciel.com",
    baseUrl: "https://ghproxy.imciel.com/",
    enabled: true,
    autoProbe: true,
    priority: 4,
    original: false,
  },
];

/** 「自动选择」不是真实线路，用一个固定 id 表示 */
export const AUTO_ROUTE_ID = "auto";

/** 原始线路 id，任何情况下都必须保留 */
export const ORIGINAL_ROUTE_ID = "github";

export function getRoute(id: string): DownloadRouteConfig | undefined {
  return DOWNLOAD_ROUTES.find((route) => route.id === id);
}

/** 界面上可选的线路（已下线的不展示） */
export function getEnabledRoutes(): readonly DownloadRouteConfig[] {
  return DOWNLOAD_ROUTES.filter((route) => route.enabled);
}

/** 参与自动探测的线路 */
export function getProbeRoutes(): readonly DownloadRouteConfig[] {
  return DOWNLOAD_ROUTES.filter((route) => route.enabled && route.autoProbe);
}

/** 所有代理都不可用时的兜底线路 */
export function getOriginalRoute(): DownloadRouteConfig {
  const route = getRoute(ORIGINAL_ROUTE_ID);
  if (!route) throw new Error("原始线路配置缺失");
  return route;
}

export function isSelectableRoute(id: string): boolean {
  return id === AUTO_ROUTE_ID || getEnabledRoutes().some((route) => route.id === id);
}
