# 🎵 SPlayer for Android

### 未发布：手机播放器

手机横屏采用左侧封面与信息、右侧歌词、中央播放与进度、底部两侧操作组。控件在 2 秒无操作后淡出，点击空白恢复；拖动进度、打开菜单或队列期间保持显示。平板继续使用原来的播放器布局。

本轮验证与复现见 [手机横屏验收说明](docs/validation/phone-landscape.md)。

手机竖屏的信息顺序调整为标题、歌手、专辑、标签与操作。操作顺序为收藏、桌面歌词、添加歌单、队列、更多；空间不足只收纳添加歌单，桌面歌词与队列常驻。手机歌词页控件 2 秒无操作隐藏，点击空白恢复。平板继续使用原布局。本阶段最新验收见 [竖屏修订说明](docs/validation/phone-portrait-revision.md)。

本地 API 异常时自动尝试服务热重载，并合并并发恢复；外网超时只提示一次，避免反复弹出相同故障提示。

桌面歌词默认只显示歌词；普通触摸切换控制显隐，2.5 秒无操作后控件与背景同步渐隐。锁定后从独立解锁按钮恢复触碰。开启文字背景遮罩时也不会在待机或锁定后留下一块背景。最新验证见 [实际触碰与 2.5 秒渐隐](docs/validation/desktop-touch-2500.md)。

手机歌词页隐藏控件后，歌词向上下扩展至安全区域，显示更多上下文；点击空白恢复控制布局。桌面歌词普通触碰可切换控制显隐，按钮点击继续执行操作，拖动与锁定行为保留。最新验证见 [歌词扩展与触碰切换](docs/validation/lyrics-expansion-toggle.md)。

### 桌面歌词与手机横屏（v3.0.11）

[v3.0.11 正式安装包](https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.11)已发布，提供四种 ABI；正式签名保持兼容，可原位升级。

桌面歌词首次使用自动字号与 `#6BB2FF`；手动修改字号后会保存，旋转不覆盖用户值，可在歌词设置中恢复自动字号。旧版已有字号和颜色保守保留。长句保持字号并横向滚动，暂停冻结、seek 和切歌重新从起点显示。

触摸歌词显示控制，闲置自动淡出；锁定后主窗口触摸穿透并快速消除控制背景，独立锁按钮负责解锁。窗口按系统安全区保存相对位置，旋转后重新约束边界。手机横屏保留完整播放控件与歌词菜单；长歌曲信息和极窄空间中的次要操作支持局部滚动。

专项实现、测试边界与历史 Android Lint 问题记录在 [重构方案](DESKTOP_LYRICS_REFACTOR_PLAN.md) 和 [执行检查点](AGENT_CHECKPOINT.md)。

<p align="center">
  <img src="https://img.shields.io/badge/version-3.0.11-blue?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/platform-Android%2010%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="platform">
  <img src="https://img.shields.io/badge/license-AGPL--3.0-red?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/Vue-3-4FC08D?style=flat-square&logo=vue.js&logoColor=white" alt="vue">
  <img src="https://img.shields.io/badge/Capacitor-8-119EFF?style=flat-square&logo=capacitor&logoColor=white" alt="capacitor">
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white" alt="typescript">
</p>

