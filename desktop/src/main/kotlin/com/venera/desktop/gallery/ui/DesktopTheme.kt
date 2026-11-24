package com.venera.desktop.gallery.ui

import com.venera.desktop.app.VeneraDesktopLargeScreen
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.composefluent.darkColors

/**
 * 桌面首页的**深色主题与色板唯一出处** —— surface 阶梯与文字色在这里定死，其余文件一律不写死色值。
 *
 * ## 为什么强制深色
 *
 * `:desktop` 之前由 `FluentTheme { … }` 自己跟系统，于是**浅色档**下画出来的界面与设计稿
 * `docs/designs/windows-gallery-home-touhou-2026-10-03.html` 那套深色阶梯毫无关系 ——
 * 而 `DesktopUnimplementedRow` / `DesktopGalleryHomeSections` 里那几枚色值（`#8B8B8B` 灰、
 * `#CBB6FF` 藤紫、`#3A2A55→#2C2C2C` 渐变）**全部是稿的深色档**。浅底上那几枚就是"看起来发灰的脏色"。
 * 所以本轮把深浅**钉死在深色**：[darkColors] 交给 Fluent，其余 surface 走稿的阶梯。
 *
 * ⚠️ 代价要写明：桌面端**今天没有主题切换**。稿上的两档浅色映射、以及
 * `DesktopGalleryPreferences` 里那 12 枚读成员（`galleryAnimated` 等）在桌面给的都是常量，
 * 主题轴还没接 —— 这是"深色固定"能成立的前提。将来要接主题轴时，
 * 本文件要变成两套色板而不是删掉深色这套。
 *
 * ## 阶梯的语义（不是随手排的几档）
 *
 * ⚠️ 行号锚定仓库内那份权威稿 `docs/designs/windows-gallery-home-touhou-2026-10-03.html`
 * （32 版）。稿改版后行号会漂 —— 引用时连带抄上选择器名，
 * 行号对不上就按名字 grep。已把早先按旧稿写的行号全部对账过一次。
 *
 * | 档 | 值 | 用在哪 | 为什么在这一档 |
 * |---|---|---|---|
 * | 窗底 | `#202020` | `windowBackground` | 稿 `:10` 的 `--host`，整扇窗的最底层 |
 * | rail | `#1C1C1C` | `rail` | 稿 `:59` 的 `.rail{background:#1C1C1C}`，**比窗底与 pane 都更暗** |
 * | 侧栏 | `#272727` | pane | 稿 `:10` 的 `--layer`，比窗底高一档 |
 * | 卡片 | `#2C2C2C` | Hero 侧卡、作品卡 | 稿 `:10` 的 `--card`（与 `--layer2` 同值），比侧栏再高一档 |
 * | 浮起 | `#353535` | 选中行 hover、输入框、**作品卡描边** | 稿 `:11` 的 `--stroke` |
 *
 * ⚠️ 浮起档**不是**稿的 `--card-hov`：那一枚是 `#333333`（稿 `:10`），比 `--stroke` 暗一档。
 * 这里的注释把它俩写成"同一颗值"，是错的 —— 本条按实稿改正，
 * 而代码取的是 `#353535`（`--stroke`），与作品卡描边的出处一致。
 *
 * ## 为什么 rail 单独一档
 *
 * 这一档原先**不存在**，当时 rail 与 pane 共用 `SidebarBackground`，而
 * `DesktopGalleryHome` 的注释却写着「底色刻意比 pane 更深一档」—— 注释与代码互相矛盾，
 * 结果两级导航在深色下糊成同一片，只靠一条 1px 线撑着（深色里几乎看不见）。
 *
 * 现在照稿执行：`.rail{background:#1C1C1C}`（稿 `:59`）确实比 `.pane{background:var(--layer)}`
 * 更暗，而**这枚明度差就是两级导航的层级差本身** —— rail 说"我在哪个域"、pane 说"我在这个域的哪一页"。
 * 删掉它两级会塌成一档，所以它不是"随手加的中间灰"，是承重值。
 * 由 `DesktopCardOverlayTest` 逐字钉死 + 一条「rail 与 pane 必须不同档」的断言守住。
 *
 * ⚠️ **五档之间不许再出现第六档"差不多"的色**：本仓记过的那类事故就是"随手加一个中间灰"，
 * 屏幕上一眼看不出差别、久了只觉得脏。五档是上限。
 */
