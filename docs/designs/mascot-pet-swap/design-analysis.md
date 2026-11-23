# 薇薇桌宠换芯 · 设计分析

- 稿源：主人定稿 v2/v3 弹簧引擎稿（`Downloads/venera.html` 2256 行版 + v3 扩展），验收目标即该稿的极简剪影支线。
- 主人拍板：双色靛蓝不做，只做极简剪影；台词接现有 `DesktopPetLines` 词库。

## 替换对象

- `DesktopPetSlot` 原六张 PNG 状态帧（pet_idle/love/happy/tap/dizzy/busy）整组退役，渲染换 `DesktopMascotPet`。
- 保留：台词气泡（含折叠档竖排分列）、badge 状态点、免打扰开关、连戳计数、CueBus 接线。

## 形状（120×120 视口，确定值）

- V 臂：`M60,74 L36,28` / `M60,74 L84,28`，描边 17.5。
- 身体：`M45,55 L75,55 L74,79 Q60,89 46,79 Z` 填充。
- 书页翼：`M59,87 Q37,71 15,80.5` / 镜像，描边 15.2。
- 底部缺口：`(60,86)→(60,98.5)`，宽 3.4，DstOut 抠穿。
- 眼：胶囊 4.5×10.6 rx2.25（异常档 8.6）；弯月弧 `Q54,61.6 57.6,68.2` 描边 2.8；内倾委屈眼旋转 ±17°。
- 剪影透明度 0.84（打盹 0.62），星 `#FBBF24`、心 `#F87171`、短线走主题前景。

## 姿态映射表（事件 → 姿态，一次钉死）

- P8 → ANOMALY；P7 → ALERT；P6 → DONE；`DOWNLOADING` → DOWNLOADING（其余 P5 → WORKING）。
- `TAP_AGAIN` → SHY；收藏三事件 → FAVORITE；空结果三事件 → ANOMALY；归来/首启 → RETURN；`LATE_NIGHT` → NIGHT；其余 P4 → TAP；P1/P0 → IDLE。
- `THINKING` / `DOZE` 暂无事件源，仅保留姿态能力。

## 事件映射之外

- 一次性姿态（DONE/ALERT/TAP/SHY）到点自动回 IDLE，气泡按 CueBus holdMs 常驻不受影响。
- 深夜态按真实时钟（23–5 点）在 IDLE 上自动生效，不引入闲置计时器（守"无本地计时器"旧决策）。
- 折叠轨 46px / 展开档 124px 两档渲染。
