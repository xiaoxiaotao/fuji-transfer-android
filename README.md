# Fuji Transfer Android

面向富士相机的 Android 连接、图库和图像传输应用，基于 [petabyt/fudge-legacy-android](https://github.com/petabyt/fudge-legacy-android) 开发。当前版本：`0.2.3-zh.42-transfer`。

## 当前功能

- 中文界面，蓝牙启动相机 Wi-Fi、手动连接相机热点、USB 传图。
- 图库缩略图、筛选、预览，任意数量多选下载。
- 下载任务与记录、本地图库、已下载文件管理、缓存和连接诊断。
- 相机基本信息；后台蓝牙待机为实验功能。

主要实测：X-E5 固件 1.12，小米 14 和小米 10 Pro。不同机型和 Android 版本需要实机验证。连接成功率仍在优化。

实时监看、遥控拍摄入口和拍摄控制台已关闭，当前优先完善连接与传图。相机拍照后自动下载尚未实现。休眠或系统限制后台时也不能保证持续连接。

## 构建

使用 JDK 17、Android SDK 35、NDK 25.1.8937393、CMake 3.22.1。依赖源码已随仓库提供，无需初始化 Git 子模块。

```sh
git clone git@github.com:xiaoxiaotao/fuji-transfer-android.git
cd fuji-transfer-android/android
# 在 Android Studio 中打开此目录并配置 SDK，或设置 ANDROID_HOME。
./gradlew --no-daemon -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false assembleDebug lintDebug testDebugUnitTest
```

APK 输出：`android/app/build/outputs/apk/debug/app-debug.apk`（Gradle 的注入式构建也可能位于 `intermediates/apk/debug/`）。当前日常测试使用 arm64-v8a。

本地 zh.42 构建、Lint、54 项测试通过；GitHub Actions 会重新构建并保存测试版 APK。测试 APK 使用调试签名，未上传本机签名密钥，不能保证直接覆盖此前在另一台机器签名的安装包。

## 工程目录

- `android/`：当前 Java/XML Android 应用与测试。
- `ndk/`：JNI、USB 和协议桥接。
- `lib/`：富士协议、PTP 通信及依赖。
- `third_party/`：图像编解码依赖和许可说明。
- `docs/`：上游说明、依赖来源、版本说明与后续计划。
- `android-compose/`、`desktop/`：继承自上游的其他工程，未在本版验收。

## 许可与致谢

保留上游 GPL v2 许可文本与原作者版权，参见 [LICENSE](LICENSE)。依赖各自许可保留于原目录。部分协议适配参考 MIT 许可的 petabyt/libfuji，说明位于 [third_party/notices](third_party/notices)。本项目为社区开发应用，与富士官方无隶属关系。

不提交 SDK、Gradle 缓存、设备日志、个人照片、Wi-Fi 密码、签名密钥和 APK 构建产物。
