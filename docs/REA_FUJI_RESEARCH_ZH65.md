# REA 调研与 zh.65 个人配方本

日期：2026-10-12。工具：REA 6.4.0 本地 CLI，Node 26.7.0，Chrome 154；借鉴流程并自行实现，未把竞品代码、配方内容或图片加入 APK。

## 调研范围和证据

REA 通过独立浏览器采集了手机版 DOM、可访问性树及截图，并执行 FujiStyle 网页相机筛选。手机包名检索没有识别到两个目标 App，本轮没有对其 APK、App 界面、付费功能或相机协议进行反编译/实测。网站功能不能直接等同于原生 App 行为。

证据原文件位于项目 outputs/research/rea-runtime，均为本次实际工具输出：

| 问题 | 文件 | Evidence ID | 结论范围 |
| --- | --- | --- | --- |
| 手机目标识别 | phone-packages.json | ev_7c23324e5df8558a5bbc6cc5fbc07e778b5ac570970612663a43586fb04b4f4d | 以 fuji 匹配包名，仅返回本项目 App；不覆盖全部应用显示名称 |
| Fuji X Weekly 页面 | weekly-evidence.json | ev_cff17c576bf0ad78726671d04f3636ab38c073d47a053e54a3af79901df0d33d | 官方 App 介绍与传感器分类入口 |
| FujiStyle 首页 | fujistyle-evidence.json | ev_951cfb747fb0695c6fcab3093029d8d2b7784996ee901facffee213be91388fe | 公开页面结构与示例展示 |
| 配方目录 | fujistyle-catalog-evidence.json | ev_a465af59b8b4e5a62882e80627ba940872ec40072b2f6a99ed65d5d6fc8dada3 | 机型/传感器/胶片模拟筛选入口与配方卡 |
| 相机筛选展开 | fujistyle-filter-evidence.json | ev_243c26668b694ed927cbcaceff452e4fc200a78590ccfae35a58e0da751ec9b7 | 实际点击后出现按相机系列组织的候选列表 |
| X-E5 筛选 | fujistyle-xe5-evidence.json | ev_8ec2331eab472335cd468f272338e8eae140c0ae210be02de35565b502f7ba0c | 实际选择后 URL 增加 camera=X-E5，本次页面返回 No Matching Recipes；仅是本次网页结果 |
| 配方读取页面 | fujistyle-reader-evidence.json | ev_a16d7c24da0310d0b4e4e4f03595c1274df845f3e09cc8a2819d8f7db4b2198f | 页面提供 JPEG 选择入口；没有提交用户照片验证识别 |
| 读取页脚本静态分析 | reader-bundle-evidence.json | ev_58e747553bd3f15cd13fdb9ce5be34138d2445fd542f4f6b15e2e538130a2f3e | 31 个观测脚本完成解析，无解析失败；框架指纹为推断，不证明识别算法或相机写入成功 |

官方参考链接：
- https://github.com/morluto/rea
- https://fujixweekly.com/app/
- https://www.fujistyleapp.com/fujifilm-recipes/
- https://www.fujistyleapp.com/tools/recipe-reader/

## 借鉴与产品决定

Fuji X Weekly 的官方介绍强调离线配方、机型/传感器过滤、收藏及个人笔记。FujiStyle 的公开目录以照片卡片搭配名称、模拟类型和作者，并提供筛选。

我们的方向是让“个人配方 → 拍摄卡 → 草稿 → 批量作品”连起来。本轮先做用户自己的离线记录，不依赖空缺的社区数据。机型是用户记录字段，不能据此推断机型支持全部参数或自动兼容。

## zh.65 已实现

首页“我的富士配方”入口与批量任务并排，保留边框创作主流程。支持：

- 独立保存名称、机型、C1–C7、胶片模拟、白平衡/偏移、动态范围、高光/阴影、色彩、锐度、降噪、颗粒、Color Chrome/Blue、清晰度、曝光补偿、使用笔记与来源。
- 搜索名称/机型/模拟/槽位等记录内容，收藏优先和仅收藏筛选。
- 查看、编辑、删除；名称与槽位校验。保存失败保留表单供重试。
- 明确标为手动记录的参数文字分享，用户自行选择分享目标。
- 从配方选照片制作配方签，或在编辑器选择配方，把名称与胶片模拟带入手动备注；不会改变照片颜色。
- AtomicFile 持久化，读写串行保护，缺项留空。同时修复草稿读写跨实例可能交错的问题。

## 尚未实现与下一步

尚无真实配方兼容数据库、传感器自动匹配、样片封面绑定、配方 JSON 备份导入、完整配方图卡、MakerNote 识别或相机配方写入。没有内置经过验证的配方参数，用户应按自己的相机记录。

后续建议先把个人样片绑定到配方、提供 JSON 备份与导入，再建立本地 MakerNote 的样本验证。自动相机写入需要独立协议研究与 X-E5 真机验证，不能根据网页按钮推断已兼容。

## 验证

构建、Lint 与 140 项测试通过。新增测试覆盖字段持久化、更新不重复、搜索/收藏、分享来源说明、备注长度、空名称校验、私有记录 ID 校验及配方选图状态与取消清理。真机补验完成：小米 14 / Android 16 验证保存、收藏及重启保留；小米 10 Pro / Android 13 验证配方选图、备注带入、草稿恢复、编辑器选择配方及系统文字分享面板（未发送）。修复表单校验自动定位，并添加输入字段的可访问性名称。小米 10 Pro 测试记录已清理。

待接回小米 14 时清理其临时 REA Test 配方，并把测试时 USB 常亮设置从临时值 2 还原为原值 0；用户已要求稍后处理，后续测试使用小米 10 Pro。
