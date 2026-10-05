/**
 * 「下载线路」选择控件。
 *
 * 只做展示与交互：列线路、报状态、把用户选择回传。
 * 不发起任何请求，探测与选线都在 services/downloadRoutes.ts 里。
 */

import {
  AUTO_ROUTE_ID,
  ORIGINAL_ROUTE_ID,
  getEnabledRoutes,
  getRoute,
} from "../data/downloadRoutes";

export interface RouteSelectorHandle {
  show(): void;
  setValue(routeId: string): void;
  showProbing(): void;
  showAutoResult(routeId: string, latency: number | null, allProxiesFailed: boolean): void;
  showManual(routeId: string): void;
}

function routeLabel(routeId: string): string {
  if (routeId === ORIGINAL_ROUTE_ID) return "GitHub 原始线路";
  return getRoute(routeId)?.label ?? routeId;
}

function optionText(routeId: string): string {
  const route = getRoute(routeId);
  if (!route) return routeId;
  return route.original ? route.label : `${route.label} · ${route.domain}`;
}

export function mountRouteSelector(
  onSelect: (routeId: string) => void,
): RouteSelectorHandle | null {
  const picker = document.getElementById("route-picker");
  const select = document.getElementById("route-select");
  const status = document.getElementById("route-status");

  if (!picker || !(select instanceof HTMLSelectElement) || !status) return null;

  const options = document.createDocumentFragment();

  const autoOption = document.createElement("option");
  autoOption.value = AUTO_ROUTE_ID;
  autoOption.textContent = "自动选择";
  options.append(autoOption);

  for (const route of getEnabledRoutes()) {
    const option = document.createElement("option");
    option.value = route.id;
    option.textContent = optionText(route.id);
    if (route.domain && !route.original) option.title = route.domain;
    options.append(option);
  }

  select.replaceChildren(options);
  select.value = AUTO_ROUTE_ID;

  const setStatus = (text: string): void => {
    status.textContent = text;
  };

  select.addEventListener("change", () => {
    onSelect(select.value);
  });

  return {
    show() {
      picker.hidden = false;
      document.getElementById("route-note")?.removeAttribute("hidden");
    },
    setValue(routeId) {
      select.value = routeId;
    },
    showProbing() {
      setStatus("正在检查下载线路…");
    },
    showAutoResult(routeId, latency, allProxiesFailed) {
      if (allProxiesFailed) {
        setStatus("已回退到 GitHub 原始线路");
        return;
      }
      if (routeId === ORIGINAL_ROUTE_ID) {
        setStatus("当前使用 GitHub 原始线路");
        return;
      }
      const suffix = latency === null ? "" : `（约 ${latency} ms）`;
      setStatus(`已自动选择 ${routeLabel(routeId)}${suffix}`);
    },
    showManual(routeId) {
      if (routeId === ORIGINAL_ROUTE_ID) {
        setStatus("当前使用 GitHub 原始线路");
        return;
      }
      setStatus(`当前：${routeLabel(routeId)}`);
    },
  };
}