> **SPlayer for Android 是一款第三方非官方移植版 Android 音乐播放器**，基于 [SPlayer](https://github.com/SPlayer-Dev/SPlayer) 与 [SPlayer-Next](https://github.com/SPlayer-Dev/SPlayer-Next) 二次开发，仅用于**个人学习与练习**，与 SPlayer 原项目无任何关联。

---

## ⚠️ 免责声明 (Disclaimer)

- **非官方性质**：本项目是**第三方非官方移植版**，与 [SPlayer](https://github.com/SPlayer-Dev/SPlayer) / [SPlayer-Next](https://github.com/SPlayer-Dev/SPlayer-Next) 原项目及其开发者无任何直接关联，亦未获其任何形式的认可或背书。
- **API 使用风险**：本项目可能调用第三方 API（如网易云音乐等）的非官方接口，其稳定性与可用性不受本项目控制，可能随时失效、变更或产生异常，请自行承担相应风险。
- **责任归属**：本项目仅用于**个人学习与练习**。因使用本项目（包括但不限于播放、下载、解锁等操作）而引发的任何直接或间接损失、纠纷或法律责任，均由使用者自行承担，项目作者不承担任何责任。
- **商业使用风险**：请勿将本项目用于商业用途或盈利行为。AGPL-3.0 协议虽允许商业使用，但本项目作者明确声明仅限学习交流，若违反由此产生的法律风险由使用者自行承担。

> 音乐资源版权归各版权方所有，请支持正版音乐；涉及解锁/试听相关的音源接口，请遵守当地法律法规并仅用于个人学习。

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
| 🪟 **桌面歌词**    | `WindowManager` 悬浮窗，逐字动画、锁定穿透、拖拽、播控                                |
| 🔔 **通知栏**      | 原生 `MediaSession`，完整播控，支持桌面歌词一键开关                                   |
| 🎚️ **精细控制**    | 渐入渐出、进度吸附歌词、允许与其他应用同时播放                                        |
| 🌐 **在线音乐**    | 网易云 + Jellyfin / Navidrome / Emby / Subsonic / OpenSubsonic / Last.fm              |
| 🔗 **网络代理**    | 支持配置 HTTP/HTTPS 代理，内置逆向 API 请求可走代理访问网易云接口                     |
| ⬇️ **音乐下载**    | 开发者模式下可下载歌曲至 SAF 授权目录，支持自定义子目录分类、歌词/ASS 附件下载        |
| 🧩 **内置 API**    | `nodejs-mobile-cordova` 嵌入网易云 API，离线可用                                      |
| 📦 **分架构打包**  | `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` 独立 APK                               |

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

前往 [**Releases**](../../releases) 选择对应 CPU 架构的 APK：

|       ABI       | 适用设备                       |   推荐度   |
| :-------------: | ------------------------------ | :--------: |
| **`arm64-v8a`** | 绝大多数现代手机 / 平板        | ⭐⭐⭐⭐⭐ |
|  `armeabi-v7a`  | 2015 年前的老旧 32 位 ARM 设备 |    ⭐⭐    |
|    `x86_64`     | Intel 平板、Android 模拟器     |     ⭐     |
|      `x86`      | 极少数 32 位 Intel 设备        |     ⭐     |

> 💡 不清楚自己设备架构？装个 **CPU-Z**，或者无脑选 `arm64-v8a`——99% 都是它。

也可以在「设置 → 关于软件 → 检查更新」中由应用自动选择匹配 ABI。应用会显示包体大小和下载进度，强制核对 GitHub Release 的 SHA-256，并在包名、版本和签名证书均可升级后调用 Android 系统安装器。首次使用需按系统提示允许此来源安装，最终安装仍须由用户确认。

---

## 🆕 更新日志

> 完整更新记录请查看 [**CHANGELOG.md**](./CHANGELOG.md)

---

## 🚀 快速开始

### 环境要求

| 工具              | 版本    |
| ----------------- | ------- |
| Node.js           | `>= 20` |
| pnpm              | `>= 10` |
| JDK               | `21`    |
| Android SDK / NDK | 最新    |

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
npx cap sync android            # 同步 Capacitor 工程
npx cap open android            # Android Studio 打开
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

推送 `v*` Tag 时，[Android Release](./.github/workflows/release.yml) 校验版本与正式签名，执行 `:app:assembleRelease` 并发布所有 ABI 的 APK。Tag 去掉 `v` 后必须与 Android `versionName`、`package.json.version` 完全一致，例如当前版本使用 `v3.0.11`。包含 `alpha`、`beta` 或 `rc` 的 Tag 自动标记为预发布，其余默认正式发布。

附件沿用 `app-arm64-v8a-release.apk` 等 Gradle 最终文件名。发布正文使用下载与安装表格、当前版本的分类更新日志和 Full Changelog 链接，另保留 GitHub 自动生成记录。已发布的 Tag 自动跳过；失败留下的草稿可安全重跑，不会重复上传相同附件。

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

### 👥 贡献者

感谢每一位为本项目付出时间与代码的伙伴 ❤️

<a href="https://github.com/SPlayer-Dev/SPlayer-for-Android/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=SPlayer-Dev/SPlayer-for-Android" alt="contributors">
</a>

> 图片由 [contrib.rocks](https://contrib.rocks) 自动生成，随 GitHub 贡献图谱更新。

---

## 🤝 致谢

本项目基于 [**SPlayer**](https://github.com/imsyy/SPlayer) 移植，向原作者 [@imsyy](https://github.com/imsyy) 与所有贡献者致以最诚挚的感谢 ❤️

---

## 📄 许可证

本项目基于 [**AGPL-3.0**](./LICENSE)（GNU Affero General Public License v3.0）开源协议发布，与上游保持一致。

- **原项目版权**：原 [SPlayer](https://github.com/SPlayer-Dev/SPlayer) / [SPlayer-Next](https://github.com/SPlayer-Dev/SPlayer-Next) 版权归原开发者团队所有，本版本保留其原始版权声明。
- **修改说明**：本版本为 Android 平台移植版，已对前端与原生层进行适配和优化，属 AGPL-3.0 协议下的衍生作品。

**AGPL-3.0 核心要求**：

- 🔓 **开源**：任何修改、衍生或分发本项目的作品，必须同样以 AGPL-3.0 协议开源，并完整提供对应的源代码。
- ©️ **保留版权**：不得移除或修改原始版权声明与许可信息。
- 📦 **提供源码**：向使用者分发本项目或其修改版本时，须同时提供可获取的源代码，或提供指向源代码的明确链接。

完整条款请参阅 [LICENSE](./LICENSE) 文件或 [GNU AGPL-3.0 官方文本](https://www.gnu.org/licenses/agpl-3.0.html)。

## 手机播放页交互

- 评论页仅显示评论内容与歌曲卡片，不显示进度、播放按钮或分页点；左右滑动返回歌曲或歌词页。
- 歌词页控件与评论页歌曲卡片在无操作 4 秒后淡出，点击页面恢复。拖动歌词、进度条或打开快捷菜单期间保持显示，结束后重新计时；自动滚词不会重置计时。
- 主播放页顶栏、内容、底栏均参与布局，封面随剩余空间收缩，过多的歌曲信息独立滚动；长标题、歌手与专辑名省略，安全区沿用 Android / WebView inset。

### Android 设备回归测试

`FloatingLyricOverlayTest` 和 `PlayerDeviceLayoutTest` 仅允许 `.lyricsverify` 隔离包运行。使用 `-PverificationSuffix=.lyricsverify` 构建测试包，不能对已有正式包清理数据。MuMu 手机/平板补充验收与验证边界见 [AGENT_CHECKPOINT.md](AGENT_CHECKPOINT.md)。
