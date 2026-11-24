# VComic v1.0.1 更新说明

## 新特性与体验优化
- **「关于与更新」页面重做与自动更新**：新增应用介绍 Hero 卡片、启动静默自动检查更新开关（24 小时节流）、页内更新日志展示，以及带实时进度、速度、SHA-256 校验与一键拉起安装包的下载更新流程。
- **界面动效与视觉细节升级**：强化导航切换、卡片交互与设置弹窗的流畅动效表现。
- **Windows / Linux 双平台构建**：完善 Linux XDG 目录、原生文件选择器与协议注册，支持通过 GitHub Actions 同步产出 Windows（`setup.exe` / 便携 `zip`）与 Linux（`AppImage` / `tar.gz`）安装包。

## 架构重构与工程质量
- **多模块物理边界隔离**：拆出纯 JVM 无 UI 依赖的 `:core-data` 与 `:gallery-core` 模块，并将调试探针隔离至 `src/probe/kotlin` 源集，不进入安装包。
- **巨型源文件拆分与三层归位**：完成仓库 TOP 10 超大文件拆分，并将 `com.venera.desktop` 根目录源码归位至 `app/`、`data/`、`ui/` 三层，实现根目录零散落文件与 `data` 层零 Compose 反向依赖。
- **测试提速与双静态护栏**：优化并行测试与无头运行配置，新增 `DesktopFileSizeAndLayerGuardTest` 守卫文件体积上限与分层依赖边界。

## 下载说明
- **Windows**：
  - `VComic-1.0.1-setup.exe`：单文件自解压图形化安装程序（免管理员权限）。
  - `VComic-1.0.1-portable-win-x64.zip`：解压即用的绿色便携包。
- **Linux**：
  - `VComic-1.0.1-linux-x64.AppImage`：单文件可执行镜像（赋予可执行权限即可运行）。
  - `VComic-1.0.1-linux-x64.tar.gz`：绿色便携压缩包。