internal object DesktopTheme {

    // ── surface 阶梯（逐字抄稿的 CSS 变量，:10-11）────────────────────────────

    /** 窗底。稿 `--host` = `#202020`。 */
    val WindowBackground: Color = Color(0xFF202020)

    /**
     * rail 专用底色。稿 `.rail{background:#1C1C1C}`（59`）。
     *
     * ⚠️ 它是**五档阶梯里的第二档**，不是"随手加的中间灰"：比窗底 `#202020` 与 pane 的
     * `#272727` 都更暗，而**这枚明度差就是两级导航的层级差本身**（rail = 我在哪个域，
     * pane = 我在这个域的哪一页）。rail 与 pane 共用 [SidebarBackground]，
     * 两级在深色下糊成一片 —— 由 `DesktopCardOverlayTest` 的「rail 与 pane 必须不同档」守住。
     */
    val RailBackground: Color = Color(0xFF1C1C1C)

    /** 侧栏（pane）。稿 `--layer` = `#272727`。 */
    val SidebarBackground: Color = Color(0xFF272727)

    /** 卡片。稿 `--card` = `#2C2C2C`。 */
    val CardBackground: Color = Color(0xFF2C2C2C)

    /** 交互态浮起。稿 `:11` 的 `--stroke` = `#353535`（**不是** `--card-hov`，那枚是 `#333333`）。只在 hover / 聚焦时出现。 */
    val SurfaceRaised: Color = Color(0xFF353535)

    // ── 文字（稿 `:11` 的 --t1..--t3，本次按用户口径收成两档）───────────────

    /** 主文字。`#FFFFFF`。 */
    val TextPrimary: Color = Color(0xFFFFFFFF)

    /** 次要文字。`#A0A0A0`（用户口径；稿那枚 `--t3` 是 `#8B8B8B`，更暗一档，本轮统一提上来）。 */
    val TextSecondary: Color = Color(0xFFA0A0A0)

    /** 最弱一档：未实现行、caption。稿 `--t3` = `#8B8B8B`。 */
    val TextTertiary: Color = Color(0xFF8B8B8B)

    // ── 强调色（稿 `:13-16` 的 --beni / --fuji 两族）───────────────────────

    /** 强调文字 / 指示条 / focus。正名批藤紫退役改品牌蓝浅档：深色底对比达标，且与强调色同族。 */
    val AccentFuji: Color = Color(0xFF8FB4FF)

    /**
     * **填充型**强调那一档（主按钮/主胶囊那种"整块涂色"的底）。设计稿原 `--accent-fill` = `#5B47A8`，
     * 正名批藤紫退役后取品牌蓝 `#2563EB`
     * （20`，与 `--accent`（=`--fuji-lite`）**是两枚**：
     * 稿里 `--accent` 管文字与指示条，`--accent-fill` 管底）。
     *
     * 为什么桌面从前没有这一档而批 95 要添：桌面第一枚"整块涂色"的控件是详情那一屏那枚主胶囊（批 94），
     * 而它本来挂的是 compose-fluent 的 `AccentButton` —— 那颗控件的填充读的是 `Colors.fillAccent`，
     * 而 `darkColors(accent = AccentFuji)` 那一步**吃不到我们递进去的色**（库里那张预置表按色查表，
     * 不在表里就整条回落到默认那套蓝；四条判据见 `DesktopAccentFillTest`，包内图证抓到的是天蓝）。
     * 换色也解决不了 —— 那张表**只有一颗键**（`#0078D4`）而回落值恰好就是它自己那份（③ 现数），
     * 递进六枚候选量到的全是同一枚 `#60CCFE`（④）⇒ 任何 accent 都动不了那一格，只能不经过那条链 ⇒ 这一档由我们自己出。
     *
     * ⚠️ 它**不是** surface 阶梯里的第六档灰：色相明确（品牌蓝，比 [AccentFuji] 深），只许出现在"填充型强调"那一形上。
     */
    val AccentFill: Color = Color(0xFF2563EB)

