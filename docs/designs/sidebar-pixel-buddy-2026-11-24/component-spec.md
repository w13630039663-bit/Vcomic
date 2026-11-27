# 侧栏像素互动伙伴 · 组件规格与实现映射（Component Spec）

## 1. 模块拆分（严守单文件 ≤ 400 行红线）
1. `DesktopPixelBuddyPrefs.kt`（`com.venera.desktop.settings`）：
   - 持久化偏好：`enabled`（总开关）、`count`（1~3 只）、`primarySkin`（主宠皮肤）、`searchTypingSpell`（搜索框聚焦拼字）、`searchCardBuild`（搜索结果拼装）、`cardClimb`（爬上漫画/画廊卡片）、`poopEnabled`（拉屎与铲屎彩蛋）。
   - 提供 `DesktopPixelBuddyBus`：供搜索框、卡片与业务事件向覆盖层广播聚焦、输入、确认、卡片矩形等轻量状态。
2. `DesktopPixelBuddySettingsSection.kt`（`com.venera.desktop.settings`）：
   - 在 `DesktopSettingsSection` 中新增独立选项卡 `PIXEL_BUDDY`（「宠物」），集中展示全部开关、数量选择、皮肤选择与快捷动作测试。
3. `PixelBuddySprites.kt`（`com.venera.desktop.ui.components`）：
   - 纯代码 ASCII 点阵与水平 RLE 批处理绘制器 + 内置 3×5 像素字模。
4. `PixelBuddyOverlay.kt`（`com.venera.desktop.ui.components`）：
   - 主窗体顶层覆盖层：侧栏竖爬、顶栏倒挂、搜索框右上角拼字、拖拽拎起甩飞重力弹跳、双宠贴贴/跳背、拉屎与点击铲屎。
5. `DesktopSearchField.kt` & `DesktopSearchFieldEffects.kt`：
   - 统一搜索框默认收窄（`0.56f`）、点击全宽展开、回车流光扫过与自动收窄，同时向 `DesktopPixelBuddyBus` 同步状态。
