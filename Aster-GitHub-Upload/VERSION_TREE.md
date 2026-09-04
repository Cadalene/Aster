# 版本树

更新时间：2026-08-30

```text
main
└── v1.3.2                                  主题色切换主线版本；拍照模块稳定基线为 v0.4
    └── v1.4.0-project-overview-template    固定项目整体情况模板、旧模板升级保护与网页设备类型筛选
        └── v1.4.1-project-overview-scroll  修复项目设备列表位置保存与返回恢复
            └── v1.4.2-astral-wind-rose-icon 更换 Astral Wind Rose Android 启动图标
                └── v1.4.3-app-name-paper-blue-default Aster 应用名称、纸张蓝默认主题与珊瑚红状态色

历史版本线：

v0.1-camerax                         CameraX 历史基线
└── v0.2-native-camera               系统相机实验版本
    └── v0.3-native-camera-gallery   原图预览与保存到相册
        └── v0.4-native-camera-gallery-import   删除照片与从相册导入
            └── v0.5-template-management         设备类型模板管理
                └── v0.5.1-template-drag-reorder  修复模板项编辑并支持拖动排序
                    └── v0.6-word-export           关键设备截图 Word 导出
                        └── v0.7-bottom-navigation-settings  底部主导航与默认 Word 导出目录
                            └── v0.8-project-export-html-viewer  项目 ZIP 导出与离线 HTML 浏览
                                └── v0.9-management-polish  导出入口、默认目录、模板复制与设备重命名
                                    └── v0.10-original-photo-export  按设备类型和设备名称导出原始照片 ZIP
                                        └── v0.11-project-device-management  项目改名及项目、设备安全删除
                                            └── v0.12-word-format-delete-confirmation  Word 标题格式及删除二次确认
                                                └── v0.13-built-in-templates-storage-path  内置设备模板及原始照片默认导出目录
                                                    └── v0.14-root-tabs-scroll-dialog  根页面切换与添加设备模板列表滚动
                                                        └── v0.15-export-status-template-order  统一原图导出、三态显示与模板排序
                                                            └── v0.16-no-navigation-animation  App 页面转场全部立即切换
                                                └── v1.0.0  首个正式签名版本
                                                    └── v1.1.0  Pinterest 风格正式界面与项目浏览包视觉统一
                                                        └── v1.1.1  精简项目卡片、统一弹窗、图片键盘切换与新应用图标
                                                            └── v1.1.2  将应用图标替换为抽象拼贴卡片方案
                                                                └── v1.2.0  模板 JSON 导入导出与 PC 双栏独立滚动
                                                                    └── v1.3.0-paper-blue  可切换经典珊瑚与纸张蓝外观
                                                                        └── v1.3.1-paper-blue-typography  纸张蓝字体真正应用到页面文字；卡片改为细边框、低阴影的纸张层级
                                                                            └── v1.3.2-theme-color  主题色切换主线版本；经典珊瑚与纸张蓝只切换颜色，不修改字体
```

## 分支说明

