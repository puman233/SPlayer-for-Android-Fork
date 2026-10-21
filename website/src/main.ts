import "./styles/main.css";

import { mountGallery } from "./components/gallery";
import { mountNav } from "./components/nav";
import { mountReleasePanel } from "./components/release";
import { mountTheme } from "./components/theme";

function boot(): void {
  mountTheme();
  mountNav();
  mountGallery();
  mountReleasePanel();
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", boot, { once: true });
} else {
  boot();
}
