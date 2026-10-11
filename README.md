# Fuji图库 · Fuji Transfer Android

**Fuji图库**是面向富士相机的 Android 连接、图库和图像传输应用，基于 [petabyt/fudge-legacy-android](https://github.com/petabyt/fudge-legacy-android) 开发。当前开发分支版本：`0.2.3-zh.51-download-recovery`；公开测试 APK 为 zh.50。

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

详细记录见 [更新日志](docs/CHANGELOG.md)、[连接状态](docs/CONNECTION_STATUS_ZH48.md)、[名称、图标与日志设置](docs/FUJI_GALLERY_ZH49.md)和[进度显示修复](docs/TRANSFER_PROGRESS_ZH50.md)。上方公开 APK 为 zh.50；zh.51 的[未完成下载恢复](docs/DOWNLOAD_RECOVERY_ZH51.md)在分支验证中，尚未合并 main。

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
