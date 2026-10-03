# Android 自动发布工作流检查点

日期：2026-10-03；分支：dev。

## 工作流（VERIFIED）

- Android CI：dev push/PR，JDK 21、pnpm 10.28.1、Gradle 缓存，资源构建、发布保护测试、ESLint、debug 编译/单元测试/Android Lint。
- Tag Release：v\* 触发，检出 Tag，正式签名 Secrets 注入，Gradle 版本校验，所有 release ABI/flavor 元数据收集，真实 APK 清单与证书校验。
- Release 幂等处理：同 Tag 串行，已公开 Release 跳过，草稿上传完成才公开；已有附件校验 SHA-256，不覆盖。
- 已替换原手动 android-release.yml；配置说明在 .github/ANDROID_RELEASE_SECRETS.md。

## VERIFIED

- 三个 Actions YAML 语法、触发条件、CI 只读/发布写权限及格式检查。
- Python 语法与 12 项发布保护测试，包括草稿 API 回退、分页、权限错误及恢复时的 Tag 提交检查。
- pnpm lint：通过；pnpm build:android：通过；pnpm format：通过。清理构建生成及全库格式化带来的无关已跟踪文件变化。
- 实际 Gradle 命令 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug：通过（首次 offline 因测试依赖缺缓存失败，联网解析后通过）。
- 临时测试证书的 :app:verifyReleaseConfiguration :app:assembleRelease：通过，四个 ABI 均生成 APK。
- 实际收集脚本：四个 APK 清单、版本与签名验证通过（本机验证只适配了 Windows 工具路径，CI 原脚本使用 Linux 工具）。
- Gradle 负向验证：v1.3.0 与 versionName=3.0.8 不一致时明确失败。
- 没有已跟踪 keystore/key.properties；临时测试签名材料与测试复制附件已清理。

## GitHub 实际发布（VERIFIED）

- 已确认四项 Secrets 已配置并由 runner 实际用于正式签名，没有读取或导出 Secrets 内容。
- dev CI：37095215774、修复后的 37096060939 均实际通过。首次环境配置失败为 setup-android 默认请求已移除 tools 包，已显式配置 platform-tools 修复。
- v3.0.9 Tag 保持指向 d6cf53c2；版本为 3.0.9 / 30015。首次 Release 37095509169 因按 Tag API 未返回新草稿而失败，留下空草稿。修复发布工具位于 dev 提交 a97c56f1。
- 恢复发布 37096228847（workflow_dispatch）成功，原草稿转为正式公开 Release：draft=false、prerelease=false、标题 v3.0.9，自动生成 Release Notes。
- 正式附件只有四个 APK：SFA-3.0.9-release-abi-arm64-v8a.apk（60001030 字节）、armeabi-v7a（59205372）、x86（62307588）、x86_64（65554677）。不含 debug 或中间文件。
- 下载正式 arm64 APK 并检查 SHA-256 与 GitHub 附件 digest 相同，真实清单为 versionName=3.0.9、versionCode=30015、非 debuggable；证书与 v3.0.8 相同，SHA-256 为 d065190eb0f517db9f8575030ae5610615d2655b219bff48efcfe33d612e5fea。
- 同一发布运行的 attempt 2 实际成功，构建和上传均跳过；四个附件 ID、数量、大小及哈希均未变化，确认没有重复上传。
- 本机 build 目录中的先前 release APK 使用临时测试证书，不属于正式发布产物；正式安装包请从下方 Release 下载。下载核验使用的临时文件已清理。

发布地址：https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.9

CI：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37096060939

发布/重跑：https://github.com/puman233/SPlayer-for-Android-Fork/actions/runs/37096228847

## 下一步

本轮发布完成。下次修改 Android 与前端版本并递增 versionCode，推送 dev 等待 CI，通过后推送新 Tag。若旧 Tag 发布工具需要修复，使用 dev 的 Android Release 手动恢复入口，保留旧 Tag 源码。

## 发布格式修正（2026-10-03，VERIFIED）

- 已读取 v3.0.8、v3.0.7 的真实发布正文及附件名称；v3.0.9 现在沿用下载与安装表格、分类更新日志、Full Changelog 链接，并保留自动生成的版本对比链接。
- v3.0.9 四个附件已原地改名为 app-arm64-v8a-release.apk、app-armeabi-v7a-release.apk、app-x86-release.apk、app-x86_64-release.apk；Release ID、附件 ID、字节大小、SHA-256、正式状态及 Tag 提交均保持不变，没有重建或重新上传 APK。
- 后续工作流保留 AGP 最终 APK 文件名；发布模板为 .github/RELEASE_TEMPLATE.md，从 CHANGELOG.md 读取当前版本分类日志，并继续调用 GitHub 自动生成发布记录。缺少该版本日志或 APK 名称重复时停止发布。
- Python 15 项保护测试通过，新增历史文案格式、当前版本日志提取、缺失日志拒绝以及 flavor 文件名保留检查。
- YAML 语法检查通过；HEAD 加本次发布改动的隔离副本执行 pnpm lint、pnpm build:android、pnpm format 均通过。主工作区 lint 被另一路临时 .verify-player.cjs 的 require 规则错误影响，因此没有删除或修改该脚本。
- 并行播放器改动及临时验证脚本保留；本次提交仅包含发布脚本、模板、对应测试和文档。