    /** 博丽朱：神社档装饰与警示。稿 `--beni` = `#D6463C`。 */
    val AccentBeni: Color = Color(0xFFD6463C)

    // ── Hero 插画的渐变色（deco 神社档那一支）──────────────────────────────

    /**
     * Hero 插画渐变的**深**那一端 —— 藤紫标准档。稿 `--fuji` = `#8A6BC4`
     * （15`）。
     *
     * 补回：`DesktopShrineDecor.desktopDecoHeroArtColors` 用
     * `listOf(DecoHeroArtDeep, DecoHeroArtFuji, AccentBeni)` 做出"深藤紫 → 浅藤紫 → 博丽朱"
     * 那一道渐变。这两枚原先在 deco 那批改动里，`git checkout` 时丢了；
     * 稿值在 `:15`（`--fuji`）与 `:16`（`--fuji-lite`）逐字可查，按它们补。
     *
     * ⚠️ 与 [AccentFuji]（= `--fuji-lite` = `#CBB6FF`）是**两枚**：那一枚管文字与指示条，
     * 这一枚管插画的深端。合成一枚会让渐变退化成单色，而稿上那一段正是要有层次。
     */
    val DecoHeroArtDeep: Color = Color(0xFF1E3A8A)

    /**
     * Hero 插画渐变的**浅**那一端 —— 藤紫浅档，与 [AccentFuji] **同值**（`--fuji-lite`）。
     *
     * 为什么另起一个名字而不是直接写 `AccentFuji`：这两个数今天相等，但**语义不同** ——
     * 前者是"插画渐变的浅端"，后者是"强调色文字/指示条"。共用一枚会让"哪天设计师
     * 只调插画浅端"这件事必须改强调色才能做到，那是一次误伤。名字分开，值放一起对得上就好。
     */
    val DecoHeroArtFuji: Color = AccentFuji

    // ── 标签 chip 的 beni 强调档（稿 `:139` 的 `.chip.beni`）──────────────────

    /**
     * beni 强调 chip 的底色。稿 `.chip.beni{background:#4A2220}`（139`）。
     *
     * ⚠️ 这两枚是 **beni 色族**（与既有 [AccentBeni] 同族），**不是 surface 阶梯里的一档灰** ——
     * 「五档之间不许再出现第六档差不多灰」那条禁的是**中性灰**（那种只差几个色号、
     * 肉眼分不出区别的相邻灰阶），色相明确的强调色不在此列；但它们同样只许在这一处出现，
     * 前缀也已登记进色板扫描器（`DesktopSurfaceLiterals` 的前缀表）。
     */
    val ChipBeniBackground: Color = Color(0xFF4A2220)

    /** beni 强调 chip 的文字。稿 `.chip.beni{color:#FFB4A8}`（`:139`）。 */
    val ChipBeniContent: Color = Color(0xFFFFB4A8)

    /**
     * 选中行的横向渐变（稿 `.nav.sel`：`linear-gradient(90deg,#3A2A55,#2C2C2C 78%)`）。
     *
     * 起点的 `#3A2A55` 是藤紫压进卡片档的混色，**不是**新色 —— 它比 [AccentFuji] 暗、比
     * [CardBackground] 亮一档，所以它在两档之间而不是自成一档阶梯。
     */
    val SelectionGradientStart: Color = Color(0xFF3A2A55)
    val SelectionGradientEnd: Color = Color(0xFF2C2C2C)

    /**
     * 作品卡底部遮罩的终点：用户口径 `#80000000`（半透明黑）。
     *
     * 为什么是半透明而不是实色：图本身明暗差别极大（白底作品贴实色会糊掉画），
     * 半透明黑让文字在任何底图上都够对比 —— 这一条由
     * `DesktopCardOverlayTest` 按"终点带 alpha"钉住，改成不透明即红。
     */
    val CardOverlayEnd: Color = Color(0x80000000)

