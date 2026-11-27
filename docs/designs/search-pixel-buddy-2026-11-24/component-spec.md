# 搜索与画廊像素伙伴（分列踩台阶 × 列光流）· 组件规格与实现映射

## 1. 模块拆分与行数门禁
1. `DesktopPixelBuddyPrefs.kt`（`com.venera.desktop.settings`）：
   - 在 `DesktopPixelBuddyBus` 新增卡片绘制会话快照 `cardPaintState`（`sessionId`、`waitingKeys`、`skimmingProgress`、`awakenedKeys`）与滚动打断信号 `scrollInterruptTick`。
   - 新增 `reportCardPaintState(...)` 与 `reportUserScroll()`。
2. `PixelBuddyCardPaintState.kt`（新建于 `com.venera.desktop.ui.components`，≤ 360 行）：
   - 独立承载卡片按横坐标分列、台阶种子选取、1~3 只动态抢单算法、`leap` / `skim` 真实毫秒状态机、列多米诺光流推进、画笔轨迹与火花粒子更新及画布绘制。
3. `PixelBuddyOverlay.kt`（瘦身至 ≤ 480 行）：
   - 移除旧版 `CardGlowSweep` 与 `chunked` 瞬跳逻辑，改为委托 `PixelBuddyCardPaintController` 驱动搜索完成后的台阶描绘与滚动打断收尾。
4. `DesktopSearchFieldEffects.kt`（`com.venera.desktop.ui.components`）：
   - 在 `Modifier.registerPixelBuddyCard(key)` 内融合暗幕实时擦除与唤醒扫光绘制（仅当前卡片属于活跃描绘会话时渲染，平时零额外绘制）。
5. `GallerySearchControls.kt` & `DesktopGallerySearchScreen.kt` & `DesktopSearchScreen.kt`：
   - 画廊搜索框补齐 `searching` 传参；漫画与画廊列表在 `isScrollInProgress` 时触发 `DesktopPixelBuddyBus.reportUserScroll()`。