## 手机播放页面 UI 修复（2026-10-03）

状态：IMPLEMENTED；下列构建、状态测试与浏览器场景为 VERIFIED，真机矩阵仍未验证。分支 dev。

- FullPlayerMobile：顶栏、内容与底栏都参与纵向布局；评论页不挂载底栏和频谱；安全区统一沿用项目变量。封面根据真实剩余区域 ResizeObserver 测量缩小，Metadata 最大占内容区域 60%，过多时独立滚动。
- useMobilePlayerControls：复用 useTimeoutFn 和 PlayerMetaHold 接口，页面隔离的 4 秒计时；多指拖动、菜单 hold 不隐藏；切页恢复；点击捕获不消费事件，隐藏时不在 pointerdown/pointerup 阶段移动命中目标。程序自动滚词不重置计时。
- PlayerComment：可单独淡出歌曲卡片，评论列表一直存在；卡片长标题不挤压操作按钮。FullPlayer：手机竖屏不使用父级点击拦截层。
- 状态回归 6 项通过：隐藏/恢复/重置、多指拖动、嵌套 hold、切页、失焦、卸载、隐藏时点击命中保护，以及浏览器接管触摸滚动后 pointercancel 不提前结束保护。
- 浏览器实际 Vue 页面：360×640、360×800、480×1067 CSS 视口（对应需求中常见 DPR 下四组物理分辨率），分别模拟 16/48px 导航安全区、30px 顶部和左右 8px cutout；长标题、多歌手、长专辑和多个标签的封面、信息、底栏无重叠。320×480 极小视口中 Metadata 可滚动到专辑，仍不进入底栏。
- 浏览器交互：歌词初始显示、4 秒淡出、点击恢复、长时间按住进度区域不隐藏、抬起后计时正常；评论无底栏、卡片淡出/恢复、评论可用高度增加。
- 普通歌曲的普通封面与唱片封面模式均已浏览器验证，封面、歌曲信息和专辑不进入底栏。
- 已执行 pnpm build:android、pnpm typecheck:web、pnpm lint、pnpm format；Android :app:assembleDebug 与 :app:testDebugUnitTest 通过；:app:lintDebug 任务返回成功，但 XML 报告仍有 205 errors / 33 warnings（203 UnsafeOptInUsageError、2 WrongConstant，涉及本轮未修改的原生播放代码），不视为零错误通过。格式与 ESLint 排除生成资源及临时验证目录。
- 未验证：真实手机手势/三键导航、系统 cutout inset 注入、真实后台音频播放、歌词长按/跳转在 Android WebView 的硬件交互。未修改 Service、队列、MediaSession 或音频引擎。
- 普通按钮、图标、顶栏最小触摸尺寸和封面首选上限仍使用固定 CSS px；没有固定底部占位高度或针对单机型的遮挡补丁。

- 最终 debug APK：`android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`（64,723,584 字节），另含 armeabi-v7a、x86、x86_64；仅用于测试，未执行正式发布或真机安装。

## v3.0.10 发布验证（2026-10-03）

状态：VERIFIED（以下本地验证）；版本 3.0.10 / versionCode 30016。GitHub 发布尚待 CI。

- 追加修复：底栏 grid 使用 minmax(0, 1fr)，按钮根据剩余宽度收缩，240px 极窄 WebView 下分页区域仍位于屏幕内。
- 独立验证包 top.imsyy.splayer.android.uiverify，与模拟器现有正式包和 debug 包并存，没有清除已有应用数据。仅验证包生成资源启用 CDP；正式配置保持 WebView debugging=false。
- 实际 Android APK：720×1280、1080×1920、1080×2400、1440×3200；手势导航与三键导航各一轮，封面、Metadata、底栏均无重叠，分页横向边界正确。
- 真实触摸：歌词初始显示，闲置淡出，点击恢复；纵向拖动超过 4 秒仍显示，松手后再次隐藏。Android 合成点击有延迟，验收在末次事件与动画完成后进行。
- 评论用固定 API/健康检查响应隔离独立包内嵌服务环境：评论文字、时间、点赞计数持续显示，歌曲卡片可隐藏/恢复，完整底栏不挂载。
- 真实原生播放：私有目录的静音 WAV 测试文件通过现有 Native Playback 插件播放；评论页和歌词 UI 隐藏时仍播放；返回桌面 6 秒后回到 APK，positionMs 从 20135 增至 27189，playing=true。
- 测试后恢复模拟器物理分辨率、密度与原导航 overlay；停止测试音频；验证包、临时音频和验证脚本均已清理，截图与日志保存在仓库外的本轮验证目录。
- 本地播放页状态测试 6 项、发布保护测试 15 项、Vue 类型检查与 ESLint 均通过；Android CI 增加播放页类型与状态测试。
- Android Lint 的历史原生代码 205 errors / 33 warnings 仍保留，不修改播放服务来消除报告。
