# 连接与传图流程复核（2026-10-11）

基线为本地 zh.45，包含尚未推送的 zh.43～45 修改。本轮为 zh.46；实机重连根因仍未确定。

## 网上研究与采用范围

| 来源 | 核实内容 | 对本项目的意义 |
| --- | --- | --- |
| [libfuji](https://github.com/petabyt/libfuji) | 支持无线传图、新旧蓝牙配对、USB 等模式；从 Fudge 拆分出协议库 | 最相关的协议研究源，后续按具体操作比较，不整体替换当前已验证协议 |
| [furble 协议文档](https://github.com/gkoh/furble/wiki/Protocol-Documentation) | 区分旧固件 token 配对与新固件加密绑定；记录 0x04D8 广播标识 | 支持保留当前绑定及广播识别修复；旧 token 模式的重连说明不能直接当作 X-E5 安全握手规则 |
| [furble](https://github.com/gkoh/furble) | ESP32 BLE 遥控实现，支持列表包含 X-E5 | 可研究配对及连接，不把遥控成功等同于 Wi-Fi 传图兼容性 |
| [furble-lite](https://github.com/yerffejytnac/furble-lite) | 提供 Basic/Secure 两种连接实现及自动重连 | 仅研究连接机制；无限重连不适合本项目用户主动断开的语义 |
| [fuji-cam-wifi-tool](https://github.com/hkr/fuji-cam-wifi-tool) | 旧款相机 Wi-Fi 协议研究工具，README 以 X-T10 为例 | 辅助对照旧 PTP/IP 行为，不能据此保证现代 X-E5 兼容 |
| [XApp 官方功能说明](https://www.fujifilm-x.com/en-in/products/software/xapp/) | 图像浏览、传输队列、原尺寸/压缩尺寸选择 | 产品流程可借鉴；自动传输需要独立协议验证，不能仅添加 UI 开关 |
| [XApp 官方功能对照](https://digitalcamera-support-en.fujifilm.com/digitalcameraengpcdetail?aid=000004150&p=a065i00000975qpAAA) | 日期/格式筛选、已传标记、RAW 及关机传输的机型限制 | 优先完善筛选、去重、失败重试；按机型验证格式和后台能力 |

上述资料是项目与官方文档的声明，本轮没有实测其他 App 的连接成功率，也没有复制外部实现代码。

## 本轮发现与修复

1. 下载中断开采用“当前文件保存完再关闭会话”。但 beginUserConnection 只检查 disconnecting，等待保存期间仍可清除 manualDisconnected 并启动 GATT。现新增对运行中 afterStop 回调的检查，直到保存结束及会话关闭完成才接受重连。
2. 下载心跳在 native ping 返回后只检查 running。用户在等待期间点停止，迟到错误仍可能强制关闭正在保存的会话。现返回后同时检查 stop 与 current 身份，并在下一轮 ping 前检查身份。
3. 当前 native 关闭路径已采用 CloseSession → Fuji goodbye → socket close，不贸然改协议顺序。补充前两步返回值日志；写入成功不能证明相机已恢复。
4. 新克隆构建缺少 lib/libpict/CMakeLists.txt，源于该目录忽略全部 *.txt。恢复原工程配置并加例外，确保源码完整进入 Git。

本轮没有改变 D621 对象编号、扩展对象解析、安全握手或热点启动协议。

## 后续开发顺序

1. X-E5 连续连接、断开、重连 3～5 轮：分别从相机页、图库页、下载中发起断开。下载中断开后立即点击重连，验证保存和关闭阶段均拒绝连接，关闭完成后可连接。
2. 手动断开后前后台各等待至少 1 分钟，确认无主动重连，记录相机屏幕是否恢复。
3. 若复现黑屏，保留应用和系统日志，对照 CloseSession/goodbye 结果及网络请求释放时刻，再决定最小协议修复。不要删配对，也不要凭相同名称切换 BLE 地址。
4. 稳定性验证后再扩展连接状态展示：明确蓝牙、热点、地址就绪、PTP、图库各阶段及失败原因。
5. 图库继续优化日期/格式筛选、已下载标记、选择保留及同一会话内失败重试。跨会话重试必须重新识别照片，不能直接复用旧 handle。

实时监看、遥控及控制台入口继续关闭。后台传输不等于自动导入新拍照片；后者尚未实现。

## 本轮验证

- assembleDebug、lintDebug、testDebugUnitTest 成功。
- 65 项测试，失败 0、错误 0、跳过 0。Lint 无错误，仍有 241 条警告，本轮未全量清理。
- APK 版本 0.2.3-zh.46-session-stop，versionCode 58，arm64-v8a。
- 使用既有本机证书重新签名并通过 apksigner 验证，证书指纹与交接文档一致。
- 已在 USB 测试机小米 10 Pro 覆盖安装并启动；设备端版本确认 58 / zh.46，未卸载或清除应用数据。
- APK SHA-256：8d19519dfb98ae2e9b0675966133872cf8b31ee384a5c77a1a467d5f576d409e。
- 本轮尚未完成 X-E5 连续重连实测，未发布 GitHub Release。

## 首轮断开与重连实测

用户反馈：图库断开后，约 10 秒相机恢复，再次重连成功。

第二次连接日志：11:03:59 发起 GATT；11:04:01 进入安全握手；11:04:07 系统选中相机 Wi-Fi；11:04:11 TCP 连接成功并被相机接受；11:04:12 再次取得 234 张照片的 D621 实际索引。抓取时应用没有运行后台待机或下载服务。

本次抓取未保留到可核对的 CloseSession/goodbye 记录。另查明 ptp_verbose_log 在未开启 native 日志缓冲时会直接返回，因此不能用缺少该日志推断关闭操作没执行；后续应将关键关闭结果接入常规诊断日志。

结论：用户操作与日志共同支持一次断开后重连成功，尚未达到 3～5 轮连续验证，也未覆盖下载中断开。
