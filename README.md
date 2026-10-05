# 🎵 SPlayer for Android Fork

<p align="center">
  <img src="https://img.shields.io/badge/version-3.0.14-blue?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/platform-Android%2010%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="platform">
  <img src="https://img.shields.io/badge/license-AGPL--3.0-red?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/Vue-3-4FC08D?style=flat-square&logo=vue.js&logoColor=white" alt="vue">
  <img src="https://img.shields.io/badge/Capacitor-8-119EFF?style=flat-square&logo=capacitor&logoColor=white" alt="capacitor">
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="typescript">
</p>

> **SPlayer for Android Fork** 是基于 [SPlayer-Dev/SPlayer-for-Android](https://github.com/SPlayer-Dev/SPlayer-for-Android) 的个人维护分支，在其 Android 移植版本基础上继续进行功能修复、适配与扩展。上游 Android 项目源自 [SPlayer](https://github.com/SPlayer-Dev/SPlayer)。
>
> 本项目为社区非官方 Fork，与 SPlayer / SPlayer for Android 上游开发团队不存在官方隶属、合作、授权背书或认可关系。

---

## ⚠️ 免责声明 (Disclaimer)

- **非官方性质**：本项目为独立维护的社区 Fork，与上游存在代码及 Git 历史上的派生关系，但不代表上游团队，亦未获其认可或背书。
- **开源许可证**：本项目依据 AGPL-3.0 提供。AGPL-3.0 本身允许包括商业使用在内的使用方式；修改、传播、分发及其他适用场景应遵守许可证对应义务。
- **第三方服务与内容**：本项目可能使用网易云音乐等第三方服务、接口或内容，其可用性与稳定性不受本项目控制。这些服务、接口、音乐资源、商标及其他内容受各自服务条款、版权及其他法律约束。AGPL-3.0 对本项目源代码的授权，不代表使用者自动取得第三方 API、音乐内容、商标或其他第三方资源的商业使用权。
- **风险承担**：使用者应自行确认其使用方式符合当地法律法规及相关第三方服务条款，并承担相应使用风险。

> 音乐资源版权归各版权方所有，请支持正版音乐；涉及解锁／试听相关音源接口时，应遵守适用法律法规及第三方服务条款。

---

## ✨ 特性一览

| 分类               | 描述                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------- |
| 🎨 **双端 UI**     | 手机 / 平板自适应布局，Cover / Lyrics 共用稳定播放底栏，旋屏后自动恢复与安全区避让    |
| 📐 **手机端布局**  | 全宽贴底底栏 + 圆角浮岛播放栏，底栏选中态滑动指示器；列表 / 卡片 / 设置项轻度紧凑     |
| 🔍 **页面缩放**    | 50%–150% 全局缩放，按实际可见视口适配播放页、设置页、弹层和底部导航                   |
| 🎵 **播放引擎**    | 原生 ExoPlayer + WebView 双引擎，弱网有界退避与断点恢复，seek / gapless 进度同步      |
| 📝 **逐字歌词**    | 毫秒级插值高亮，翻译 / 罗马音，拖动吸附最近行，支持全局歌词偏移                       |
| 📂 **本地音乐**    | 自动扫描本地歌曲，支持 TTML / LRC 歌词匹配（同目录 / 独立歌词目录），与桌面端行为对齐 |
| ☁️ **WebDAV 音乐** | 通过 WebDAV 连接远程音乐，在线播放与浏览，扩展私人曲库                                |
| 🪟 **桌面歌词**    | 悬浮歌词、逐字动画、锁定穿透、拖动、播控与自动字号                                    |
| 🔔 **通知栏**      | 原生 `MediaSession`，完整播控，支持桌面歌词一键开关                                   |
| 🎚️ **精细控制**    | 渐入渐出、进度吸附歌词、允许与其他应用同时播放                                        |
| 🌐 **在线音乐**    | 网易云 + Jellyfin / Navidrome / Emby / Subsonic / OpenSubsonic / Last.fm              |
| 🔗 **网络代理**    | 支持配置 HTTP/HTTPS 代理，内置逆向 API 请求可走代理访问网易云接口                     |
| ⬇️ **音乐下载**    | 开发者模式下可下载歌曲至 SAF 授权目录，支持自定义子目录分类、歌词/ASS 附件下载        |
| 🧩 **内置 API**    | `nodejs-mobile-cordova` 嵌入网易云 API，离线可用                                      |
| 📦 **分架构打包**  | `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` 独立 APK                               |

手机横屏采用封面与歌词双栏布局，评论展开时使用完整内容宽度；按钮分组随内容收紧，减少底部预留空白并保留系统安全区域。播放控件支持自动隐藏，点击空白恢复；手机竖屏各页保持一致布局，歌词隐藏控件后显示更多内容，当前行位置保持稳定，封面与控件同步渐隐。窄屏主页推荐卡片纵向排列。桌面歌词支持触摸切换控制显隐，无操作后控件与背景自动淡出，锁定后可通过独立按钮解锁。

---

## 📱 平台兼容性

**最低要求：Android 10（API 29）**

> ⚠️ 更早的 Android 版本（Android 9 及以下）没有计划去实现支持。原因是：
>
> - 旧版 Android 性能不足以流畅运行本应用的 WebView 渲染和音频处理
> - Android 10 以下可用的系统 API 较少，无法实现 SAF 文件访问、MediaStyle 通知、深色模式适配等核心功能
> - 这些版本的设备硬件早已过时，完全不适合运行本应用

---

## 📦 下载与安装

前往 [**Releases**](https://github.com/puman233/SPlayer-for-Android-Fork/releases) 选择对应 CPU 架构的 APK：

|       ABI       | 适用设备                       |   推荐度   |
| :-------------: | ------------------------------ | :--------: |
| **`arm64-v8a`** | 绝大多数现代手机 / 平板        | ⭐⭐⭐⭐⭐ |
|  `armeabi-v7a`  | 2015 年前的老旧 32 位 ARM 设备 |    ⭐⭐    |
|    `x86_64`     | Intel 平板、Android 模拟器     |     ⭐     |
|      `x86`      | 极少数 32 位 Intel 设备        |     ⭐     |

> 💡 不清楚自己设备架构？装个 **CPU-Z**，或者无脑选 `arm64-v8a`——99% 都是它。

也可以在「设置 → 关于软件 → 检查更新」中由应用自动选择匹配 ABI。应用会显示包体大小和下载进度，校验 GitHub Release 提供的文件摘要，并在包名、版本和签名证书均可升级后调用 Android 系统安装器。首次使用需按系统提示允许此来源安装，最终安装仍须由用户确认。

APK 下载优先使用 [GH-Proxy](https://gh-proxy.com/) 加速入口，失败后依次尝试备用源和 GitHub 原地址。版本与文件摘要仍读取 GitHub 官方接口；按摘要区分同版本替换后的附件，下载后继续严格校验完整性与签名。

---

## 🆕 更新日志

> 完整更新记录请查看 [**CHANGELOG.md**](./CHANGELOG.md)

---

## 🚀 快速开始

### 环境要求

| 工具              | 版本                       |
| ----------------- | -------------------------- |
| Node.js           | `>= 20`                    |
| pnpm              | `>= 10`                    |
| JDK               | `21`                       |
| Android SDK / NDK | API 36 / NDK 28.2.13676358 |

### 一键构建

```bash
pnpm install
pnpm build:android
cd android && ./gradlew assembleDebug
```

产物：`android/app/build/outputs/apk/debug/`（按 ABI 分包）

Debug 包使用 `top.imsyy.splayer.android.debug` 包名，可直接安装并与正式版并存，适合本地验证。正式发布必须通过 GitHub Actions Secrets，或在工作区外准备 keystore 并通过已忽略的 `android/key.properties` 注入签名参数；没有正式签名材料时，不应把 Debug 签名产物用于覆盖安装或公开发布。

### 分步构建

```bash
pnpm build:web                  # 前端 Vite 构建
pnpm build:android:node         # 内置 Node API Bundle
pnpm prepare:android:embedded   # 准备嵌入资源
pnpm exec cap sync android      # 同步 Capacitor 工程
pnpm exec cap open android      # Android Studio 打开
```

---

## ❓ FAQ

<details>
<summary><b>🔑 桌面歌词（悬浮歌词）怎么用？</b></summary>

<br>

> 在 **设置 → 歌词 → 桌面歌词** 打开。首次开启会弹出悬浮窗权限申请（跳转系统设置授权），授权后歌词即可显示在其他应用上层。若拒绝授权，该功能将不可用。

</details>

<details>
<summary><b>🎧 想和其他应用同时播放？</b></summary>

<br>

进入 **设置 → 播放 → Android 系统设置 → 允许与其他应用同时播放**。

> ⚠️ 注意：开启后将切换为 ExoPlayer 引擎（handleAudioFocus=false），不再抢占音频焦点，可与其他应用同时播放。

</details>

<details>
<summary><b>📦 我该下载哪个 APK？</b></summary>

<br>

无脑选 **`arm64-v8a`**。只有极老的设备（通常 2015 年前）才需要 `armeabi-v7a`。`x86` / `x86_64` 几乎只用于模拟器。

</details>

<details>
<summary><b>🖥️ 能在电脑上运行吗？</b></summary>

<br>

本仓库已剥离 Electron 桌面端打包链路，**仅保留 Android 构建**。需要桌面版请前往 [原版 SPlayer](https://github.com/imsyy/SPlayer)。

</details>

<details>
<summary><b>🔕 通知栏控制器不显示？</b></summary>

<br>

1. **设置 → 播放** 里打开 **"通知栏音乐控制器"**
2. 系统设置里允许 SPlayer 发送通知（Android 13+ 首次启用会弹权限框）

</details>

<details>
<summary><b>🌙 支持后台播放吗？</b></summary>

<br>

支持。通知栏控制器启用后，应用进入后台仍会继续播放，不会被系统快速回收。

</details>

<details>
<summary><b>📂 本地歌词不显示？</b></summary>

<br>

1. 进入 **设置 → 本地与缓存 → 本地歌词覆盖在线歌词**，添加歌词所在目录
2. 歌词文件可按 `歌曲ID.ttml` / `歌曲ID.lrc` 或 `歌名.歌曲ID.ttml` / `歌名.歌曲ID.lrc` 命名
3. TTML 文件内含 `ncmMusicId` 元数据的也可自动匹配
4. 放在音乐同目录下、与音频同名的歌词文件（如 `歌曲名.lrc`）也会自动识别

</details>

<details>
<summary><b>⬇️ 如何下载歌曲？</b></summary>

<br>

1. 进入 **设置 → 常规**，连续点击版本号 5 次开启开发者模式
2. 在 **设置 → 本地与缓存 → 下载配置** 中选择下载目录（SAF 授权）
3. 在歌曲列表中点击菜单即可看到下载选项

</details>

---

## 🤖 CI / 发布

推送到 `dev` 或向 `dev` 提交 PR 时，[Android CI](./.github/workflows/android-ci.yml) 自动构建 Web/嵌入资源，并执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`，不会创建 Release。

推送 `v*` Tag 时，[Android Release](./.github/workflows/release.yml) 校验版本与正式签名，执行 `:app:assembleRelease` 并发布所有 ABI 的 APK。Tag 去掉 `v` 后必须与 Android `versionName`、`package.json.version` 完全一致，例如版本 `3.0.14` 对应 Tag `v3.0.14`。包含 `alpha`、`beta` 或 `rc` 的 Tag 自动标记为预发布，其余默认正式发布。

附件沿用 `app-arm64-v8a-release.apk` 等 Gradle 最终文件名。发布正文使用下载与安装表格、当前版本的分类更新日志和 Full Changelog 链接，另保留 GitHub 自动生成记录。已发布的 Tag 自动跳过；失败留下的草稿可安全重跑，不会重复上传相同附件。

同版本修复通过手动运行该工作流，填写现有 Tag、`repair_ref` 完整提交 SHA 和 `replace_release_id`。校验目标身份后先备份旧附件到 Actions artifact，再替换现有 Release；原 Tag 保留，Android 构建号递增以支持覆盖安装。

MuMu 手机与平板使用隔离 debug 包测试，后续版本使用 `adb install -r` 覆盖安装并保留数据，不卸载测试应用。必要时可利用已开放的 root 做诊断，正式应用数据不参与破坏性测试。

签名所需 Secrets：

| Secret                      | 说明                    |
| --------------------------- | ----------------------- |
| `ANDROID_KEYSTORE_BASE64`   | Keystore 的 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore 密码           |
| `ANDROID_KEY_ALIAS`         | Key 别名                |
| `ANDROID_KEY_PASSWORD`      | Key 密码                |

详细说明见 [`.github/ANDROID_RELEASE_SECRETS.md`](./.github/ANDROID_RELEASE_SECRETS.md)。

---

## 🧑‍💻 参与贡献

欢迎提 Issue 与 PR！提交前请先阅读 [**CONTRIBUTING.md**](./CONTRIBUTING.md)，了解 Issue / PR / Commit 规范。

- 🐞 [提交 Bug](../../issues/new?template=bug.yml)
- ✨ [提交功能建议](../../issues/new?template=feature.yml)
- 📮 [发起 Pull Request](../../compare)

### 👥 上游贡献者

感谢 SPlayer for Android 与 SPlayer 的开发者及所有上游贡献者。下图展示直接上游 SPlayer for Android 的贡献者。

<a href="https://github.com/SPlayer-Dev/SPlayer-for-Android/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=SPlayer-Dev/SPlayer-for-Android" alt="SPlayer for Android 上游贡献者">
</a>

> 图片由 [contrib.rocks](https://contrib.rocks) 自动生成，随上游仓库的 GitHub 贡献图谱更新。

---

## 🤝 致谢

本项目 Fork 自 [SPlayer-Dev/SPlayer-for-Android](https://github.com/SPlayer-Dev/SPlayer-for-Android)，并在其 Android 移植版本基础上继续开发。SPlayer for Android 源自 [SPlayer](https://github.com/SPlayer-Dev/SPlayer)。

感谢 SPlayer for Android 的维护者、SPlayer 原作者 [@imsyy](https://github.com/imsyy) 及所有上游贡献者提供的代码、设计与维护工作。

---

## 📄 许可证

本项目基于 [**AGPL-3.0**](./LICENSE)（GNU Affero General Public License v3.0）开源协议发布，与上游保持一致。

- **上游版权**：本项目保留 [SPlayer for Android](https://github.com/SPlayer-Dev/SPlayer-for-Android) 与 [SPlayer](https://github.com/SPlayer-Dev/SPlayer) 的原始版权声明及许可信息。
- **修改说明**：本 Fork 在上游 Android 移植版本基础上进行修复、适配与扩展，属 AGPL-3.0 协议下的衍生作品。

修改、传播、分发或以网络服务方式提供本项目时，应根据 AGPL-3.0 的适用条款履行源代码提供、许可证及版权声明保留，以及其他相关义务。

具体权利与义务以仓库中的 [LICENSE](./LICENSE) 正文为准；亦可参阅 [GNU AGPL-3.0 官方文本](https://www.gnu.org/licenses/agpl-3.0.html)。
