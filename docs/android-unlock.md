# Android 音源与嵌入式 API

## 请求链路

嵌入式 API 通过 `nodejs-mobile-cordova` 启动，入口位于 `API/mobile-entry.ts` 与 `API/mobile-server.ts`。前端初始化由 `src/utils/embeddedApi.ts` 管理，音源获取由 `src/core/player/SongManager.ts` 处理。

- 官方完整音源优先；官方返回试听或不可用时，按用户配置尝试替代音源。
- 替代音源不可用时，按播放设置回退到官方试听；试听地址不用于完整歌曲缓存或下载。
- 音源实现位于 `API/unblock/`，Android 路由为 `/api/netease/unblock/{server}`。
- 音源开关与顺序沿用设置状态，不因外部服务短暂故障永久禁用用户配置。

## 维护与构建

修改音源请求或嵌入式 API 后，在仓库根目录运行：

```bash
pnpm lint
pnpm build:android
```

该构建命令生成前端资源、同步 Capacitor 工程、打包 Node API 并准备嵌入式资源。随后在 `android/` 运行 `./gradlew assembleDebug`；Windows 使用 `gradlew.bat assembleDebug`。按 ABI 分包的产物位于 `android/app/build/outputs/apk/debug/`。

外部音源不可用时，检查对应实现的请求地址、响应结构、超时与回退逻辑。运行时诊断可按 `[unblock]` 日志前缀过滤；日志只在本地用于排查，不提交到仓库。

正式签名与自动发布配置见 [Android Release Secrets](../.github/ANDROID_RELEASE_SECRETS.md)，用户安装与通用构建说明见 [README](../README.md)。