    /**
     * 压在图上的**小胶囊底**（作品卡的站点标、hero 的「重排」钮）：`#B0202020`。
     *
     * 它是窗底 `#202020` 的**半透明变体**而不是新的一档 surface —— 与 [CardOverlayEnd] 同理由，
     * 实色会糊掉图面。收进主题是因为它原先在两个文件里各写一遍 `Color(0xB0202020)` 字面量，
     * 而旧版 `DesktopSurfaceLiterals` 的前缀表只认 `0xFF` ⇒ 这两处从来没被「色板唯一出处」扫到。
     */
    val OverlayScrim: Color = Color(0xB0202020)

    /**
     * 「未实现」行的**图标**档：`#5E5E5E`，稿 `.nav.off svg`（`:86-87`）。
     *
     * 比同行的文字档 [TextTertiary]（`#8B8B8B`）再暗一档 —— 图标是次要信号，
     * 不该和标题抢注意力。收进主题的理由同上（原先在 `DesktopUnimplementedRow.kt` 里写字面量）。
     */
    val OffGlyph: Color = Color(0xFF5E5E5E)

    /** 图位底色：图还没到 / 取不到时那块不是纯黑也不是纯白，而是卡片档压深一点。 */
    val ImagePlaceholder: Color = Color(0xFF1F1F1F)

    /** 图位里的字（"没给缩略档"这类），压在 [ImagePlaceholder] 上。 */
    val OnImagePlaceholder: Color = Color(0xFF8B8B8B)

    /**
     * 给 `FluentTheme` 用的深色配色：accent 走藤紫。
     *
     * 为什么 accent 取藤紫而不是博丽朱：博丽朱是神社档的装饰色，选中态那种"当前位置"的
     * 语义对不上；稿上 `.nav.sel` 的指示条本来就是 `--fuji-lite`。
     */
    fun colors() = darkColors(accent = AccentFuji)
}

// 侧栏与命令带双档色板：深色复用五档阶梯，浅色对齐浅皮 Token；色值字面量集中于本文件以通过静态检查。

internal data class DesktopChromePalette(
    /** 当前档位。玻璃底衬、hero 渐变这类「只有一档才有意义」的装饰靠它分流，不另开状态源。 */
    val dark: Boolean,
    val page: Color,
    /**
     * 玻璃 tint。**半透中性色**，别改成实色、也别加色相：Acrylic 档叠在实时系统材质上，
     * 降级档叠在 haze 糊过的壁纸副本上，两种档都只靠它调明暗。
     * 演进：v4.2 想透壁纸但 haze 糊不了 OS 壁纸 ⇒「成透明了」；v4.3 窗根实底 +
     * `HazeInput.Sources` 真糊；v4.4 被模糊的内容换成真实壁纸副本、撤掉全部彩色斑；
     * v4.5 主路径升级为实时 Acrylic，本 tint 两档共用。浅 0.38 白 / 深 0.62 深。
     */
    val railGlass: Color,
    /**
     * 玻璃顶部高光渐变起点（中性白，给平涂的半透层一点厚度）。
     * v4.4：原先的 blobA/B/C 三枚彩色氛围斑已删 —— 主人明确「不要渐变色，只要背景模糊」，
     * 被模糊的内容改为真实壁纸副本，色板里不再保留任何造色相的字段。
     */
    val glassSheen: Color,
    /** haze 模糊层的叠色：浅档提亮、深档压暗，别让深色玻璃被白 tint 洗灰。 */
    val glassHazeTint: Color,
    val railLine: Color,
    val card: Color,
    val raised: Color,
    val pill: Color,
    val search: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val chrome: Color,
    val accent: Color,
    val danger: Color,
    val hairline: Color,
    val frame: Color,
    val hover: Color,
    val brandStart: Color,
    val brandEnd: Color,
    /** 首页 Hero 渐变两端（与品牌渐变**不同**：Hero 是深青/紫的大面积底图）。 */
    val heroStart: Color,
    val heroEnd: Color,
    /** 源状态「正常 / 告警」两枚状态色，两档共用同值。 */
    val ok: Color,
    val warn: Color,
    // ── macOS 皮三枚（macos-style-稿 · 全站换皮）──
    /** 分段控件轨道：14% 中性灰，白滑块浮在其上（全站唯一选中轨口径）。 */
    val segmentTrack: Color,
    /** 白卡架底（hero 白卡架 / 浮层材质）：92% 白，叠微影。 */
    val elevated: Color,
    /** 封面暗玻璃徽章底（55% 近黑，白字恒可读）。 */
    val glassDark: Color,
)

