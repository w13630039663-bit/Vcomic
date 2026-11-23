# 本地文件夹原地导入（单个漫画）

## 1. 背景与目标

- 解决痛点：用户本地已解压或整理的漫画文件夹无法直接加入「本机内容 → 漫画」，只能先手动打包成 CBZ/ZIP 再解压复制一遍，费时且占用双倍磁盘空间。
- 核心目标：在「下载与本地 → 本机内容 → 漫画」新增「导入文件夹」入口，支持选择单个漫画文件夹并原地关联入库（零文件拷贝），同时支持单话图片文件夹与多话子目录结构。
- 非目标（Non-Goals）：
  - 本期不做一次选中父目录批量递归导入多本独立漫画（避免误扫大型图库或系统目录导致卡顿与误入库）；
  - 不复制文件夹内容到下载目录（用户已明确选择原地关联模式，删卡时仅移除台账记录，不删除硬盘原文件夹）。

## 2. 用户操作流

1. 入口：在「下载与本地」页面「本机内容 → 漫画」右侧点击「导入文件夹」。
2. 过程：弹出系统原生文件夹选择框（`DesktopDirChooser`），选中目标漫画文件夹后：
   - 若文件夹内直接包含图片，按单话漫画入库；
   - 若文件夹内包含多个装有图片的子文件夹，按多话漫画入库；
   - 原地记录到 `desktop_downloads.db` 的 `local_comic_imports` 表，零文件拷贝。
3. 结果：漫画卡片立即出现在「本机内容 → 漫画」中，点击可直接阅读（多话可先选话再阅读）；切换在线漫画下载目录不影响已导入的漫画；移除卡片时仅解除关联，保留硬盘原文件夹。

## 3. 范围

- 涉及屏与路由：`DesktopDownloadsPipelineScreen`、`DesktopComicAndSourceHost`、`DesktopComicAndFilesEffects`、`DesktopRoute.LocalComicReader`。
- 涉及表与列：`desktop_downloads.db` 新增 `local_comic_imports` 表（无历史库迁移风险，`CREATE TABLE IF NOT EXISTS`）。
- 涉及启动参数或构建项：无。

## 4. 边界与异常

- 空态：取消文件夹选择框不做任何操作；选中空文件夹或不含支持格式图片（jpg/jpeg/png/webp/gif/avif）的文件夹时，提示明确原因且不入库。
- 失败：原文件夹后续在系统里被移动或删除时，不崩溃且可正常移除失效关联记录。

## 5. 验收

- 判据：`:engine-probe:compileKotlin`、`:desktop:compileKotlin`、`:desktop:checkDuplicateImports` 通过，补齐 `DesktopDownloadsDbTest`、`DesktopLocalComicImportSafetyTest` 与 `DesktopDownloadsPipelineScreenTest` 用例并全绿。
- 端到端验证：真窗下导入单话/多话文件夹可正常出卡、出封面、阅读，切换下载目录后旧漫画标题封面及已导入漫画均不丢失，下载中在线漫画不会显示成「导入」。

## 6. 文档影响

- [x] `docs/02-architecture/database-schema.md`
- [x] `docs/CHANGELOG.md`
