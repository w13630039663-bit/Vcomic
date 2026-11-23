# 全仓 MD3 弹窗排查与统一方案

日期：2026-11-09 · 状态：**只读排查完成，未改代码**。
按仓库「调研预算制」：本轮调研已超 4 条只读命令，且改动面跨共享 chrome 与多处判据，按纪律停下汇报，等主人定范围后执行。

## 1. 结论先行

全仓生产源码共 **55 处**弹窗 API / 组合调用，但**桌面运行时真正可达的只有 10 个弹窗实例（桌面专属）+ 2 处共享层调用**。其余分两类：编译面内不可达 5 处、仅 Android 侧 37 处（被 `build.gradle.kts` exclude，桌面编不进）。

**能改掉的边界 = 桌面运行时可达的 10 个实例**；共享层 `SourceEditScreen` 那 2 处同时被 Android 依赖，需单独一轮；37 处 Android-only 不在桌面编译面，改了不影响桌面且会连坐 Android 端，本轮不动。

## 2. 真凶定位

`VeneraDialog` 不在同名文件里，真实定义在
`desktop/app/src/main/java/com/venera/compose/components/venera/VeneraControls.kt:428`。
它属 app 共享层，经 `desktop/build.gradle.kts:118 srcDir(components)` 进桌面编译面，内部按 `useMiuixWidgets` 分两档：

- MIUIX 档 → `WindowDialog`（`:442`）
- MD3 档 → `Md3AlertDialog`（`androidx.compose.material3.AlertDialog` 别名，`:475`）

桌面 `DesktopVeneraTheme` 钉 MD3 ⇒ 恒走 M3 档，这就是用户看到的 Material3 弹窗。

### 关键技术约束（决定改法）

`VeneraControls.kt:416-418` 注释已定性：独立 `androidx.compose.ui.window.Dialog` 窗口里的内容**采样不到宿主窗口的 RenderNode**，跨窗口贴 blur 玻璃只会是空白底板。因此 mac 规范要求的 `blur 22-26` 浮层**不能靠改公共件内部材质实现**，必须走同窗口 `Popup` / 自绘浮层——这会改变弹窗的模态形态（点外部关闭、焦点、Esc 处理需自行接管）。这是需要主人拍板的产品语义变化。

## 3. 全仓清单

### 3.1 弹窗实现（4 类并存）

| 实现 | 位置 | 形态 |
|---|---|---|
| `VeneraDialog`（共享层） | `.../components/venera/VeneraControls.kt:428` | miuix `WindowDialog` / M3 `AlertDialog` 双档 |
| `VeneraModalDialog`（桌面专属） | `.../desktop/ui/DesktopVeneraPrimitives.kt:284` | 自绘 `Box` + 0.65 Scrim + Escape + 卡片；`GalleryTokens.Shape.Large` + 1.dp outline |
| 内联 `Dialog` 自绘（桌面专属） | `DesktopDownloadsPipelineScreen.kt:1385`、`DesktopFavoritesScreen.kt:454`、`DesktopSettingsHub.kt:1397/1453` | `androidx.compose.ui.window.Dialog` + 自绘 r16 / 1.dp border |
| M3 `AlertDialog` / `BasicAlertDialog` 直调 | 共享层 `SourceEditScreen.kt:180` 等 | Material3 原生 |

桌面侧**没有**通用 mac 对话框容器。最接近的现成浮层壳是 `ui/components/DesktopContextMenu.kt:124` 的 `DesktopContextMenuPopup`（`Popup` + r9 玻璃底 + 0.5dp 发丝 + 边缘避让），以及 `ui/DesktopMacControls.kt` 的 `MacRPop = RoundedCornerShape(9.dp)`(:73)、`vShadow`(:288)、`DesktopShelf`(:747)。

### 3.2 桌面运行时可达（10 个实例 + 2 处共享层）

| # | 文件:行 | 当前形态 | 用途 |
|---|---|---|---|
| 1 | `detail/DesktopComicDetailScreen.kt:691` | `VeneraDialog` | 收藏到… |
| 2 | `detail/DesktopComicDetailScreen.kt:831` | `VeneraDialog` | 选择下载章节（另有独立方案稿 `comic-download-dialog-redo`） |
| 3 | `pixiv/DesktopPixivAccountScreen.kt:457` | `VeneraDialog` | 画质调节 |
| 4 | `downloads/DesktopDownloadsPipelineScreen.kt:1385` | 内联 `Dialog` | 已下载章节列表 |
| 5 | `favorites/DesktopFavoritesScreen.kt:454` | 内联 `Dialog` | 新建收藏夹 |
| 6 | `settings/DesktopSettingsHub.kt:1397` | 内联 `Dialog` | SauceNAO API Key |
| 7 | `settings/DesktopSettingsHub.kt:1453` | 内联 `Dialog` | Gelbooru 桌面登录 |
| 8 | `ui/DesktopVeneraPrimitives.kt:284` | `VeneraModalDialog` | 模态删除确认（调用点 `DesktopDownloadsPipelineScreen.kt:757`） |
| 9–10 | 上述组合调用点 `DesktopDownloadsPipelineScreen.kt:728`、`DesktopFavoritesScreen.kt:258` | 自绘组合 | 与上列 4 / 5 为同一实例 |
| S1 | `app/.../feature/sourcemanage/SourceEditScreen.kt:71` | `Dialog` | 源编辑页窗口（**共享层，Android 也用**） |
| S2 | `app/.../feature/sourcemanage/SourceEditScreen.kt:180` | `AlertDialog` | 源编辑保存/确认（同上） |

