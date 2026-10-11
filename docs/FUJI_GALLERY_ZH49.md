# Fuji图库 · zh.49

- 显示名称改为 Fuji图库，保留 applicationId、原签名、现有存储目录和相机端手机标识。
- 图标使用原创银黑相机造型、红色点缀和墨绿背景；支持普通与 Android 8+ 自适应图标。
- 页面采用墨绿底色、薄荷绿主操作、柔和文字层次和圆角卡片，保留连接阶段提示。
- 设置 → 显示调试信息，默认关闭。详细日志、蓝牙内部状态、诊断入口和后台蓝牙实验入口受开关控制；图库文件详情独立控制。
- Java 运行记录及有 JNI 上下文的 PTP 详细记录写入应用私有 no_backup/run-logs/current.log，超过 1 MiB 轮换至 previous.log，总计最多 2 MiB；重启继续追加，不参与系统备份。无 JNI 上下文的 native 线程继续记录 Android logcat。
- 可见性开关不控制日志记录。写入线程使用有界队列；达到上限由调用线程完成写入，避免积压造成无界内存增长。突然终止进程可能丢失尚未写入的末尾队列。
- 正在进行的本机 adb logcat 捕获持续保持，原始诊断在仓库外的 outputs/diagnostics。

验证：assembleDebug、lintDebug、74 项 Android 测试（包括关闭显示仍记录及轮换保留测试）和 8 项 native 恢复测试通过。Lint 0 errors / 245 warnings。原签名覆盖安装至 Xiaomi 10 Pro，versionCode 61；桌面名称与图标确认，默认隐藏调试、私有运行文件写入确认。
