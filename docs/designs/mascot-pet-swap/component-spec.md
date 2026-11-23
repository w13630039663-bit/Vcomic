# 薇薇桌宠换芯 · 组件规格与 Compose 映射

## 组件

- 新件 `DesktopMascotPet`（`ui/components/`），`internal`；姿态枚举 `MascotPetPose` 13 态全保留。
- 参数：`pose`（事件映射姿态）、`modifier`、`size`（默认 124dp）。
- 绘制层 `CompositingStrategy.Offscreen`，底部缺口用 `BlendMode.DstOut` 抠穿。

## 状态机（MascotPetState 私有类）

- 帧循环 `withFrameNanos`，每帧推进呼吸相位 → 眨眼/低头排程 → 目标姿态 → 二阶弹簧与阻尼 → 写几何。
- 弹簧四组（缩放/沉浮/右臂/双翼）；身体旋转、左臂、眼形混合、装饰透明度走指数阻尼。
- 一次性姿态回退由组件侧 `LaunchedEffect(pose)` 按 `AUTO_RETURN_SEC`（DONE 2.6s / ALERT 0.9s / TAP 1.2s / SHY 2.2s）执行。
- 首帧即静置形状（bootstrap 基线），无空白帧；周期切换走相位累加器，无收尾跳变。

## 接线（DesktopPetSlot）

- `frame` 状态 → `pose` 状态；`petFrameFor` → `petPoseFor`（DesktopMascotPet.kt 内单源）。
- 渲染：`Image(bitmap)` 与几何占位分支整删，换 `DesktopMascotPet`；`loadPetFrames` 与六张 PNG 引用退役（资源文件不删）。
- 保留不动：台词气泡（竖排分列/右列先读）、badge 状态点、免打扰开关、连戳计数与 `TAP_AGAIN` 触发、错误总线接线。

## 验收对照

- 13 态姿态参数全部照 v3 稿；气泡策略与台词走 `DesktopPetLines`，本批零词库改动。
- 渲染自查三问：首帧无空白（bootstrap）、无收尾跳变（相位累加）、事件切换不重建节点（单组件长期驻留）。