### 3.3 编译面内但桌面不可达（5 处）

`app/.../feature/ComicDetailScreen.kt:516/1639/1689/2211`（3 处 `VeneraDialog` + 1 处 `AlertDialog`）、`HistoryScreenBody.kt:257`（1 处 `VeneraDialog`）。依据：`VeneraDesktopLargeScreen.kt:4817` 已下掉共享 `ComicDetailScreenBody`、`:5410` 不再用共享 `HistoryScreenBody`，全树真实调用为 0。

### 3.4 仅 Android 侧（37 处）

`SearchScreen.kt`、`LocalComicScreen.kt`、`NetworkFavoritesScreen.kt`、`GalleryAccountCard.kt`、`ComicSourceScreen.kt`、`WebLoginScreen.kt`、`feature/settings/*` 5 个文件、`gallery/ui/*` 5 个文件等，均在 `build.gradle.kts` 的 exclude 清单内。桌面编不进，本轮不动。

### 3.5 测试源码

`VeneraDialog(` / `AlertDialog(` / `Dialog(` 在 `src/test` 里直接调用 **0 处**；判据多为源码字符串扫描与离屏语义树。

## 4. 会连坐的判据（改代码时必须同步）

| 判据 | 钉住什么 |
|---|---|
| `gallery/data/DesktopFavoriteFoldersTest.kt:218-226` | `if (showFavoriteDialog) {` 在详情屏恰 1 处 |
| `architecture/DesktopFavoriteWriteSideTest.kt:106-120` | 标题「收藏到…」与四句档名 |
| `gallery/data/DesktopComicBulkDownloadTest.kt:271-290` | 正则 `VeneraDialog\([\s\S]{0,600}desktopComicBulkToggleAllLabel` —— 全选钮必须住在 content 里 |
| `screens/DesktopComicBulkDownloadUiTest.kt` | 12 项离屏语义树（开/关窗、徽章枚数、合并树） |
| `screens/DesktopFavoriteFoldersUiTest.kt`、`DesktopNewScreensSemanticsTest.kt:113,169` | 弹窗开关与文案 |
| `architecture/DesktopThemeLayerTest.kt:114-180` | 运行时 MD3 后端与 `useMiuixWidgets` 分派 8 处 |
| `DesktopTestTheme.kt:17-20` | 台架钉 `DesktopVeneraTheme(MD3)` + `VeneraDialog` 从 `WindowDialog` 换 `Md3AlertDialog` |
| `app/.../GalleryPixivAccountWiringTest.kt:120` 等 | `confirmDestructive = true` 破坏色（共享层，Android 侧） |

## 5. 单一方案（不做 A/B/C）

**新建一枚桌面专属 `DesktopMacDialog`**（同窗口 `Popup` / 自绘浮层容器），参数形状对齐 `VeneraDialog` 常用入参，逐个替换 §3.2 的 10 个桌面实例：

- 容器：r10 + 0.5dp `controlHair` 发丝 + 顶缘高光 + `sh-pop` 大影（走 `vShadow`，不裸用 `Modifier.shadow`）+ 材质走既有玻璃管道；accent 深 `#0A84FF` / 浅 `#0A63F6`。
- 标题 / 正文 / 按钮按 `controls-unify-2026-11-08` 的控件尺度；破坏性确认保留 `confirmDestructive` 语义。
- 模态语义自行接管：点遮罩关闭、Escape 关闭、焦点环 1.5dp accent 30%。
- **共享层 `VeneraControls.kt` 不动**，Android 端 miuix / MD3 双语义保留；`SourceEditScreen.kt` 两处留独立一轮。
- 复用 `DesktopContextMenuPopup` 的 `Popup` 边缘避让与 `DesktopMacControls` 的 `MacRPop` / `vShadow`，不造第二套玻璃。

### 不做的事

- 不改全局 token、不改壳层、不改 Android 侧 37 处。
- 不把「选择下载章节」弹窗与 `comic-download-dialog-redo` 方案稿混做：那份方案稿待主人确认后独立实施，本轮统一只换容器皮肤。
- 不引入实时 backdrop 采样（项目玻璃批定案）。

