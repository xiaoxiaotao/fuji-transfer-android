# Fuji图库 · Fuji Transfer Android

**Fuji图库**是一款 Android 摄影工具，包含两个独立功能：**相框创作**与**富士配方**。照片可以从手机相册选择，也可以通过富士相机连接导入。

当前开发分支：[`codex/android`](https://github.com/xiaoxiaotao/fuji-transfer-android/tree/codex/android)；版本：`0.2.3-zh.68-recipe-workspace`（versionCode 80）。2026-10-14 按用户要求暂停开发，当前代码与 APK 已推送到此分支，尚未合并到 `main`。

## 下载与安装

[下载 Fuji图库 zh.68 测试版 APK（arm64-v8a）](https://github.com/xiaoxiaotao/fuji-transfer-android/raw/refs/heads/codex/android/downloads/FujiGallery-Android-zh.68-recipe-workspace-arm64.apk)

支持 Android 6.0 及以上的 arm64 设备。下载 `.apk` 后安装；GitHub 的源码 ZIP 不能直接安装。安装包使用调试签名，不同签名的旧安装包可能无法直接覆盖。

APK SHA-256：

```text
be1d85e671db33d0e1ce24028a7e33f8ca90010f23a5bbbb3aa6181854dbd83b
```

其他已发布安装包位于 [downloads](downloads/)，校验记录见 [SHA256SUMS.txt](downloads/SHA256SUMS.txt)。

**zh.68 仍为测试快照**：构建、Android Lint 和 158 项测试通过。小米 10 Pro 已检查五项导航、独立配方首页及前一轮图标编辑界面；当前 APK 中最新的全页配方编辑与完整识别、样片、保存链路尚未完成真机验收。

## 功能入口

底部导航为「**相框 / 配方 / 照片 / 相机 / 我的**」。

| 页面 | 用途 |
| --- | --- |
| 相框 | 选照片、选择边框、继续草稿、批量出片 |
| 配方 | 独立管理富士色彩配方、原图识别、样片与参数编辑 |
| 照片 | 浏览已导入照片与已保存作品，筛选与搜索 |
| 相机 | 富士连接、相机图库与原文件下载 |
| 我的 | 默认署名、导出画质、调试与诊断设置 |

相框与配方分别使用独立页面和记录；配方不再挂在相框首页。需要制作配方签时，可从配方详情显式进入相框创作。

## 相框创作

- **71 款本地边框**，分为水印参数、印象装裱、光影模糊、简约白边、胶片相纸、电影故事、旅行出版、色彩创意八类，无需联网下载模板。
- 支持双图、三图、六格拼图，以及纸色、留白、圆角、阴影、背景虚化、标题、署名和真实拍摄参数。
- 模板收藏与最近使用；首页和编辑器共享记录。
- 自动保存创作草稿；可继续编辑、重新选择失效原图或只删除草稿记录。
- 单图导出提供 1080px、2048px、4096px 三档；尺寸指照片最长边，成品另计边框留白，小图不放大。拼图每张照片最多 2048px。
- 保存或分享独立 JPEG 副本，不改写原文件；成品不携带 GPS 和原图 EXIF。
- 批量套框最多 30 张，逐张读取参数；支持继续任务、重试失败项和取消后保留已保存作品。目前支持单图模板。

缩略图按需加载，解码、背景处理与预览在后台执行。批量任务串行处理；系统结束应用进程后可以重新进入任务继续，尚不能保证锁屏持续执行。

## 富士配方

- 独立配方首页，提供「我的配方 / 收藏」、搜索、新建与从富士原图识别入口。
- 配方卡片展示自己的样片、名称、胶片模拟、机型和槽位；样片后台解码并缓存。
- 详情与编辑按「机身与胶片模拟 / 色彩与白平衡 / 影调与质感 / 拍摄笔记」分组，使用相机、镜头光圈、胶片、WB、红蓝偏移、DR、颗粒等语义图标。
- 常用模拟、动态范围、影调和效果可选择枚举或档位，也可自行填写；未记录字段留空。
- 保存相机与镜头、C1–C7、模拟、白平衡与偏移、高光、阴影、色彩、锐度、降噪、颗粒、Color Chrome / FX Blue、清晰度、曝光参考、笔记及来源。
- 支持收藏、编辑、删除、分享参数文字，以及将配方作为作品的手动备注。原图读取值与个人修改值保留来源区分。
- 全页编辑在页面重建时保留尚未保存的字段。

**这些是本地配方记录，不是后期滤镜，也不会自动写入相机。** 当前没有在线社区配方库或第三方二维码互通功能。

### 原图识别

JPEG 的富士 MakerNote 可提供胶片模拟及部分 JPEG 设置。识别包括 Velvia、Classic Chrome、Classic Negative、Reala ACE、Nostalgic Negative、Eterna、Acros 等；标准 `LensModel` 用于补读镜头名称。三款富士拍摄卡可显示当前照片识别出的模拟，与手动备注分开。

X-E5 白平衡偏移在原值能整除 20、换算后处于 ±9 档时显示相机档位，同时保留原值；其他机型和异常值保留原始单位。此换算经过 X-E5 元数据副本对照验证，不能视为所有富士机型的统一规则。

缺失或未知参数不推测。原图参数无法确定自定义配方名称、C1–C7 槽位、网络配方身份或后期滤镜。当前富士参数解析仅支持 JPEG，不解析 RAF、HEIF、PNG 的 MakerNote。

详见 [MakerNote 识别说明](docs/FUJI_MAKERNOTE_ZH66.md)和 [FujiStyle 安卓真机调研](docs/REA_FUJISTYLE_ANDROID_2026-10-14.md)。

## 富士相机连接与传图

支持蓝牙启动相机 Wi-Fi、手动连接相机热点和 USB 传图。连接状态卡显示蓝牙、Wi-Fi、相机会话与图库进度、断开和错误；初始化传输失败时尝试一次自动恢复。

相机图库支持缩略图、日期分组、筛选、多选及原文件下载。下载进度统一显示百分比、大小和速度；已保存标记核对原文件是否仍可访问，预览成功不等于原图已下载。下载记录支持未完成任务恢复。

主要实测设备为 X-E5、小米 14、小米 10 Pro。不同相机、系统及长时间后台连接仍需实机验证。实时监看、遥控拍摄入口和拍摄控制台当前关闭；相机拍照后自动下载尚未实现。

## 已知问题与验证范围

| 项目 | 当前状态 |
| --- | --- |
| 系统相册选图缺图 | **未修复**。用户确认发生在系统选图界面，部分图像无法找到；zh.68 未改动 PhotoPicker，后续排查相册提供方、目录与兼容选图入口。 |
| 最新配方界面 | 自动化检查通过；完整真机验收待继续。 |
| 应用「照片」页 | 展示已导入或已保存记录，不等于手机全部相册。 |
| 配方写入相机 | 未实现；蓝牙/Wi-Fi 传图成功不代表支持配方写入。 |
| 第三方二维码 | 仅研究到竞品扫码入口，格式和互通未验证。 |
| 非零 WB 换算 | X-E5 受控元数据副本已验证；其他机型未验证。 |
| 后台任务 | 系统休眠或结束进程后，不保证连接或批量导出持续执行。 |

当前开发已暂停；上述问题没有通过本次推送解决。历史版本的测试数量和设备表现仅代表当时的检查范围，见 [Android 历史版本记录](docs/ANDROID_VERSION_HISTORY.md)。

## 构建与检查

使用 JDK 17、Android SDK 35、NDK 25.1.8937393、CMake 3.22.1。依赖源码已随仓库提供，无需初始化 Git 子模块。

```sh
git clone --branch codex/android git@github.com:xiaoxiaotao/fuji-transfer-android.git
cd fuji-transfer-android/android
# 在 Android Studio 中打开此目录并配置 SDK，或设置 ANDROID_HOME。
bash gradlew --no-daemon -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false assembleDebug lintDebug testDebugUnitTest
```

APK 一般位于 `android/app/build/outputs/apk/debug/app-debug.apk`；注入式构建也可能位于 `android/app/build/intermediates/apk/debug/`。当前日常测试使用 arm64-v8a。

zh.68 的构建、Android Lint 和 **158 项 Android 测试**通过，Lint 警告尚未全部清理。GitHub Actions 配置见 [android.yml](.github/workflows/android.yml)，构建产物与仓库中明确发布的 APK 分别管理。

可单独验证 native 初始化恢复逻辑，需要 C 编译器：

```sh
# 在仓库根目录执行
sh tests/run-ptp-init-recovery.sh
```

## 调试与日志

正常使用保持「我的 → 显示调试信息」关闭；排查时开启可查看详细状态、复制诊断和访问实验入口。连接状态卡不受此开关影响。

运行日志独立持续记录，保存在应用私有 `no_backup/run-logs/` 中，保留 `current.log` 与 `previous.log`，每个文件约 1 MiB，不参与系统备份。Java 运行记录和具有 JNI 上下文的 PTP 记录写入日志，Android logcat 同时保留协议输出。进程突然结束时，尚未写入的末尾记录可能丢失。

## 工程与资料

| 路径 | 内容 |
| --- | --- |
| `android/` | 当前 Java/XML Android 应用与测试 |
| `ndk/` | JNI、USB 和协议桥接 |
| `lib/` | 富士协议、PTP 通信及依赖 |
| `third_party/` | 图像编解码依赖及许可说明 |
| `downloads/` | 明确发布的 APK 与校验记录 |
| `docs/` | 版本、验证、设计与研究记录 |
| `ios/` | iOS 工程；不属于此次 Android 验收范围 |
| `android-compose/` | 继承的其他 Android 工程；未在本版验收 |

相关资料：[历史版本](docs/ANDROID_VERSION_HISTORY.md)、[更新日志](docs/CHANGELOG.md)、[批量套框](docs/BATCH_FRAMES_ZH64.md)、[性能验证](docs/zh59-performance.md)、[下载恢复](docs/DOWNLOAD_RECOVERY_ZH51.md)、[连接状态](docs/CONNECTION_STATUS_ZH48.md)。

## 许可与致谢

连接与传图部分基于 [petabyt/fudge-legacy-android](https://github.com/petabyt/fudge-legacy-android) 开发。保留上游 GPL v2 许可文本与原作者版权，参见 [LICENSE](LICENSE)。部分协议适配参考 MIT 许可的 petabyt/libfuji，说明位于 [third_party/notices](third_party/notices)；其他依赖保留各自许可。

本项目为社区开发应用，与富士官方无隶属关系。参考应用仅用于研究交互，不打包竞品配方库、图片或标识。不提交 SDK、Gradle 缓存、设备日志、个人照片、Wi-Fi 密码和签名密钥。
