# 照片工作室参考与设计

2026-10-11，依据公开官网、帮助文档研究，未安装这些 App 进行操作实测。

| 产品 | 已核实的能力 | 本项目采用的设计方向 |
| --- | --- | --- |
| [FujiStyle](https://www.fujistyleapp.com/) | EXIF 边框、艺术相框、配方卡片 | 真实参数边框、风格模板、作品分享 |
| [Fuji X Weekly](https://fujixweekly.com/app/) | 示例照片、机型筛选、配方笔记、离线配方 | 图像优先；后续配方功能另行开发 |
| [Cascable](https://www.cascable.app/studio/features/) | 照片网格或元数据列表、图片查看、RAW 工作流 | 图片预览优先，参数按需展开 |
| [qDslrDashboard](https://dslrdashboard.info/introduction/) | Nikon/Canon/Sony、相机与本地图片浏览、PTP/PTP-IP | 本地编辑不占用相机通信，保留传图与查看流程 |
| [CamRanger Android 手册](https://camranger.com/knowledge-base/camranger-2-user-manual-for-android/) | 图片浏览、图像评级、下载和分享 | 原图与分享副本分开，清晰的导出操作 |
| [Framio](https://framio.org/) | 本地 EXIF 排版、参数栏、画廊留白和模板 | 图像模板选择与本地导出；当前还未提供原分辨率导出 |
| [Fuji Recipes](https://fujirecipes.co/) | 配方文件夹、机内 RAW 渲染、相机自定义槽位管理 | 拍摄、管理、编辑的流程组织；配方写入另行研究 |
| [Helicon Remote](https://www.heliconsoft.com/heliconsoft-products/helicon-remote/) | Nikon/Canon 等联机与多平台工作流 | 学习工作流程组织，本次不添加遥控拍摄功能 |

本次实施：深色工作室、大图预览、八种原创布局、照片模板缩略图、五种纸张颜色、留白宽度、标题、署名、参数开关、拍摄详情折叠、常用风格设置持久化。预览使用小图，导出使用最长边 2048px 的照片；布局按照片宽度等比绘制。不裁切原图，不宣称模拟真实胶片，不填充缺失参数，不导出 GPS。

仍需实机验收：不同横竖幅与中文标题、缺失 EXIF、保存及第三方分享、长时间切换模板的内存、系统字体放大。批量导出、更高分辨率和富士 MakerNote 配方解析尚未实施。