/**
 * 三块玻璃（内容区 / 侧栏 / 顶栏）的唯一取值点。
 *
 * **只保留一个函数、一个值**：曾给侧栏开过 rail() 特例（不同基色 + 不同 alpha），
 * 结果就是三块深浅不一、看着割裂。要调亮/调透就改这里，三处一起变。
 * 深色基色用 raised（比 page 亮一档）而非 page，整体不至于沉成黑板。
 */
internal object DesktopGlass {
    fun surface(p: DesktopChromePalette): Color =
        (if (p.dark) p.raised else p.page).copy(alpha = if (p.dark) 0.20f else 0.72f)
}

internal object DesktopChromeColors {
    /**
     * 根据明暗与纯黑偏好返回对应色板。
     * @param dark 是否为深色模式
     * @param pureBlack 深色模式下是否开启纯黑
     */
    fun of(dark: Boolean, pureBlack: Boolean = false): DesktopChromePalette {
        if (!dark) return Light
        return if (pureBlack) PureBlack else Dark
    }

    /** 浅色档：LightRailTokens / HomeLightTokens 原值。 */
    val Light = DesktopChromePalette(
        dark = false,
        // 浅色层次：底色压深一档，白卡才浮得起来（原 #F6F7F9 与纯白只差 9 级明度，卡片糊进底里）。
        page = Color(0xFFEAEDF1),
        // 浅色侧栏优化：0.38 的玻璃压在彩色壁纸上会透成"灰褐雾"，文字跟着发灰。
        // 抬到 0.74（模糊实现不变，只调 tint 浓度）—— 浅色档要读成一块干净的浅面板。
        railGlass = Color(0xFFFFFFFF).copy(alpha = 0.74f),
        glassSheen = Color(0xFFFFFFFF).copy(alpha = 0.20f),
        glassHazeTint = Color(0xFFFFFFFF).copy(alpha = 0.30f),
        railLine = Color(0xFF1A1D21).copy(alpha = 0.09f),
        card = Color(0xFFFFFFFF),
        raised = Color(0xFFFFFFFF),
        pill = Color(0xFFE4E9EF),
        search = Color(0xFFE0E6EC),
        ink1 = Color(0xFF1A1D21),
        // 浅色文字加深：ink3 原 #9AA3AD 在浅底上 11-12sp 看着"发虚"（对比不足，
        // 不是字体糊）。压到 #6F7883；ink2 同步略深一档。
        ink2 = Color(0xFF4E5761),
        ink3 = Color(0xFF6F7883),
        chrome = Color(0xFF3D454D),
        accent = Color(0xFF0A63F6),
        danger = Color(0xFFE81123),
        hairline = Color(0xFF1A1D21).copy(alpha = 0.06f),
        frame = Color(0xFF1A1D21).copy(alpha = 0.10f),
        hover = Color(0xFF1A1D21).copy(alpha = 0.05f),
        brandStart = Color(0xFF3B82F6),
        brandEnd = Color(0xFF1D4ED8),
        heroStart = Color(0xFF37666C),
        heroEnd = Color(0xFF22303B),
        ok = Color(0xFF16A34A),
        warn = Color(0xFFD97706),
        segmentTrack = Color(0xFF787880).copy(alpha = 0.14f),
        elevated = Color(0xFFFFFFFF).copy(alpha = 0.92f),
        glassDark = Color(0xFF1C1C20).copy(alpha = 0.55f),
    )