## 6. 建议落地顺序（放行后一轮做完）

① 新建 `DesktopMacDialog` + 单测 → ② 替换 3 个 `VeneraDialog` → ③ 替换 5 处自绘 `Dialog` / `VeneraModalDialog` → ④ 同步 §4 判据 → ⑤ `:desktop:compileKotlin` + 点名跑受影响测试类（不跑全量）→ ⑥ commit + 备份。

## 7. 需要主人拍板的两点

1. **范围**：只做桌面可达 10 个实例，还是连 `SourceEditScreen` 两处共享层一起改（后者会连坐 Android）。
2. **模态语义**：接受从「独立窗口 Dialog」改为「同窗口浮层」吗？这是拿到 mac blur 材质的前提，会改变点外部关闭 / 焦点 / Esc 的既有行为。

---

## 8. 范围修订（2026-11-09 11:27 主人令）

主人明令：**「整个项目不用管安卓那边的，这个项目文件夹只管桌面端的」**。据此，前文所有以「Android 连坐 / 共享层被 Android 依赖需保留双语义」为由的保留判断一律作废，重定如下：

- 约束只剩一条：**桌面端编译与运行正确**。`:app` 的 Android 语义不再作为设计约束。
- `VeneraControls.kt` 虽属 app 共享层，但被桌面 `srcDir(components)` 复用，**可以直接改**：把 `VeneraDialog` 的 MD3 档从 `Md3AlertDialog` 换成 mac 风格自绘浮层，一处改动即可让桌面全部 `VeneraDialog` 调用点统一（桌面可达 3 处 + 编译面内 4 处）。Android 档是否随之改变不在考虑范围。
- `SourceEditScreen.kt` 两处桌面可达调用**纳入改造范围**，不再「单独一轮」。
- 37 处 Android-only 仍不纳入：它们被 `build.gradle.kts` exclude，桌面编不进，改了不产生桌面收益。
- 桌面可达的 5 处自绘 `Dialog` / `VeneraModalDialog` 全部纳入，逐个替换为同一 mac 容器。
- 修订后「能改掉的」= **桌面可达 12 处调用 / 10 个弹窗实例，全部统一，无遗留**。

**仍成立的技术约束（与 Android 无关）**：`VeneraControls.kt:416-418` 定性的跨窗口 blur 不可得仍有效。mac 浮层若坚持实时 blur 必须走同窗口 `Popup`；若只做**外观统一**（r10 + 0.5dp 发丝 + sh-pop + accent + 半透明面），可保留 `Dialog` 窗口形态，不改模态语义。**本稿采用后者**：先统一外观，不动点外部关闭 / Esc / 焦点行为，把语义风险降到零。§7 的两个待拍板点由此消解。

### 修订后的落地顺序

① 改 `VeneraControls.kt` 的 MD3 档为 mac 自绘（一处公共件，覆盖全部 `VeneraDialog` 调用点）→ ② 桌面 5 处自绘 `Dialog` / `VeneraModalDialog` 换用同一容器 → ③ `SourceEditScreen` 2 处同样换用 → ④ 同步 §4 判据 → ⑤ `:desktop:compileKotlin` + 点名跑受影响测试类 → ⑥ commit + 备份。

按「最小补丁优先 / 编译驱动」，① 单独成一批先编译验证，②③ 随后一批。**本轮只落 ①**。

---

## 9. 实现校正（① 落地后）

① 已落地：`VeneraControls.kt` 的 MD3 档由 `Md3AlertDialog` 换成自绘 mac 浮层，`:desktop:compileKotlin` 通过。据此校正前文两处描述：

- §5 的「新建桌面专属 `DesktopMacDialog` + 逐个替换」与「共享层 `VeneraControls.kt` 不动」**作废**；实际采用「直接改公共件 MD3 档，一处覆盖全部 `VeneraDialog` 调用点」，②③ 只处理 5 处自绘与 `SourceEditScreen` 2 处。原因是主人令「只管桌面端」后，共享层的 Android 双语义不再是约束，改公共件的改动面远小于逐个替换。
- 第一批容器皮肤实际包含：r10 圆角、0.5dp 发丝描边、顶缘 0.5dp 高光、42% 黑遮罩、色差面；**不含** `sh-pop` 大影与 accent 实底主按钮（项目 Compose Desktop 铁律：低 elevation `Modifier.shadow` 在浅底渲染成硬边灰框，浮起只靠色差 + 发丝描边；按钮沿用既有 `VeneraTextButton` / `confirmDestructive` 语义，避免连坐判据）。这两项属后续批次。
- 模态语义**未改**：仍用独立 `androidx.compose.ui.window.Dialog` 窗口，点外部关闭、Esc、焦点行为不变；遮罩与卡片做成兄弟节点，避免点卡片冒泡触发关闭。
