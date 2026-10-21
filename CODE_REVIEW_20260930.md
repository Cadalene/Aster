# Aster 代码检查记录

检查日期：2026-09-30。检查对象：当前 Android 项目、离线 PC 浏览页、导出与模板导入流程。

此次新增版本为 1.6.1（versionCode 32）。已修改 PC 浏览页的三项需求：取证项不显示关键截图标签；放大图片可按住鼠标拖动；移除图片下载入口。保留设备层面的关键截图完整性提示、滚轮缩放和左右方向键切图。

以下为 v1.6.1 检查发现的问题，位置与复现记录保留作历史参考；已在 v1.7.0 修复，见文末修复记录。按处理优先级排列。

## 1. [P1] 同名项目导致全量备份失败

位置：[EvidenceViewModel.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/EvidenceViewModel.kt#L232)，相关写入在第 239 行。

项目允许同名；备份仅以替换非法字符后的项目名作为 ZIP 内目录。两个同名项目都会写入同一个 `项目名/PC浏览包.zip`。Java 的 `ZipOutputStream` 拒绝重复条目并抛出异常，备份在第二个项目处中止。不同原名经字符替换后相同，也会触发。

建议给备份项目目录添加项目 ID 或处理唯一目录名，不修改项目本身名称。

## 2. [P2] 备份失败后留下不完整的目标 ZIP

位置：[EvidenceViewModel.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/EvidenceViewModel.kt#L228)，失败处理在第 263 行。

备份直接写入目标文件，失败时只清理临时目录并显示失败提示，未清理目标文件。前几个项目可能已经写入，且流关闭时 ZIP 仍可能具有可解压的结构，但项目或模板不完整。普通项目导出则有删除失败目标的处理。

建议失败时清理本次新建的目标文件；对用户选择已有文件的情况，需要明确区分新文件与覆盖文件。完成时校验项目数量和必需条目。

## 3. [P2] 串行备份仍累积所有项目的临时导出文件

位置：[EvidenceViewModel.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/EvidenceViewModel.kt#L233)，清理在第 258 行。

每个项目的浏览 ZIP 和 Word 写入总备份后仍保留在缓存中，直到所有项目完成才删除。大量照片时，内部缓存占用持续增加；即使外部目标目录空间充足，也可能因内部存储不足而使备份失败。

建议每个项目写入总 ZIP 后立即清理该项目的临时文件，使用 `try/finally` 保证异常时也能清理。

## 4. [P2] PC 的“仅符合”筛选会包含未记录项

位置：[project_viewer.html](app/src/main/assets/project_viewer.html#L292)。

筛选根据 `isCompliant` 判断结论，没有检查 `isRecorded`。未记录项默认 `isCompliant = true`，因此会出现在“仅符合”结果中。

已使用浏览器复现：一个已记录符合项 `Policy` 和一个未记录项 `Empty`，选择“仅符合”后仍同时显示两项。界面徽标对未记录项不显示结论，因此筛选结果与徽标语义不一致。

建议结论筛选仅匹配已记录项；全部结论仍包含未记录项。

## 5. [P2] 导出进度与实际完成时机不一致

位置：[WordExporter.kt](app/src/main/java/com/example/dengbaoevidence/data/WordExporter.kt#L55)、[ProjectPackageExporter.kt](app/src/main/java/com/example/dengbaoevidence/data/ProjectPackageExporter.kt#L58)。

两个导出器在本地临时文件生成结束时报告 100%，但 ViewModel 随后才将文件复制到用户选择的目录。大文件或较慢的存储提供器会使界面长时间显示 100%，此时目标文件仍在写入。

备份在 [EvidenceViewModel.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/EvidenceViewModel.kt#L236) 只在每个项目开始时更新进度，未接收浏览包和 Word 的内部回调，也未显示原图复制进度。单个大项目处理中可能一直显示 0%。

建议分别显示生成、写入和校验阶段；最终目标文件关闭并校验后再报告完成。备份进度组合当前项目的内部进度。

## 6. [P2] 首次选择默认目录后，原导出操作没有继续

位置：[SettingsScreen.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/SettingsScreen.kt#L428)，目录选择回调在第 99 行附近。

没有默认目录时，点击“保存到默认位置”先清空 `pendingExport`，再打开目录选择器。回调仅保存目录偏好，未恢复模板导出或备份操作。用户选择目录后需再次点击导出。

建议在目录选择期间保留待导出类型，取得目录后继续当前导出；取消选择时清除待处理状态。

## 7. [P3] 部分原先约定的导出文案和日期规则尚未落实

位置：[SettingsScreen.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/SettingsScreen.kt#L369)、[EvidenceViewModel.kt](app/src/main/java/com/example/dengbaoevidence/ui/main/EvidenceViewModel.kt#L239)。

模板导入仍先列出全部同名模板，再分别列出一致和差异模板，名称依然重复出现。备份内部的浏览 ZIP、Word 和模板 JSON 没有日期，虽然外层备份 ZIP 有日期。

建议同名模板只分别列出一致和差异两组；统一导出文件的日期命名规则。

## 验证与限制

- 真实 Edge 浏览器自动验证 PC 图片滚轮缩放、鼠标等距离拖动、拖动边界、缩小复位、左右方向键切图、关闭后重新打开复位、标签与下载入口移除。
- 检查了 1440×900 和 390×844 两种视口截图，图片与关闭按钮保持在窗口内。
- 浏览器未出现 JavaScript 运行错误。
- 验证脚本：`tools/tests/project_viewer_smoke.cjs`；截图位于 `app/build/reports/pc-viewer/`。
- `assembleRelease`、`assembleDebugAndroidTest`、`lintDebug` 成功；lint 为 0 个错误、22 个警告和 4 个提示。`testDebugUnitTest` 为 `NO-SOURCE`，不代表已有单元测试执行通过。
- 已确认 APK 内的 PC 页面与当前源码完全一致，APK 的版本为 1.6.1 / 32，签名验证通过，发布副本与构建产物的 SHA-256 一致。
- 当前没有连接 Android 手机，设备测试只能编译，无法运行；备份异常和 SAF 文件写入行为尚需设备验证。

## v1.7.0 修复记录

本次优先保护源项目、照片及已有导出文件；数据库仍为版本 4，不修改迁移和已有实体结构。

1. 项目创建、改名的重名检查与写入在同一个 Room 事务内；历史重复项目不会改名或删除。备份目录含项目 ID，可兼容历史同名与文件名清洗碰撞。
2. 导出选择文件夹，生成新的唯一文件名，不再通过“创建文档”选择已有文件覆盖。生成失败时还没有目标文件；写入或校验失败时清理本次新建目标，清理失败会明确提示。
3. 备份串行处理；当前项目合并后立即清理缓存，异常也清理。如果不能释放当前项目缓存，则停止而不是继续累积。外层 ZIP 在缓存中生成并校验，再写入目标目录。
4. PC 的符合、不符合、不适用筛选仅匹配已记录项；全部结论仍保留未记录项。
5. 0–75% 为生成，75–99% 为目标写入及回读 SHA-256 校验，最终流关闭且字节数和哈希匹配后才报告 100%。备份接收当前项目内部进度和原图合并进度。
6. 设置及项目页的目录选择保留待导出类型；首次设置默认目录后继续该导出，取消则清空待处理操作。目录持久化前检查读写权限。
7. 导入弹窗仅分别列出一致项和差异项，差异明细独立滚动，处理策略固定在下方；备份里的浏览 ZIP、Word、模板 JSON 统一加 `yyyyMMdd` 日期。

另外：导出任务的进度保存在 ViewModel，离开页面后仍保持；阻止并发导出及导出期间删除项目、设备、照片；导出前保存待写入的备注；全量备份使用一次数据库事务取得项目和模板快照；Word 的图片准备失败也会释放已经转换的临时图片，登记过但缺失的关键照片会报错而非静默遗漏。

验证命令及 Windows 中文路径编码说明见 `README.md`。新增 JVM 测试覆盖历史同名目录、同名设备原图、串行缓存释放、生成失败、完整性校验失败、流关闭后的最终进度、空项目备份和日期命名。Room 重名与并发创建测试已加入 Android 测试集，需要手机运行；没有把“编译成功”记作“设备测试通过”。

人工复核：覆盖安装后旧项目与模板是否保留；四种导出的默认/其他目录及取消操作；同一天重复导出是否保留旧文件；大项目导出后原图数量和 Word 内容；小屏幕模板导入弹窗的三种策略是否可见。导出中应保持 App 运行，不将强制终止期间产生的目标文件当作有效备份。

最终验证：

- JVM 11 项测试执行通过，0 失败、0 跳过；PC Edge 回归测试通过。
- `assembleRelease`、`assembleDebugAndroidTest`、`lintDebug` 成功。lint 0 错误、22 警告、2 提示；未连接设备，Room 和 Android 界面测试未执行。
- APK 版本为 1.7.0 / 33，包名 `com.walter.dengbaoevidence`，显示名 Aster；发布签名 SHA-256 与旧版一致：`13e52062b228a25e4e7c2901ea9b2242e559ff567048352a40f1faf028b00518`。
- 新旧 APK 内均包含同一 Room schema identity `736f33bf9a93480ec6aee24e15a57eed`；不需要本次数据库迁移。
- APK 内 `assets/project_viewer.html` 与源码 SHA-256 完全一致。发布副本与构建产物 SHA-256 均为 `A212D9252E596A0023E9C3241C79E8C939383B3C2E98AB23F97117680CBA57EF`。
- 发布文件：`release/Aster-v1.7.0-export-data-safety-release.apk`。v1.6.1 APK 原样保留，SHA-256 仍为 `07790A828492D1A5A89EB030C5055B8299CAA4D79F30C2C8E3EB654D874A8526`。
