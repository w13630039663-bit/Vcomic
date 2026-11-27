# 侧栏星穹天气昼夜切换开关与动效重设计 · 方案稿

规格名：`sidebar-theme-switch-2026-11-27` · 日期：2026-11-27  
状态：**方案一定案深化（天气联动版，待主人最终拍板）**。获批后补齐阶段 B 三件套（`design-analysis.md` / `design-tokens.md` / `component-spec.md`），未批前不动业务代码。

依据：用户拍板选用方案一并要求接入所在位置实时天气 / [VeneraLightRailSkin.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt) / [DesktopRailFloatingDock.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/DesktopRailFloatingDock.kt) / [DesktopThemeMode.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/settings/DesktopThemeMode.kt) / [build.gradle.kts](file:///g:/venera-desktop/desktop/desktop/build.gradle.kts)  
交互预览：[ui-mockup.html](file:///g:/venera-desktop/desktop/docs/designs/sidebar-theme-switch-2026-11-27/ui-mockup.html)（含 5 种天气 × 昼夜双态即时切换 + 真实 IP/Open-Meteo 试测）  
配套：`design-analysis.md` / `design-tokens.md` / `component-spec.md`（获批后补）

## 1. 结论

采用**方案一 · 星穹实时天气昼夜舱（Weather-Aware Celestial Horizon）**：在用户卡片底坞上方 `8dp` 放置一块 `224×48dp`（折叠态 `40×40dp`）的微缩天象画布，保留太阳抛物线落日、弯月升起、暮色紫霞与流星扫边的完整昼夜交响，并结合当前所在位置实时天气叠加 **晴 / 多云 / 小雨 / 雷暴 / 飘雪** 5 种气象粒子与天色表现。

| 层 | 目标 |
|---|---|
| 视觉 | 天幕色温、云层厚薄、雨丝涟漪、闪电分叉与雪花薄雪随气象实时演化；支持展开与 `64dp` 折叠双态同源绘制 |
| 交互 | 侧栏一键直切深/浅主题；位置获取走「IP 粗定位 + 手动城市覆盖」双轨制，解决代理漂移痛点；悬停/切换全速动效，失焦/闲置节流降帧 |
| 数据 | 复用现有 `OkHttp` + `kotlinx.serialization` 拉取免 Key 开源气象（Open-Meteo），本地缓存 30 分钟；断网/关闭时无感退回原版晴空/星夜 |

保留全部业务逻辑、数据来源、状态管理与功能入口。

## 2. 位置与天气获取可行性评估（含风险与兜底）

| 维度 | 方案与评估 | 风险与处理 |
|---|---|---|
| 定位方式 | **IP 粗定位（不弹系统定位授权框，兼顾 Windows 与 Linux）** + **设置/右键手动指定城市（Open-Meteo Geocoding）** | 桌面用户常开代理，出口 IP 可能定位到代理节点所在国；提供「手动指定城市」优先级高于 IP，彻底避开代理误判 |
| 气象数据源 | **Open-Meteo**（免费、免注册、无 API Key、返回标准 WMO 天气码与体感温度），经由已有 `OkHttp`【确定 · `desktop/build.gradle.kts:208`】请求 | 启动后后台静默拉取并缓存 30 分钟；超时/无网时静默回退为默认「晴空/星夜」，绝不阻塞启动或弹窗报错 |
| 隐私控制 | 设置中枢外观卡提供「侧栏天气联动：关闭 / 自动(IP) / 指定城市」三选或开关 | 用户明确关掉时不发任何外部定位请求 |
| 空载功耗 | `VeneraLightRailSkin.kt:371` 曾因无限帧动画导致空载单核高占用【确定 · `VeneraLightRailSkin.kt:371`】 | 天气粒子全部在 `Canvas` 内自绘（零布局重组），且设计为：切换主题与鼠标悬停期间 60fps 播放，窗口失焦或长时间静止自动暂停/降帧 |

## 3. 方案明细

### 3.1 视觉层（5 种天象 × 昼夜双态）

- **晴朗（Clear，WMO 0–1）**：白昼蔚蓝晴空 + 旋转光芒炽日 + 底部白云；黑夜深蓝极光夜幕 + 弯月 + 5 颗十字星芒 + 切入时流星划过。
- **多云 / 阴天（Cloudy，WMO 2–3, 45–48）**：白昼柔青灰天幕 + 三层缓慢飘动云海；黑夜低亮度星芒 + 暗灰云团半遮弯月。
- **小雨 / 阵雨（Rain，WMO 51–67, 80–82）**：玻璃窗表面点缀晶莹凝露水珠（零遮挡、极干净），平时静谧或极轻缓呼吸；仅在悬停/切主题时于左半天区轻拂极细渐隐烟雨微丝与底沿小涟漪，绝不穿脸压字。
- **雷暴（Thunderstorm，WMO 95–99）**：厚重压境乌云内部隐现紫蓝蓄电光团（Sheet Lightning），配左半天区偶尔劈落的细锐分叉闪电与极轻斜雨雾，靠光影氛围而非密密麻麻白线表现张力。
- **飘雪（Snow，WMO 71–77, 85–86）**：冰蓝寒天 + 正弦摇曳飘落的景深雪花 + 开关底部 `6dp` 渐隐薄雪层。

### 3.2 空间协同与尺寸

- 展开态：`224×48dp`，圆角 `12dp`，位于 `DesktopRailFloatingDock` 上方 `8dp`。
- 折叠态：`40×40dp`，圆角 `11dp`，位于头像上方。
- 桌宠避让：`MacPetZone` 底部距由 `98.dp` 抬升至 `156.dp`（滚动列表底距同步 `+58.dp`），确保桌宠站在天气开关正上方不重合。

### 3.3 全窗切换大片转场（离屏截帧 + 光波揭幕）

目前切换 `DesktopThemeMode` 时全窗瞬间硬闪变色（`VeneraDesktop.kt:255-263` / `DesktopThemeBridge.kt:64-88`）。若逐组件加颜色过渡会引起整树重组掉帧；采用 Compose Desktop 原生 `rememberGraphicsLayer()` 离屏截帧方案可做到零卡顿 60fps：
1. 点击开关换肤前，瞬时抓取旧主题窗口 GPU 纹理快照盖在最顶层，底层同时悄悄切换为新主题；
2. 顶层快照执行 `520~680ms` 揭幕动画（HTML 预览页提供 3 种可试玩特效）：
   - **终极星爆冲击波（主推）**：点击瞬间微缩蓄力，随即从侧栏开关中心炸出双层色散极光波纹（主波 + 延迟半拍回声波）及 36 颗拖尾星芒粒子向全窗四散飞溅！
   - **次元极光裂隙**：中央斜向撕开高能极光裂缝并向全窗两侧高速推开揭幕，边缘伴随炫光刀锋；
   - **螺旋星轨光阑**：360° 旋转星轨光幕横扫全窗完成昼夜吞噬更替。
3. **边界说明**：若开启 Windows 底层 DWM 透明窗底，系统底板色切换略快半拍，窗内全组件仍由快照层丝滑接管；实底模式为 100% 无缝全屏冲击波。

### 3.4 数据与状态层

- 主题流仍复用 `DesktopThemeMode.mode` 与 `pref_desktop_theme_mode`。
- 新增轻量级桌面天气状态单例 `DesktopRailWeatherState`（内存 `StateFlow` + `JsonKeyValueStore` 30 分钟时间戳缓存：城市名、经纬度、WMO 天气码、温度、刷新时间）。

## 4. 验收口径

- 获批后先补全阶段 B 三件套（`design-analysis.md` / `design-tokens.md` / `component-spec.md`），再进行阶段 C 落码。
- 代码落点：`DesktopRailThemeSwitch.kt`（UI 绘制）、`DesktopRailWeatherService.kt`（IP/城市/Open-Meteo 缓存与查询）、`VeneraLightRailSkin.kt`（挂载与桌宠避让）、`DesktopSettingsCardsGallery.kt`（定位方式/城市/开关配置）。
- 验证标准：`:desktop:compileKotlin`、新增天气映射单测通过，实测无网降级与空载 CPU 占用正常后交付真窗截图验收。

## 5. 实施记录

- 2026-11-27 已完成三件套归档与阶段 C 落码（待主人真窗运行截图确认效果后统一更新 `docs/CHANGELOG.md` 并提交）：
  - 新增 `DesktopRailWeatherService.kt`：实现 IP 粗定位 / 手动城市查询、Open-Meteo 实时天气拉取与 30 分钟本地缓存、WMO 5 档天气映射，以及全窗切换特效偏好 `DesktopThemeRevealPrefs`。
  - 新增 `DesktopThemeRevealHost.kt`：实现 `rememberGraphicsLayer().toImageBitmap()` 全窗离屏截帧 + `BlendMode.DstOut` 羽化擦除，支持「星穹冲击波 / 次元裂隙 / 螺旋光阑 / 无」4 档，并在 `DesktopLargeScreenChrome.kt` 根层挂载。
  - 新增 `DesktopRailThemeSwitch.kt` 与 `DesktopRailThemeSwitchCanvas.kt`：实现位于侧栏用户底坞上方的星穹实时天气昼夜舱（展开态 `224×48dp`，折叠态 `40×40dp` `10dp` 圆角微章），含运动残影太阳、弯月星芒、玻璃凝露水珠、悬停微雨丝与雷暴云闪；常态静止以保空载低占用。
  - 更新 `VeneraLightRailSkin.kt`、`DesktopThemeMode.kt`、`DesktopStartupBootstrap.kt`；将 `buildAppearanceSettingsCards` 拆出至 `DesktopAppearanceSettingsCard.kt`（瘦身 `DesktopSettingsCardsGallery.kt`）并补齐「全窗切换动效 / 侧栏天气联动 / 指定城市 / 立即刷新」设置行。
  - 新增 `DesktopRailWeatherPolicyTest.kt` 定向测试。
