# 画廊多选与批量操作设计令牌 — 2026-11-18

## 1. 颜色与材质令牌 (Color & Material Tokens)

| Token 变量 | 浅色模式 (Light) | 深色模式 (Dark) | 语义说明 |
|---|---|---|---|
| `dock.background` | `rgba(255, 255, 255, 0.85)` | `rgba(18, 22, 30, 0.85)` | 悬浮坞毛玻璃底板 |
| `dock.border` | `rgba(255, 255, 255, 0.70)` | `rgba(255, 255, 255, 0.12)` | 外层发丝高光描边 |
| `dock.shadow` | `0 12px 36px rgba(15, 23, 42, 0.16)` | `0 14px 40px rgba(0, 0, 0, 0.45)` | 悬浮坞立体景深投影 |
| `dock.text.primary` | `#1A1D21` | `#F1F5F9` | 坞内主文本色 |
| `dock.text.secondary` | `#5C6670` | `#94A3B8` | 次级与辅助说明色 |
| `dock.accent` | `#2563EB` | `#3B82F6` | 主色高亮与选中强调 |
| `dock.accent.tint` | `rgba(37, 99, 235, 0.10)` | `rgba(59, 130, 246, 0.16)` | 操作胶囊悬停底色 |
| `dock.danger` | `#DC2626` | `#EF4444` | 清空与破坏性操作图标 |
| `dock.hairline` | `rgba(0, 0, 0, 0.08)` | `rgba(255, 255, 255, 0.10)` | 竖向分割线与子元素细线 |

---

## 2. 几何与同心圆法则 (Geometry & Layout Tokens)

遵循 `Outer = Inner + Padding` 同心圆几何体系：

- **悬浮坞 (Floating Dock)**：
  - 高度：`52dp`
  - 外圆角：`26dp` (全圆角 Pill)
  - 内衬外边距：`horizontal 14dp, vertical 6dp`
  - 内部操作按钮高度：`40dp`，圆角：`20dp` (同心减量)
  - 最小宽度：`460dp`，最大宽度自适应内容不超过 `720dp`

- **批量弹窗 (Batch Modal)**：
  - 弹窗外框圆角：`18dp`
  - 弹窗宽度：`560dp`，最大高度：`640dp`
  - 内衬 Padding：`24dp`
  - 缩略图条 (Thumb Reel)：高 `76dp`，单张宽高比 1:1 或 3:4，圆角 `8dp`，移除 `✕` 钮半径 `14dp`。

---

## 3. 动效与手势规范 (Motion Tokens)

- **悬浮坞入场/退场**：
  - 入场：`translateY(40dp -> 0)` + `alpha(0 -> 1)`，采用 `Spring(stiffness = StiffnessMediumLow, dampingRatio = BouncyNo)`，耗时约 `220ms`。
  - 退场：`translateY(0 -> 30dp)` + `alpha(1 -> 0)`，耗时 `150ms easeIn`。
- **操作胶囊交互**：
  - 悬停：`scale(1.02)`，底色淡入 `80ms`。
  - 按压：`scale(0.96)`，回弹 `120ms`。
- **计数数字跳动**：
  - 采用 `AnimatedContent` 纵向轻微位移滚入，增强选择反馈感。
