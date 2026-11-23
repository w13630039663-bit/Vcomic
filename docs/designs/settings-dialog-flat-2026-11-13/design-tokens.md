# 设置弹窗扁平化 · Design Tokens

> 色值来源：截图取色（估算）+ 本仓既有 `SettingsGlassPalette` 语义色复用。
> 「确定」= 本仓既有常量；「估算」= 按截图目视取色，落地允许 ±8 明度微调。
> 所有 Token 在 HTML 方案稿里以 `:root` CSS 自定义属性呈现；Compose 侧落在新增的
> `SettingsDialogPalette`（`SettingsDialogShell.kt` 内），不扩散到全仓。

## 全局颜色 Token（深色档，默认）

| Token | 值 | 用途 | 依据 |
|---|---|---|---|
| `--dlg-bg` | `#2B2B2B` | 弹窗底 | 估算 |
| `--dlg-bg-rail` | `#2B2B2B` | 左轨底（与弹窗同色，不另起卡面） | 确定（截图无独立轨底） |
| `--dlg-divider` | `#3A3A3A` | 左轨/右栏竖向分区线、行分隔线 | 估算 |
| `--dlg-divider-strong` | `#454545` | 顶栏下缘线 | 估算 |
| `--dlg-scrim` | `rgba(0,0,0,.5)` | 弹窗外遮罩 | 估算 |
| `--dlg-hover` | `rgba(255,255,255,.05)` | 行/分区项悬停底 | 估算 |
| `--dlg-pressed` | `rgba(255,255,255,.09)` | 行按压底 | 估算 |
| `--dlg-selected` | `rgba(255,255,255,.09)` | 分区选中条 | 估算 |
| `--dlg-focus` | `#5B9CFF` | 焦点环（2dp） | 确定：复用 `SettingsGlassPalette.Deep.glassBorderFocus` |
| `--dlg-text-1` | `#EAEAEA` | 行标题、弹窗标题、昵称 | 估算 |
| `--dlg-text-2` | `#A8A8A8` | 分组标题、当前值、副行、未选中分区文字 | 估算 |
| `--dlg-text-3` | `#7C7C7C` | 版本尾注、箭头、占位文字 | 估算 |
| `--dlg-field` | `#303030` | 搜索框/行内输入框底 | 估算 |
| `--dlg-field-border` | `#454545` | 输入框 1dp 描边 | 估算 |
| `--dlg-on-accent` | `#FFFFFF` | 彩色图标块内图标、开关滑块 | 确定 |

对比校验：`#EAEAEA` on `#2B2B2B` ≈ 11.9:1；`#A8A8A8` on `#2B2B2B` ≈ 6.4:1；
`#7C7C7C` on `#2B2B2B` ≈ 3.6:1（仅用于非文字装饰与 11sp 尾注，需降为装饰用途，
尾注改用 `--dlg-text-2` 以保 4.5:1）。

## 组件级语义色（彩色图标块，纯色不渐变）

| Token | 值 | 分组/用途 |
|---|---|---|
| `--ico-purple` | `#7C5CFF` | 外观、声音类 |
| `--ico-green` | `#22C55E` | 启动、播放类 |
| `--ico-blue` | `#3B82F6` | 通用、网络、文件类 |
| `--ico-cyan` | `#06B6D4` | 阅读、数据类 |
| `--ico-orange` | `#F97316` | 文件夹、媒体类 |
| `--ico-slate` | `#64748B` | 中性/不可用项 |
| `--switch-on` | `#3B82F6` | 开关选中轨 |
| `--switch-off` | `#555555` | 开关未选中轨 |
| `--danger` | `#F87171` | 危险操作文字按钮（沿用 `SettingsGlassPalette.Deep.danger`） |

彩色块内图标恒为 `--dlg-on-accent`；选中分区的彩色块不变色，只有行底变 `--dlg-selected`。

## 尺寸 Token

