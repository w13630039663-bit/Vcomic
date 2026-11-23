---
trigger: glob
globs: desktop/src/main/kotlin/com/venera/desktop/reader/**
---

# 阅读器开发与修改防呆纪律

1. **禁止假开关与半截接线**：新增或修改任何阅读器设置项（`DesktopReaderPrefsState`）时，必须同步检查并接通：阅读器内设置面板、全局设置页（`DesktopSettingsHub`）、以及实际渲染视口（分页 / 3D 翻页 / 导览视图 / 连续滚动）。
2. **手势与滚轮分层放行**：在 `PointerEventPass.Initial` 拦截滚轮或拖拽时，当浮层（设置、弹窗、放大镜）打开或指针在侧边栏上时，严禁调用 `change.consume()`，必须放行给子组件。
3. **全路径动画与缩放一致**：任何翻页模式（含 3D 翻页、导览视图）必须同时响应点击热区、键盘方向键、顶栏按钮、进度条与自动翻页；所有视口模式必须统一接入外层 `graphicsLayer` 缩放与放大后拖拽平移。
4. **图像管线缓存对齐与兜底**：
   - 预加载（`computePreloadIndices`）的 `model` 必须与渲染层（`ReaderPagedCell`）完全一致（含 `DesktopAiPage`），且前后预加载范围不少于 6 页。
   - 自定义 Coil `Fetcher` 遇到解码或处理失败时，必须回退返回原始字节流，严禁返回 `null` 导致整页黑屏报错。
   - 翻页切换页码的渲染槽位必须使用 `key(pageIdx)` 固定组合身份，防止重组闪烁。
