import { defineConfig } from "vite";

/**
 * 站点部署在 GitHub Pages 的项目子路径下：
 * https://puman233.github.io/SPlayer-for-Android-Fork/
 * 所有资源引用都必须经过 Vite 的 base 处理，避免硬编码 /assets/xxx。
 */
export default defineConfig({
  base: "/SPlayer-for-Android-Fork/",
  publicDir: "public",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    target: "es2020",
    cssCodeSplit: false,
    sourcemap: false,
  },
  server: {
    port: 5174,
  },
  preview: {
    port: 4174,
  },
});