    /** 深色档：只用上面五档阶梯 + 文字/强调既有值，不造新灰。 */
    val Dark = DesktopChromePalette(
        dark = true,
        page = DesktopTheme.WindowBackground,
        railGlass = DesktopTheme.RailBackground.copy(alpha = 0.62f),
        // v4.4：深色档同样糊壁纸，只是 tint 换成深色；不再铺实底 return，也不画彩色斑。
        glassSheen = Color(0xFFFFFFFF).copy(alpha = 0.06f),
        glassHazeTint = Color(0xFF000000).copy(alpha = 0.18f),
        railLine = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        card = DesktopTheme.CardBackground,
        raised = DesktopTheme.SurfaceRaised,
        pill = DesktopTheme.SidebarBackground,
        search = DesktopTheme.SidebarBackground,
        ink1 = DesktopTheme.TextPrimary,
        ink2 = DesktopTheme.TextSecondary,
        ink3 = DesktopTheme.TextTertiary,
        chrome = DesktopTheme.TextSecondary,
        accent = Color(0xFF8FB4FF),
        danger = DesktopTheme.AccentBeni,
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        frame = Color(0xFFFFFFFF).copy(alpha = 0.12f),
        hover = Color(0xFFFFFFFF).copy(alpha = 0.06f),
        brandStart = Color(0xFF3B82F6),
        brandEnd = Color(0xFF1D4ED8),
        heroStart = Color(0xFF1E3A8A),
        heroEnd = DesktopTheme.WindowBackground,
        ok = Color(0xFF16A34A),
        warn = Color(0xFFD97706),
        segmentTrack = Color(0xFFFFFFFF).copy(alpha = 0.14f),
        elevated = DesktopTheme.SurfaceRaised,
        glassDark = Color(0xFF000000).copy(alpha = 0.62f),
    )

    /** 纯黑档：针对 OLED 屏幕将窗底与主容器降为纯黑。 */
    val PureBlack = DesktopChromePalette(
        dark = true,
        page = Color.Black,
        railGlass = Color.Black.copy(alpha = 0.85f),
        glassSheen = Color(0xFFFFFFFF).copy(alpha = 0.04f),
        glassHazeTint = Color(0xFF000000).copy(alpha = 0.30f),
        railLine = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        card = Color(0xFF121212),
        raised = Color(0xFF1E1E1E),
        pill = Color(0xFF161616),
        search = Color(0xFF161616),
        ink1 = DesktopTheme.TextPrimary,
        ink2 = DesktopTheme.TextSecondary,
        ink3 = DesktopTheme.TextTertiary,
        chrome = DesktopTheme.TextSecondary,
        accent = Color(0xFF8FB4FF),
        danger = DesktopTheme.AccentBeni,
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        frame = Color(0xFFFFFFFF).copy(alpha = 0.12f),
        hover = Color(0xFFFFFFFF).copy(alpha = 0.06f),
        brandStart = Color(0xFF3B82F6),
        brandEnd = Color.Black,
        heroStart = Color(0xFF1C1C1C),
        heroEnd = Color.Black,
        ok = Color(0xFF16A34A),
        warn = Color(0xFFD97706),
        segmentTrack = Color(0xFFFFFFFF).copy(alpha = 0.14f),
        elevated = Color(0xFF1E1E1E),
        glassDark = Color.Black.copy(alpha = 0.85f),
    )

    /** 桌面端白色 surface 唯一出处：浅皮批原先在两个文件里写死 0xFFFFFFFF，统一收这里。 */
    val SurfaceWhite: Color = Color(0xFFFFFFFF)
}

/**
 * v4 命令带 / 玻璃轨 / 浅色首页**共用的**档位色板。
 *
 * 由 `VeneraDesktopLargeScreen` 窗根那层 `CompositionLocalProvider` 一次注入，
 * 值只由 `DesktopThemeMode.mode` 这一条流换算 —— 轨、内容头、首页屏读到的是同一颗，
 * 不会出现「轨已经深色、内容头还浅色」的半窗漂移。
 * 默认浅色：安卓 / 预览 / 单元测试不注入时行为确定。
 */
internal val LocalChromePalette = staticCompositionLocalOf { DesktopChromeColors.Light }
