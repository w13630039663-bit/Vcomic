# 搜索与画廊像素伙伴（分列踩台阶 × 列光流）· 设计令牌

- 沿用全局基准：`desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md` 与 `desktop/docs/designs/sidebar-pixel-buddy-2026-11-24/design-tokens.md`。
- 角色像素尺寸：逻辑像素格 `2.dp`（点阵 `16 × 10` → 实际渲染 **`32 × 20 dp`**），搜索框右上角间距 `38.dp`，点击热区 `36.dp`。

## 1. 真实毫秒计时与步频令牌（跨 60Hz~240Hz 屏幕一致）
| Token | 数值 | 用途 |
|---|---|---|
| `leapDurationMs` | `270 ~ 440 ms` | 按跳跃距离线性映射的抛物线跃迁时长 |
| `leapArcHeightDp` | `30.dp` | 踩台阶跨跳最高弧高 |
| `skimDurationMs` | `290 ~ 440 ms` | 按卡片宽度映射的顶边小跑擦亮时长 |
| `singlePetPaceMultiplier` | `1.12f` | 仅 1 只宠物上场时的轻快步频倍率 |
| `columnBeamSpeedDpPerSec` | `360.dp/s` | 列多米诺光流向下流淌物理速度 |
| `returnJumpStep` | `0.038f * dt` | 收尾或打断跳回侧边栏的弧线步进 |

## 2. 卡片擦除与唤醒视觉令牌
| 状态 | 视觉参数 |
|---|---|
| 等待唤醒 (`Waiting`) | 暗幕遮罩渐变 `rgba(148,127,214,0.24) → rgba(24,26,33,0.82)`，内容保持可读且可点 |
| 宠物擦除中 (`Skimming`) | 遮罩随 `progress` 从左向右实时缩回（`clip` / 起点右移），前沿带微光竖线，高亮边框 |
| 唤醒完成 (`Awakened`) | `340ms` 遮罩退场过渡 + `560ms` 斜向扫光（`VeneraMotion.easingDecelerate`）+ `3` 颗像素火花 |
| 无障碍降级 (`VeneraMotion.reduceMotion`) | 跳过跳跃、擦除与光流，直接静默显示 |
