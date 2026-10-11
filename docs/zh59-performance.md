# zh.59 性能验证记录

日期：2026-10-11。开发分支：codex/android。设备：USB Mi 10 Pro，Android 13。应用版本：zh.58（70）与 zh.59（71），arm64 debug APK。

## 方法

每个版本 force-stop 后 am start -W 启动 FrameHome。预热 4 次滑动；重置 dumpsys gfxinfo；执行 8 次滑动，x=660，y=1750→650 或 650→1750，每两次换方向，持续 400ms。随后收集 gfxinfo 和 meminfo。手机解锁并暂时保持 USB 唤醒；结束恢复原设置。没有清除应用数据。

## 结果

| 项目 | zh.58 | zh.59 |
| --- | ---: | ---: |
| 冷启动 Activity TotalTime | 566ms | 456ms |
| 总帧数 | 56 | 360 |
| Janky frames | 56（100%） | 1（0.28%） |
| 中位数 / P90 / P95 / P99 | 150/150/150/150ms | 8/9/10/12ms |
| 慢 bitmap 上传次数 | 56 | 0 |
| 总 PSS | 344554KiB | 120085KiB |
| Graphics | 168868KiB | 51872KiB |

Activity 启动时间不包括全部异步图像完成。统计仅代表本次设备和操作，不是通用性能承诺。旧版帧耗时直方图在 150ms 档饱和，不能把该值解释为精确最大耗时。

## 原因与修复

旧版首页一次生成全部 58 张模板图，GPU image texture 记录为 113.07MiB；编辑器在 UI 线程进行全部模板预览和氛围模糊。新版只绑定可见列表项，限制示例照片尺寸与缓存；编辑器将图像处理交给后台，丢弃过时待处理任务，按当前分类生成缩略图。导出读取原照片，预览降采样不降低导出档位。

## 功能验证

- 系统 PhotoPickerActivity 提供照片/影集，模板点击直接打开；选择照片正常进入 PhotoStudio。
- 反复切换分类、模板，再执行 8 次横向滑动：344 帧，0 卡顿，中位数 5ms，P99 7ms。
- 4096px 作品分享生成 JPEG，进入系统分享面板；取消，没有发送给任何应用或联系人。
- 应用日志确认照片准备和渲染在后台完成；本次日志无 FATAL EXCEPTION。
- assembleDebug、lintDebug、testDebugUnitTest 通过：116 测试，0 失败、0 错误、0 跳过。新增队列测试验证快速变更去重和位图释放顺序，选择器测试验证系统相册与兼容回退。

本地原始记录保存于 /tmp/fuji58-cold-home-gfx.txt、/tmp/fuji58-cold-memory.txt、/tmp/fuji59-cold-home-gfx.txt、/tmp/fuji59-cold-memory.txt、/tmp/fuji59-editor-gfx.txt。临时日志不纳入仓库，避免携带用户照片与 URI。
