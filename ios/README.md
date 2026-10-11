# Fuji图库 · iPhone 版本

分支：`codex/ios-fuji-gallery`。第一版目标设备为 iPhone 15 Pro Max，最低 iOS 16。

原生 SwiftUI 界面，CoreBluetooth 读取富士相机热点信息，NEHotspotConfiguration 加入热点，共用仓库现有 C 语言 Fuji/PTP 协议；不改动 Android 代码。仅提供连接、图库和照片保存，没有遥控拍摄或删除相机照片功能。

## 功能与边界

- 蓝牙扫描后由用户选择设备，读取安全配对状态；iOS 在访问受保护特征时处理系统配对，不使用 Android 蓝牙地址。
- 手动加入热点后可使用 Wi-Fi 直连，支持自动、传统及 XApp 协议选择。默认相机地址 `192.168.0.1` 可修改。
- 图库缩略图按需读取、多选照片、保存至系统相册，只申请添加照片权限。第一版暂不保存视频。
- 连接状态和下载进度、停止剩余任务、持久化历史。同一蓝牙相机重连后按文件名、大小、日期和格式核对任务，排除重复身份；直连不猜测相机身份。
- 文件长度验证通过才提交相册。断线文件完整重下，不是字节续传。相册提交前写入“待确认”状态，提交过程中崩溃的记录不会自动再次保存，需用户检查相册。
- 默认隐藏调试信息；本地运行日志持续记录，单文件约 1 MiB，保留当前及上一份。Wi-Fi 密码只保留在本次操作内存中，不写入日志或设置。
- 临时热点连接受 iOS 后台与休眠限制。进入后台时停止剩余任务、结束连接；相机传图应保持 App 在前台。尚未验证首次配对、重连、RAF/HEIF 相册兼容性及长时间传输，不宣称与 Android 完全一致。

## 打开与真机安装

1. 在 Mac 安装完整 Xcode；Command Line Tools 没有 iPhone SDK，不能生成可安装的 iPhone 程序。
2. 打开 `ios/FujiGallery.xcodeproj`，选择 `FujiGallery` target。
3. 在 Signing & Capabilities 选择 Apple Development Team，必要时修改 Bundle Identifier，保留 Hotspot Configuration 能力。此能力需账号支持；不支持时请使用下方手动 Wi-Fi 方案。
4. 连接 iPhone 15 Pro Max，信任 Mac，按 Xcode 提示启用开发者模式；选择手机并 Run。
5. 在相机上注册本 iPhone，确认两端配对提示，允许蓝牙、本地网络及添加照片。

若账号不支持 Hotspot Configuration，可在 target 移除该能力及 entitlement，再由用户在 iPhone 设置中手动加入相机热点，使用 App 的“相机 Wi-Fi 直连”。此时自动蓝牙热点加入不会工作。

IPA 必须通过 Apple 签名后才能安装，未签名的 CI `.app` 仅供编译检查；本仓库不包含证书、账号、描述文件或签名密钥。

## 验证

本机无完整 Xcode：C 原生桥接编译与生命周期检查、8 项 Foundation 恢复逻辑检查、Swift 语法解析和 plist 检查已通过。`swift test` 在此机器因缺少 XCTest 框架无法运行，本地检查不能替代 iOS 编译验证。GitHub Actions 已通过 8 项 XCTest，并成功构建未签名 iPhone App 和模拟器 App。

```
sh ios/tools/native-check.sh
sh ios/tools/core-check.sh
swift test --package-path ios
xcodebuild -project ios/FujiGallery.xcodeproj -scheme FujiGallery -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

GitHub Actions `iOS build and recovery tests` 使用完整 Xcode 跑单元测试并构建真机和模拟器版本。生成工程的工具为 `python3 ios/tools/generate-project.py`，图标为原生绘图生成，无第三方工程生成依赖。

真机验收顺序：权限允许/拒绝 → 首次蓝牙配对 → 图库读取 → 单张 JPEG 保存 → 多选 → 主动断开重连 → 下载时断线 → 恢复任务 → 后台/锁屏 → 已保存文件不重复。使用现有照片测试，不删除、拍摄或重置相机。

## Apple 文档

- [连接相机等设备的热点](https://developer.apple.com/documentation/networkextension/wi-fi-configuration)
- [临时热点生命周期](https://developer.apple.com/documentation/networkextension/nehotspotconfiguration/joinonce)
- [本地网络权限](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
- [PhotoKit 相册保存](https://developer.apple.com/documentation/photos/phphotolibrary)
