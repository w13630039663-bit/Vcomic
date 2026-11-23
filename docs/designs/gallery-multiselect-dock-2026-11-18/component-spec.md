# 画廊多选与批量操作组件规格 — 2026-11-18

落地目标文件族：`com.venera.desktop.gallery.ui.selection`，挂在 `VeneraDesktopLargeScreen` 主画廊与搜索结果页。

---

## 1. GalleryMultiSelectDock(底部多选悬浮坞)

### 参数定义
```kotlin
@Composable
internal fun GalleryMultiSelectDock(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onClearSelect: () -> Unit,
    onBatchDownload: () -> Unit,
    onBatchFavorite: () -> Unit,
    onCopyLinks: () -> Unit,
    onOpenMoreModal: () -> Unit,
    modifier: Modifier = Modifier,
)
```

### 交互规格
1. **常态收起**：`selectedCount == 0` 时整组件不渲染或处于离开视口的位移状态。
2. **状态区解构**：
   - 计数 Badge：背景采用 `accent.tint`，蓝色粗体数字。
   - `全选` / `反选`：文字药丸，当 `selectedCount == totalCount` 时「全选」置灰。
   - `清空`：带 `Icons.Default.Close`，点击即触发 `onClearSelect`。
3. **动作区解构**：
   - `批量下载 (N)`：带主色实心胶囊背景，作为主行动点 (Primary Call-to-Action)。
   - `收藏`：带心形图标，若已有部分收藏，支持一键加入或补全。
   - `复制`：带链接图标，点击后 toast 提示「已复制 N 个作品链接到剪贴板」。
   - `更多`：带三点图标，展开二级高级选项或直接拉起详情弹窗。

---

## 2. GalleryBatchActionDialog(多选批量管理弹窗)

### 参数定义
```kotlin
@Composable
internal fun GalleryBatchActionDialog(
    selectedCards: List<DesktopWallCardModel>,
    onDismiss: () -> Unit,
    onRemoveCard: (String) -> Unit, // 从当前选中池剔除单个作品
    onConfirmDownload: (quality: PixivImageQuality, skipExisting: Boolean) -> Unit,
    onConfirmFavorite: (folderName: String?) -> Unit,
    onCopySelectedUrls: (type: LinkType) -> Unit,
)
```

### 内部结构
1. **顶部卡片排 (Selected Card Reel)**：
   - 水平滚动条，列出当前勾选的全部作品缩略图。
   - 每张缩略图悬停时在右上角浮出红色小删除按钮，点击可单项移出选中池。
2. **Tab 分页控制器**：
   - **Tab 1: 批量保存**
     - 下载目录提示（支持点击打开系统目录）。
     - 画质单选组：`原图 (优先最高清)`、`1200大图 (兼顾速度与质量)`、`中画质 (极速保存)`。
     - 勾选项：`自动跳过已存在的图片`、`按作者建子文件夹归类`。
   - **Tab 2: 批量收藏**
     - 目标文件夹下拉选单（默认文件夹、插画、壁纸等）。
     - 新建文件夹文本框。
   - **Tab 3: 提取与共享**
     - 复制类型：`站方作品网页链接`、`高清图片直链`、`汇总 Tags 标签`。
3. **执行控制条**：
   - 取消按钮（文本胶囊）。
   - 确定执行按钮（主色高亮胶囊，内显任务预估项数）。

---

## 3. 调度与生命周期接线

- 数据源单源化：直接消费 `VeneraDesktopLargeScreen` 中的 `wallSelectedUids` 与当前屏已加载的卡片池 `modelByUid`。
- 换源/换页行为：当用户在多选态下切换 Tab 或切站源时，悬浮坞依然驻留，避免误关；用户手动点 `✕` 或操作执行完毕后自动收起并清空。