- `main`：当前正式主线（v1.3.2），沿用固定正式包名与签名，支持设备类型模板迁移、项目浏览包双栏操作和经典珊瑚/纸张蓝主题色切换。两种主题共用原有字体与组件样式。
- `codex/app-name-paper-blue-default-v1.4.3`：基于 v1.4.2 的版本分支，将应用显示名改为 Aster；首次安装或无历史主题设置时默认使用纸张蓝；经典珊瑚显示名改为珊瑚红；纸张蓝不符合状态改为更鲜明的红色。
- `v0.4-native-camera-gallery-import`：拍照模块稳定基线，使用 Android 标准全尺寸拍照 Intent 调用手机系统相机。
- `v0.5.1-template-drag-reorder`：修复普通模板项无法保存的问题，App 顺序改为长按拖动，Word 顺序保留数字输入。
- `v0.6-word-export`：在项目页导出关键设备截图 DOCX，按项目、设备、取证项建立三级标题，只插入有效照片并保持多图连续排列。
- `v0.7-bottom-navigation-settings`：现场取证与设备类型模板改为底部双入口；齿轮进入设置，并支持持久化默认 Word 导出目录。
- `v0.8-project-export-html-viewer`：支持导出包含原图、缩略图、项目数据和离线 HTML 浏览器的 ZIP 项目包；网页支持按设备/取证项两种视图及符合性筛选。
- `v0.9-management-polish`：项目页使用带文字按钮导出 ZIP 和 Word；设置页增加项目浏览包默认目录；支持复制现有设备类型模板和修改设备名称。
- `v0.10-original-photo-export`：项目页独立导出原始取证照片 ZIP，按设备类型和设备名称分层，保留原文件字节、格式和多图顺序。
- `v0.11-project-device-management`：项目详情支持改名和删除，设备详情支持删除；删除前显示影响数量，确认后级联删除记录并清理原始照片文件。
- `v0.12-word-format-delete-confirmation`：Word 项目名称和关键截图项使用黑色仿宋二号，设备名称使用黑色仿宋小二；项目删除增加原始照片导出提醒和不可恢复确认。
- `v0.13-built-in-templates-storage-path`：加入详细设备类型和取证项内置模板；设置页增加原始照片 ZIP 默认导出目录，项目页导出时自动使用该目录。
- `v0.14-root-tabs-scroll-dialog`：现场取证和设备类型模板改为同级根页面；移除模板列表页左上角返回按钮；添加设备对话框中的设备类型列表支持滚动。
- `v0.15-export-status-template-order`：移除重复原始照片 ZIP，项目浏览包 originals 保留原图；取证项显示三种状态；设备类型模板支持拖动排序；默认目录导出前允许改选位置；设置页显示 App 私有原图路径。
- `v0.16-no-navigation-animation`：关闭 App 内前进、返回和预测返回的导航转场动画，所有页面立即切换；系统相机和系统文件选择器仍由手机系统控制。
- `v1.0.0`：首个正式版本；正式包名为 `com.walter.dengbaoevidence`，数据库升级到版本 3，项目浏览包格式升级到 1.1。
- `v1.1.0`：移动端使用 Pinterest 灵感的暖灰、珊瑚色和大圆角卡片界面；项目导出包内的 PC 离线浏览器同步采用同一套视觉风格，保留原有双视图、搜索、结论筛选和原图查看功能。
- `v1.1.1`：移除项目列表固定渐变背景；模板列表去掉齿轮图标和冗余说明；统一添加、导出及确认弹窗背景；PC 浏览包支持同一取证项多张图片用左右方向键切换；更新应用图标。
- `v1.1.2`：应用图标改为珊瑚、米白和墨绿色圆角卡片叠层，不再使用相机和勾选符号。
- `v1.2.0`：设置页支持使用带格式版本号的 JSON 文件导出、预览校验和导入设备类型模板；同名模板可选择自动改名、覆盖或跳过，且不影响项目中已创建的设备。PC 项目浏览包在桌面端改为设备列表和记录内容分别滚动，点击设备或取证项后右侧自动回到顶部。
- `v1.3.0-paper-blue`：新增外观设置，经典珊瑚外观继续作为默认；增加纸张蓝（Hermes Editorial）外观，使用暖纸色背景、深墨蓝文字、低饱和局部蓝色强调和更克制的纸张层级。外观选择使用现有 SharedPreferences 持久化，不修改业务数据、导航结构和照片/导出逻辑。
- `v1.3.1-paper-blue-typography`：修正纸张蓝字体未覆盖旧页面裸 `Text` 的问题，补齐衬线标题、衬线正文与等宽辅助信息的主题角色；纸张蓝卡片使用细边框并移除浮动阴影，经典珊瑚外观保持原样。版本号 1.3.1（versionCode 24）。
- `v1.3.2-theme-color`：将功能定位明确为主题色切换主线版本。设置页名称改为“主题色”，选项仅显示“经典珊瑚”和“纸张蓝”；两种主题共用原有字体、形状和卡片样式，仅切换颜色 token。版本号 1.3.2（versionCode 25）。
- `v1.4.0-project-overview-template`：新安装仅初始化固定的“项目整体情况”模板（保留“其他”），不再预置普通设备类型；旧版本升级保留已有模板和项目数据。新项目自动复制该模板并创建“项目整体情况”设备，普通设备添加列表隐藏固定模板。固定模板可编辑取证项但不可改名或删除。项目浏览包固定使用 Hermes 偏白风格，并支持设备类型多选筛选；项目设备列表返回时恢复滚动位置。版本号 1.4.0（versionCode 26）。
- `v1.4.1-project-overview-scroll`：按项目持久化设备列表的滚动索引和偏移量，进入设备前立即保存，返回项目或重新打开项目时恢复；不修改业务数据。版本号 1.4.1（versionCode 27）。
- `v1.4.2-astral-wind-rose-icon`：接入 `android-icon-final` 资源包中的 Astral Wind Rose 启动图标，更新自适应图标、旧版密度 PNG 和圆形图标资源；不修改业务数据和导航逻辑。版本号 1.4.2（versionCode 28）。
- `feature/native-camera`：v0.4 之前的系统相机开发分支，保留用于查看历史实现。

## 回退方式

切回 CameraX 历史版本：

```powershell
git switch --detach v0.1-camerax
```

切回 v0.4 拍照稳定基线：

```powershell
git switch --detach v0.4-native-camera-gallery-import
```

切回 v0.6 Word 导出版本：

```powershell
git switch --detach v0.6-word-export
```

切回 v0.7 底部导航与设置版本：

```powershell
git switch --detach v0.7-bottom-navigation-settings
```

切回 v0.8 项目离线浏览版本：

```powershell
git switch --detach v0.8-project-export-html-viewer
```

切回 v0.9 管理功能完善版本：

```powershell
git switch --detach v0.9-management-polish
```

切回 v0.10 原始照片导出版本：

```powershell
git switch --detach v0.10-original-photo-export
```

切回 v0.11 项目与设备管理版本：

```powershell
git switch --detach v0.11-project-device-management
```

切回 v0.12 Word 格式与删除确认版本：

```powershell
git switch --detach v0.12-word-format-delete-confirmation
```

切回 v0.13 内置模板与原始照片目录版本：

```powershell
git switch --detach v0.13-built-in-templates-storage-path
```

切回 v0.14 根页面与模板列表滚动版本：

```powershell
git switch --detach v0.14-root-tabs-scroll-dialog
```

切回 v0.15 导出、状态与模板排序版本：

```powershell
git switch --detach v0.15-export-status-template-order
```

切回 v0.16 无页面转场动画版本：

```powershell
git switch --detach v0.16-no-navigation-animation
```

切回首个正式版本：

```powershell
git switch --detach v1.0.0
```

v0.1 至 v0.16 使用测试应用 ID `com.example.dengbaoevidence`；v1.0.0 起使用正式应用 ID `com.walter.dengbaoevidence` 和固定发布签名，因此手机会将首个正式版视为一个新 App。以后正式版只要保持应用 ID 和签名不变并提高版本号，即可覆盖升级并保留数据。
