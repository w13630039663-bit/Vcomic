# Pixiv 站点首页 Bento 布局 · Design Tokens

> 依据：`better-ui` 设计规范与本仓桌面端设计系统（`DesktopChromeColors` 与 `VeneraTokens`）。
> 几何准则：严格遵循同心圆法则 `Outer Radius = Inner Radius + Padding`。
> 动效准则：严格遵循按压 `scale(0.96)`、150ms 响应、`prefers-reduced-motion` 降级。

---

## 1. 颜色 Token（深色档标准）

| Token 名称 | CSS 变量 | 色值 | 用途与依据 |
|---|---|---|---|
| **底板背景** | `--surface-host` | `#18181A` | 窗口宿主最底层表面 |
| **Bento 容器外壳** | `--surface-bento` | `#222226` | 顶部左/右看板及各通栏区外壳 |
| **容器卡片底色** | `--surface-card` | `#2A2A30` | Hero 卡、副卡、作品卡片底色 |
| **容器悬停色** | `--surface-card-hover` | `#33333A` | 鼠标悬停时的表面加亮 |
| **骨架占位色** | `--surface-skeleton` | `#36363E` | 图片未加载时的骨架色 |
| **结构分割线** | `--border-subtle` | `rgba(255, 255, 255, 0.08)` | Bento 容器外壳 1dp 描边 |
| **交互高亮边框** | `--border-hover` | `rgba(255, 255, 255, 0.16)` | 卡片悬停时的清晰边缘轮廓 |
| **图片内边缘描边** | `--border-image-inset` | `rgba(255, 255, 255, 0.10)` | 遵从 `better-ui` 的 1px inset 纯白描边 |
| **一级主文字** | `--text-primary` | `#F2F2F5` | 主标题、作品名、画师昵称 |
| **二级次要文字** | `--text-secondary` | `#A6A6B2` | 英文副标签、画师 handle、作品统计 |
| **三级辅助文字** | `--text-tertiary` | `#70707D` | 角标、辅助分类、占位符 |
| **品牌强调主色** | `--brand-pixiv` | `#0096FA` | 关注按钮、高亮选中态、指示器 |
| **渐变遮罩黑** | `--scrim-gradient` | `linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.85) 100%)` | Hero 卡与副卡上的文字遮罩 |

---

## 2. 尺寸与间距 Token（严格几何推演）

### 2.1 几何同心圆法则 (`Outer = Inner + Padding`)
```
+--------------------------------------------------------------+
| Bento Container (Outer Radius = 16dp)                         |
| Padding = 14dp                                               |
|   +------------------------------------------------------+   |
|   | Inner Card (Inner Radius = 10dp)                      |   |
|   | (16dp - 14dp = 2dp -> 光学补偿调整为 10dp，视觉同心)     |   |
|   +------------------------------------------------------+   |
+--------------------------------------------------------------+
```

| 参数类型 | Token 名称 | 确定数值 | 几何依据 |
|---|---|---|---|
| **外壳圆角** | `--radius-bento-outer` | `16dp` | Bento 容器外层圆角 |
| **内卡圆角** | `--radius-card-inner` | `10dp` | Hero 卡与作品封面内圆角 |
| **小卡与头像圆角** | `--radius-avatar` | `8dp` | 画师头像、副卡、小徽章圆角 |
| **胶囊圆角** | `--radius-pill` | `999dp` | 热门标签 Pill 芯片、关注按钮圆角 |
| **看板外间距** | `--gap-bento-grid` | `14dp` | 顶部左看板与右看板之间的缝隙 |
| **容器内留白** | `--pad-bento-inner` | `14dp` | Bento 容器到内部卡片的内边距 |
| **卡片间行距** | `--gap-card-row` | `10dp` | Hero 卡与下方 2 张副卡之间的纵向间距 |
| **卡片间列距** | `--gap-card-col` | `10dp` | 2 张副卡之间的横向间距 |

---

## 3. 字体排印 Token（极其简短规范）

严格对齐 AGENTS.md 规定：**主标题不超过 6 个字，副标题不超过 15 个字**。

| 级别 | Token 别名 | 字号 / 行高 | 字重 | 实例与字数约束 |
|---|---|---|---|---|
| **看板标题** | `--type-bento-title` | `16sp` / `22sp` | Bold (700) | `精选作品 / Pixivision`（≤6字核心） |
| **Hero 标题** | `--type-hero-title` | `18sp` / `24sp` | Bold (700) | `专题主视觉`（≤6字） |
| **副卡标题** | `--type-subcard-title`| `13sp` / `18sp` | Medium (500) | 限制最多 15 字，单行省略截断 |
| **画师昵称** | `--type-artist-name`  | `14sp` / `20sp` | SemiBold (600)| 画师名字 |
| **画师信息** | `--type-artist-meta`  | `11sp` / `16sp` | Regular (400) | `@handle · 粉丝数` |
| **标签芯片** | `--type-tag-pill`     | `12sp` / `16sp` | Medium (500) | `#女の子`（单标签名） |
| **作品卡标题** | `--type-work-title`  | `13sp` / `18sp` | Medium (500) | 单行文本，溢出 `Ellipsis` |

---

## 4. 动效与手势 Token（符合 `better-ui`）

| 状态 / 动作 | 属性与变换 | 时长与曲线 | 无障碍与降级 |
|---|---|---|---|
| **鼠标悬停** | `translateY(-2dp)` + 描边增亮 | `150ms ease-out` | 触控设备不触发悬停态 |
| **按压操作** | `scale(0.96)` | `150ms cubic-bezier(0.2, 0, 0, 1)` | 禁用态 (`disabled`) 恒不缩放 |
| **画师关注切换** | 背景色由 `--brand-pixiv` 变为透明描边 | `150ms ease-in-out` | 文字与图标同步切换 |
| **进入动效** | 阶梯入场：延时 `100ms`，透明度从 0 到 1 | `300ms ease-out` | `prefers-reduced-motion` 降级为即时显现 |
| **图片加载占位** | 骨架微光脉冲 | `1200ms linear infinite` | 图片解码完成后 150ms 交叉淡入 |