| Token | 值 | 说明 |
|---|---|---|
| `--dlg-w` / `--dlg-h` | `760dp` / `790dp` | 目标尺寸 |
| `--dlg-min-w` / `--dlg-min-h` | `720dp` / `480dp` | 最小尺寸 |
| `--dlg-max-w` / `--dlg-max-h` | `960dp` / `860dp` | 最大尺寸 |
| `--dlg-radius` | `24dp` | 弹窗圆角（Outer） |
| `--inner-radius` | `16dp` | 内容容器圆角（Inner） |
| `--edge-pad` | `8dp` | Outer − Inner 的 Padding |
| `--topbar-h` | `56dp` | 顶栏高 |
| `--rail-w` | `200dp` | 左轨宽 |
| `--rail-w-compact` | `64dp` | 窄窗左轨宽 |
| `--content-pad` | `24dp` | 右栏左右留白 |
| `--row-h` | `48dp` | 行高（点击区内） |
| `--nav-h` | `40dp` | 分区项高 |
| `--ico-block` | `24dp` | 彩色图标块边长 |
| `--ico-radius` | `7dp` | 彩色图标块圆角 |
| `--row-radius` | `8dp` | 行/分区项圆角 |
| `--field-radius` | `8dp` | 搜索框/输入框圆角 |
| `--avatar` | `40dp` | 用户头像直径 |
| `--hit-min` | `44dp` | 最小点击区 |

同心圆校验：`24 = 16 + 8`（弹窗 ↔ 内容容器）；`24 = 7 + (图标内距)`（图标块自身圆角与内图标留白独立，不套 Outer 法则）。

## 间距 Token

| Token | 值 | 用途 |
|---|---|---|
| `--sp-1` | `4dp` | 图标与文字最小间隙 |
| `--sp-2` | `8dp` | 分组标题下、行内元素间隙 |
| `--sp-3` | `12dp` | 行左右内距、图标块↔标题 |
| `--sp-4` | `16dp` | 用户卡上下、顶栏左右留白 |
| `--sp-5` | `20dp` | 分组前留白 |
| `--sp-6` | `24dp` | 右栏内容留白、左轨尾部留白 |

## 字体排印 Token

沿用本仓 `VeneraType`，不新造字阶；只做角色映射：

| 角色 | 字号 / 行高 | 字重 | 色 |
|---|---|---|---|
| 弹窗标题 | 14sp / 20sp | Medium | `--dlg-text-1` |
| 分区项文字 | 14sp / 20sp | Normal（选中 Medium） | `--dlg-text-1` / `--dlg-text-2` |
| 行标题 | 14sp / 20sp | Normal | `--dlg-text-1` |
| 行副文 | 11sp / 16sp | Normal | `--dlg-text-2` |
| 当前值 / 箭头 | 12sp / 18sp | Normal | `--dlg-text-2` |
| 分组标题 | 12sp / 18sp | Medium | `--dlg-text-2` |
| 版本尾注 | 11sp / 16sp | Normal | `--dlg-text-2` |
| 搜索占位 | 12sp / 18sp | Normal | `--dlg-text-3`（占位非标签，标签由内容描述承载） |

正文不小于 11sp；长文本单行省略（`TextOverflow.Ellipsis`），不换行撑破行高。

## 阴影 / 微光 Token

| Token | 值 | 说明 |
|---|---|---|
| `--dlg-shadow` | `0 24dp 64dp rgba(0,0,0,.45)` | 弹窗唯一投影，有偏移，非零偏移彩色光晕 |
| `--dlg-hairline` | `1dp --dlg-divider` | 行分隔线，无发光 |

禁：玻璃高光、焦点光晕层、多层叠加投影。焦点只用 2dp `--dlg-focus` 环。

## 动效 / 手势 Token

| Token | 值 | 说明 |
|---|---|---|
| `--motion-feedback` | `150ms` | 悬停/按压/选中底色过渡 |
| `--motion-nav` | `180ms` | 分区切换时右栏淡入 |
| `--motion-dialog` | `220ms` | 弹窗进出（缩放 0.98→1 + 透明度） |
| `--press-scale` | `0.96` | 按压弹性缩放（spring，无过冲） |
| `--easing` | `cubic-bezier(.2,.8,.2,1)` | 标准减速 |

只动 `transform` 与 `opacity`；尊重系统「减少动态效果」：开启时把过渡时长置 0，不改变最终状态。

## 状态 Token 汇总

| 状态 | 行底 | 文字 | 图标块 | 边框 |
|---|---|---|---|---|
| 默认 | 透明 | `--dlg-text-1` | 分组色 | 无 |
| 悬停 | `--dlg-hover` | 不变 | 不变 | 无 |
| 按压 | `--dlg-pressed` + scale `.96` | 不变 | 不变 | 无 |
| 焦点 | 不变 | 不变 | 不变 | 2dp `--dlg-focus` |
| 选中（分区） | `--dlg-selected` | `--dlg-text-1` | 保持彩色 | 无 |
| 禁用 | 透明 | `--dlg-text-3` | `--ico-slate` | 无 |
