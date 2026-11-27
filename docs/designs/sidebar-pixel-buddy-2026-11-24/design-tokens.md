# 侧栏像素互动伙伴 · 设计令牌（Design Tokens）

## 1. 像素与节拍令牌
| Token | 数值 | 用途 |
|---|---|---|
| `pixelCellSize` | `3.dp` | 逻辑像素块边长 |
| `spriteGrid` | `16 × 10` | 标准角色点阵尺寸（绘制尺寸 `48 × 30 dp`） |
| `animTickMs` | `100ms` | 离散复古帧切换节拍 |
| `physicsFrameMs` | `16ms` | 拖拽/下落/弹跳/跨跳物理步进 |

## 2. 调色板令牌
| 皮肤 | 主色 `#` | 暗部 `s` | 瞳色 `o` |
|---|---|---|---|
| 薰衣草紫猫 (`CAT`) | `#947FD6` | `#735EB5` | `#141413` |
| 暖橘小方块 (`ORANGE`) | `#D97757` | `#BF684C` | `#141413` |
| 雾蓝小兔 (`BLUE`) | `#6B9FD4` | `#5280B3` | `#141413` |

## 3. 动效与搜索框联动令牌
- 搜索框收窄比例：`0.56f`（未聚焦 / 搜索完成）→ `1.0f`（聚焦全宽），时长走 `VeneraMotion.tweenNormal()`（`220ms`）。
- 确认扫光时长：`420ms`，缓动 `VeneraMotion.easingDecelerate`。
- 降级规则：`VeneraMotion.reduceMotion == true` 时关闭跳跃/弹跳/砖块拼合，仅保留静态驻留与轻淡入。
