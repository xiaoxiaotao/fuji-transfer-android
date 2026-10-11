# Fuji图库 · Fuji Transfer Android

**Fuji图库**是面向富士相机的 Android 连接、图库和图像传输应用，基于 [petabyt/fudge-legacy-android](https://github.com/petabyt/fudge-legacy-android) 开发。当前代码版本：`0.2.3-zh.51-download-recovery`；公开测试 APK 为 zh.50。

## 下载 APK

[直接下载 Fuji图库 zh.50 测试版（arm64-v8a）](https://github.com/xiaoxiaotao/fuji-transfer-android/raw/refs/heads/main/downloads/fudge-0.2.3-zh.50-transfer-progress-arm64.apk) · [版本说明与校验值](https://github.com/xiaoxiaotao/fuji-transfer-android/releases/tag/v0.2.3-zh.50-transfer-progress)

适用于 Android 6.0 及以上的 arm64 设备。此安装包已在小米 10 Pro（Android 13）安装并启动，当前仍为测试版。请使用上方直接下载链接获取 `.apk`，源码压缩包不能直接安装。

APK SHA-256：

```text
36ec3a5003598368f284c479b9e6993332d96aeb4dbcfeef095b4173847bdccb
```

## 当前功能

- 中文界面、富士相机风格图标、墨绿与薄荷绿配色；支持蓝牙启动相机 Wi-Fi、手动连接相机热点、USB 传图。
- 连接状态卡显示蓝牙、Wi-Fi、相机会话和图库的进度、错误及断开状态；初始化传输失败时尝试一次自动恢复。
- 图库缩略图、筛选、预览，任意数量多选下载。
- 下载任务与记录、本地图库、已下载文件管理、缓存和连接诊断。
- 保存时统一显示百分比、已传大小与速度，避免多个进度来源互相覆盖、闪烁。
- 设置中的「显示调试信息」默认关闭；运行日志独立持续记录，应用重启后保留最近约 2 MB。
- 相机基本信息；后台蓝牙待机为实验功能。

主要实测：X-E5 固件 1.12，小米 14 和小米 10 Pro。不同机型和 Android 版本需要实机验证。连接成功率仍在优化。

实时监看、遥控拍摄入口和拍摄控制台已关闭，当前优先完善连接与传图。相机拍照后自动下载尚未实现。休眠或系统限制后台时也不能保证持续连接。

## 最近更新与验证

zh.43–zh.50 完善蓝牙识别、手动断开与重连、PTP 初始化恢复、连接状态提示及下载进度显示。zh.50 已在小米 10 Pro 覆盖安装，用户确认保存进度显示正常；zh.49 实机完成连接并读取 234 个文件的图库索引。其他相机、长时间后台传输和更广泛的系统兼容性仍需验证。

详细记录见 [更新日志](docs/CHANGELOG.md)、[连接状态](docs/CONNECTION_STATUS_ZH48.md)、[名称、图标与日志设置](docs/FUJI_GALLERY_ZH49.md)和[进度显示修复](docs/TRANSFER_PROGRESS_ZH50.md)。上方公开 APK 为 zh.50；zh.51 的[未完成下载恢复](docs/DOWNLOAD_RECOVERY_ZH51.md)已通过本地构建、Lint、84 项 Android 测试及用户真机功能验证，合并至 main。

## 构建

使用 JDK 17、Android SDK 35、NDK 25.1.8937393、CMake 3.22.1。依赖源码已随仓库提供，无需初始化 Git 子模块。

```sh
git clone git@github.com:xiaoxiaotao/fuji-transfer-android.git
cd fuji-transfer-android/android
# 在 Android Studio 中打开此目录并配置 SDK，或设置 ANDROID_HOME。
bash gradlew --no-daemon -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false assembleDebug lintDebug testDebugUnitTest
```

APK 输出：`android/app/build/outputs/apk/debug/app-debug.apk`（Gradle 的注入式构建也可能位于 `intermediates/apk/debug/`）。当前日常测试使用 arm64-v8a。

本地 zh.50 构建、Lint、77 项 Android 测试及 8 项 native 初始化恢复场景通过；Lint 仍有 245 条警告，尚未全部清理。GitHub Actions 会重新构建并保存测试版 APK。测试 APK 使用调试签名，未上传本机签名密钥，不能保证直接覆盖此前在另一台机器签名的安装包。连续连接、断开、重连仍需实机验证。

可单独验证 native 初始化恢复逻辑（需要 C 编译器）：

```sh
# 在仓库根目录执行
sh tests/run-ptp-init-recovery.sh
```

## 调试与日志

正常使用保持「设置 → 显示调试信息」关闭；排查问题时开启，可查看详细状态、复制当前诊断和访问实验入口。连接状态卡不受此开关影响。

运行日志保存在应用私有的 `no_backup/run-logs/`，单文件达到 1 MiB 后轮换，保留 `current.log` 与 `previous.log`，不参与系统备份。Java 运行记录和具有 JNI 上下文的 PTP 记录持续写入；Android logcat 同时保留协议输出。进程被突然终止时，末尾尚未写入的记录可能丢失。诊断可能包含设备和网络信息，分享前请检查内容。

## 工程目录

- `android/`：当前 Java/XML Android 应用与测试。
- `ndk/`：JNI、USB 和协议桥接。
- `lib/`：富士协议、PTP 通信及依赖。
- `third_party/`：图像编解码依赖和许可说明。
- `docs/`：上游说明、依赖来源、版本说明与后续计划。
- `android-compose/`、`desktop/`：继承自上游的其他工程，未在本版验收。

## 许可与致谢

保留上游 GPL v2 许可文本与原作者版权，参见 [LICENSE](LICENSE)。依赖各自许可保留于原目录。部分协议适配参考 MIT 许可的 petabyt/libfuji，说明位于 [third_party/notices](third_party/notices)。本项目为社区开发应用，与富士官方无隶属关系。

不提交 SDK、Gradle 缓存、设备日志、个人照片、Wi-Fi 密码和签名密钥。`downloads/` 仅保存明确发布、已签名并验证的安装包；其他构建产物不提交。

### Android 照片详情与边框（zh.52）

在手机图库打开已保存照片，点击“详情 / 边框”；相机预览页菜单也提供入口，需要先保存原文件。可读取相机、镜头、光圈、快门、ISO、焦距、日期和尺寸，缺失参数不推测。富士 MakerNote 胶片模拟与配方尚未解析。

支持白色参数边框、深色参数边框和简洁白边，预览后可保存副本或通过系统分享。导出为 JPEG，照片最长边不超过 2048 像素（边框另计），不修改原文件，不携带 GPS 或原图 EXIF。RAW 等无法解码的格式会提示使用 JPEG。Android 6–8 保存需存储权限；拍摄参数读取需要 Android 7 或更新版本。

### 边框工作室（zh.53）

深色大图编辑器，八种模板：经典参数、夜色参数、纯净留白、画廊藏品、即刻胶片、35mm 胶卷、旅途杂志、宽银幕。支持照片模板缩略图、五种纸色、3%–15% 留白、标题、署名、拍摄参数开关和折叠详情。常用风格、纸色、留白、署名与参数设置自动保存。预览和导出按同一比例布局，导出保留完整照片，不裁切原图。参考研究见 [设计记录](docs/photo-studio-references.md)。

### zh.54：连接与选片流程

- 首页显示已保存相机卡片，可直接通过已验证的蓝牙地址重连，随后沿用现有 Wi-Fi 交接和传图流程。权限缺失、蓝牙关闭和连接错误仍显示在连接状态面板中。
- 相机图库默认按拍摄日期分组，最新日期在前；日期信息尚未读出的照片暂归入“日期待确认”。点击日期标题选择整组，再次点击可取消整组。筛选窗口可关闭分组，恢复相机顺序或其他排序。
- 长按照片进入多选，随后滑动连续选片；靠近列表边缘可以继续滚动选片。拖动期间暂停分组更新，选择始终跟随相机对象编号。
- 缩略图明确显示“预览”或“✓ 原文件”；已保存标记会核对下载记录和本地文件是否仍可访问。照片详情区显示原文件大小和保存状态，清晰预览不代表已经保存。
- 边框工作室新增三档导出：轻巧分享（照片最长边上限 1080px / JPEG 85%）、标准画质（2048px / JPEG 95%）、高清作品（4096px / JPEG 95%）。尺寸指边框内部照片，成品包含额外留白；小图不会放大，实际尺寸也受解码采样影响。导出从本地原文件重新读取，不使用屏幕预览；保留原文件，导出副本不包含 GPS 和原始 EXIF。
- 高清导出若内存或空间不足，会提示改选较低画质，不自动替换为低画质成功结果。运行日志持续记录连接和导出结果。

验证：`assembleDebug`、`lintDebug`、94 项单元测试通过。已安装到 USB 连接的 Mi 10 Pro，已检查主页、画质菜单和分享导出：同一张照片的高清副本为 4326 × 3359 像素，轻巧副本为 1080 × 839 像素，均无 EXIF；X-E5 快捷重连和在线图库滑动选片仍需实机相机复测。研究参考来自 [Shutter+ 富士兼容说明](https://shutter.dev/fujifilm)、[已保存相机](https://shutter.dev/user-guide/getting-started/managing-saved-cameras)、[照片下载流程](https://shutter.dev/user-guide/importing-photos/downloading-photos)，功能支持以本项目实际实现和相机实测为准。

### zh.55：光影卡片边框

新增雾光卡片、光影参数、琉璃色盘三款模板，参考光影边框官方样例的模糊背景、悬浮卡片与参数分区设计。可调背景虚化、圆角、阴影和留白；色盘来自当前照片，拍摄参数来自原文件 EXIF。更新后首次进入工作室默认展示雾光卡片，旧模板仍可选择。前景保持原比例，背景铺满画布，圆角遮罩四角；保存和分享均生成独立 JPEG 副本。三档导出设置继续有效。

工作室预览固定显示并支持双指缩放，编辑项拆分为“模板 / 背景 / 参数 / 导出”。模板分类为“光影卡片 / 参数留白 / 艺术胶片”；侧栏参数可放在左侧或右侧，色盘从整张照片选取有代表性的不同颜色。界面与原有保存、分享、原图保护流程结合。
