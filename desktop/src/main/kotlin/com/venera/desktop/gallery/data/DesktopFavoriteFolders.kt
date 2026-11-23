package com.venera.desktop.gallery.data

import com.venera.compose.data.db.FavoriteRecord

/**
 * 收藏**文件夹**那一格（对岸 `ComicDetailScreen.kt:2030-2066` 那一排 `FavRow`）。
 *
 * ## 缺口长什么样
 *
 * `comic_favorite.folder_name` 那一列从桌面接收藏那天起就**恒走默认那枚**：
 * `FavoriteRecord.folderName` 的默认值是 `"默认"`，而详情那一屏那枚钮写死的形参里根本没提它 ⇒
 * 桌面上任何一本收藏都落在同一格里。那意味着三件事同时不成立：
 *  - 用户没法把一本收到别一夹（**选择**没接）；
 *  - 收藏那一屏看不出某一本在哪一夹（**显示**没接，屏上那两行是题名与源名）；
 *  - 对岸那颗 `getFolders()`（`FavoriteDao.kt:117`）在桌面**零调用点** ⇒ 那一排名字没有出处。
 *
 * ## 一处必须写明而**不许照抄对岸**的形状差
 *
 * 对岸一个收藏夹是**一张表**（`LocalFavoritesManager`「每夹一表」），所以同一本能同时
 * 躺在好几个夹里，那一排 `FavRow` 是**逐个开关**（在里就移出、不在里就加入）。
 * 桌面这一路写的却是共享那张 `comic_favorite`，而它 v2 的主键是 `(comic_id, source_name)`
 * （`SchemaSql.kt`）⇒ **一本在桌面上只能占一格**。所以这里那一排是**单选**而不是逐枚开关，
 * 而"改选另一夹"的实际语义是**移到**而不是"也收进"。
 * 措辞跟着语义走（[desktopFavoriteFolderActionLabel]），不拿对岸那句「加入/已加入」糊过去 ——
 * 摆一句"也收进这一夹"而实际把人原来那一夹挪空，是假话。
 *
 * ## 为什么名字的出处是"那张表"而不是 `getFolders()`
 *
 * 壳里已经读回整张收藏表（[com.venera.desktop.VeneraDesktopLargeScreen] 那颗 `favorites`），
 * 而 `getFolders()` 要再发一次 `SELECT DISTINCT` ⇒ 两轨读同一件事一定漂（同一时刻屏上
 * 那一排与那一本显示所在的那一格可能对不上）。这里从**已经读回来的那一份**现算，
 * 零额外 IO，也符合"屏只搬、算在外面"那条既有纪律。
 * 代价一并写明：拿**已读回的那一批**算出来的名字，天然只包含"真被用过的夹"，
 * 对岸那句 `SELECT DISTINCT` 也是同一条口径（它同样只列在用的），所以两端的集合语义一致。
 */

/** 那张表里永远有的第一格（逐字照对岸 `FavoriteDao.kt:118` 与 `FavoriteRecord.folderName` 的默认值）。 */
internal const val DESKTOP_FAVORITE_DEFAULT_FOLDER: String = "默认"

/**
 * 那一排在用夹的名字：默认恒在首位，其余**按名升序去重**（与对岸 `getFolders()` 那句
 * `SELECT DISTINCT folder_name … ORDER BY folder_name ASC` 同一条排序与去重口径）。
 *
 * 为什么空表也要交回 `[默认]` 而不回空表：那一排是**选择器**，空表意味着"还没有别的夹"，
 * 而不是"没有地方可以收"。回空表会让那一格整个消失，用户连"收到默认这一夹"都看不见。
 */
internal fun desktopFavoriteFolderNames(favorites: List<FavoriteRecord>): List<String> {
    val others = favorites.map { it.folderName }
        .filter { it.isNotBlank() && it != DESKTOP_FAVORITE_DEFAULT_FOLDER }
        .distinct()
        .sorted()
    return listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER) + others
}

/**
 * 那一排当下选中哪一格。三档按优先级：
 *  1. 用户这一趟挑过的（`picked`）；
 *  2. 那一本**已经收在**的那一格（`currentFolder`，从那张表现算）—— 没挑过而它已经在
 *     「追更」里时，那一排若把光标高亮在「默认」，按下去"取消收藏"再按"收藏"就悄悄把它移走了；
 *  3. 默认那一格。
 *
 * ⚠️ 第 2 档**不许**折成"总是默认"：那是这一格里最容易写错的一笔（看起来只是光标位置，
 * 实际决定下一次写入落在哪一格）。
 */
internal fun desktopFavoriteFolderChoice(picked: String?, currentFolder: String?, names: List<String>): String {
    val wanted = picked ?: currentFolder ?: DESKTOP_FAVORITE_DEFAULT_FOLDER
    // 挑过的那一格后来被清空（那一夹里最后一本被移出）时不许选中一个不存在的名字，
    // 也不许顺手猜别的夹 —— 回默认那一格才是实话。
    return if (wanted in names) wanted else DESKTOP_FAVORITE_DEFAULT_FOLDER
}

/**
 * 那一本现在收在哪一夹；`null` = **那张表里没有它的有效夹名**，而这一档有两种成因：
 *  - 那张表**还没读回来**（`loaded == false`）；
 *  - 表读回来了而这一本确实没收过。
 *
 * 分开那两种成因的是 `favorited` 那枚三态布尔（来自即时查库 `isFavoriteOf`），不是这颗件 ——
 * 那颗件只看那张表，而表里"没有这一行"与"表还没到货"交回的东西本来就该一样：
 * **都不许猜出一格来**。猜出来的那一格会被用户读成"我收在那儿了"，而那一句正是
 * [desktopFavoriteFolderActionLabel] 第一档要念出来的实话。
 */
internal fun desktopFavoriteFolderOf(
    comicId: String,
    favorites: List<FavoriteRecord>,
    loaded: Boolean,
): String? {
    if (!loaded) return null
    return desktopFavoriteRowOf(comicId, favorites)?.folderName
}

/**
 * 那一本在**已读回的那张表**里的那一行；`null` = 那张表里没有这一本（或还没读回来时由调用方自己判）。
 *
 * 与 [desktopFavoriteFolderOf] 同一颗出处（那一格就是这里取出来的 `folderName`）—— 两处各查一遍
 * 就是"哪一行算这一本"能漂成两种的那一族。
 */
internal fun desktopFavoriteRowOf(
    comicId: String,
    favorites: List<FavoriteRecord>,
): FavoriteRecord? = favorites.firstOrNull { it.comicId == comicId }

/**
 * 按下那一枚把手**真写回库**的那一条记录：只换 `folderName`，其余七格照原样留着。
 *
 * ## 为什么不许整条重拼（这一格是最容易写错而一条都不红的）
 *
 * 那颗 DAO 走的是 `INSERT OR REPLACE`（`FavoriteDao.kt:138`，主键 `(comic_id, source_name)`），
 * 它是**整行替换**而不是 UPDATE 那一列。拿详情现拼的那一份写回去，就会连带把
 * `tags` / `has_update` / `latest_chapter` 打成默认值，并把 `created_at` 换成**现在**——
 * 而收藏屏那一列是按 `created_at DESC` 排的：只是换个夹，那一本却跳到顶端，
 * 手机上攒的标签和追更标记也一并没了。那是"改一格而搬掉别的格"，比不改更坏。
 *
 * `existing == null`（这一本还没收过）才用装配点那份新拼的 —— 那种情形本来就没有旧格子可保。
 */
internal fun desktopFavoriteRecordToWrite(
    existing: FavoriteRecord?,
    fresh: FavoriteRecord,
    folderName: String,
): FavoriteRecord = (existing ?: fresh).copy(folderName = folderName)

/**
 * 那一排下面那一句"按下会落到哪"（**成品文案**，屏只搬）。
 *
 * 三种情形三种话，差别全在"要不要说**移到**"：
 *  - 那一本还没收过 ⇒ 「这一本会落到 X」；
 *  - 已收而正是选中的那一格 ⇒ 「X 正是这一本现在那一格」；
 * - 已收而收在**别**格 ⇒ 「这一本会从 Y 移到 X」—— 新增的那条实话：
 *    同一本只能占一格，按下会把原来那一格腾空，不写出来就是悄悄搬人东西。
 *
 * ## 为什么措辞里刻意不出现「收藏」那两个字
 *
 * 不是文风。屏上那枚钮自己念的就是「收藏」/「已收藏 · 点取消」，而无头判据用的
 * `onNodeWithText(...)` **默认子串匹配** ⇒ 任何含「收藏」的新句子都会与那枚钮撞成
 * "Expected exactly 1 node but found 2"，把既有的判据顶红（那种红是**台架的错**，
 * 不是新功能的错，也不该靠放宽旧判据来消）。所以这一句换成不含那两个字而**说得更多**的形状。
 *
 * `currentFolder == null`（那张表还没读回来）时不许猜"没读过"，交回最短那句实话。
 */
internal fun desktopFavoriteFolderActionLabel(
    favorited: Boolean,
    currentFolder: String?,
    selected: String,
): String =
    when {
        favorited && currentFolder == null -> "这一本现在在哪一格还没读回来 ⇒ 按下会落到 $selected"
        favorited && currentFolder == selected -> "$selected 正是这一本现在那一格"
        favorited && currentFolder != null -> "这一本会从 $currentFolder 移到 $selected"
        else -> "这一本会落到 $selected"
    }

/** 那一排上每格那枚把手念的那句（选中的那一枚点名它是"收进"还是"移到"）。 */
internal fun desktopFavoriteFolderChipLabel(name: String, selected: Boolean): String =
    if (selected) "$name ✓" else name

/**
 * 收藏那一屏每一格念的那一行「在哪一夹」（把**显示**接上）。
 *
 * 为什么要一颗纯件而不是屏里拼那一句：那一排选择器的措辞、默认那一格的名字、以及
 * "空白那一格怎么办"三处都住在这一份文件里，屏里再拼一遍就是同一件事有两个出处
 * （`DesktopMaskJudgementSingleSourceTest` 对"屏自己再判一遍"同一族）。
 *
 * 空白那一格念「（没记夹名）」而不是猜一个「默认」：库里真有那种行（`folder_name` 是空串），
 * 猜出来的那一格会被用户读成"我收在那儿了"—— 与 [desktopFavoriteFolderOf] 那一句同一口径。
 */
internal fun desktopFavoriteFolderLine(folderName: String): String =
    "夹：" + (if (folderName.isBlank()) "（没记夹名）" else folderName)

/**
 * 收藏那一屏顶部那一排「只看哪一夹」。
 *
 * ## 缺口长什么样
 *
 * 把 `folder_name` 那一列接上之后，桌面**第一次**会长出第二夹，而那一屏今天是
 * 一张平铺的网：每一格只多念一行「夹：X」⇒ 用户能把一本移到「追更」，却不能只看「追更」。
 * 那一列的价值只兑现了一半。
 *
 * ## 为什么是「一排 + 只看这一夹」而不是对岸那套「每夹一表」
 *
 * 对岸 `FavoritesScreen`（1337 行）那四格吃的是 `LocalFavoritesManager`「一夹一张表」，
 * 而桌面写的是共享那张 `comic_favorite`（主键 `(comic_id, source_name)` ⇒ 一本只占一格）。
 * 照抄「每夹一表」要动 `app/src/main`，不在桌面这条线的面里（同一条理由）。
 * 桌面的形状是 `LazyVerticalGrid(Adaptive)`，所以「每夹一表」在桌面上本来就没有对应物 ⇒
 * 这里做的是**同一面墙按夹筛**，不是给每一夹各画一屏。
 *
 * ## 三条不显然的口径
 *
 * 1. **「全部」是 `null` 而不是一夹名**：它表达的是"不按夹筛"。给它编一个假名字（把「全部」
 *    当夹名去比）就会让那一本真的被写进一个叫「全部」的夹里。
 * 2. **夹名清空了不猜别的夹**：挑中的那一格后来没有本子对「按某一夹」那一档是常事
 *    （移出完最后一本），此时交回 `null`（全部）而不是顺手换成「默认」—— 后者等于替用户改筛选。
 *    与 [desktopFavoriteFolderChoice] 那一条同一口径。
 * 3. **空白夹名那一本只在「全部」里出现**：库里真有 `folder_name` 为空串的行，而那一格自己念
 *    「夹：（没记夹名）」。把它算进「默认」那一夹是假话（它没记那一格），而给它单开一档又会让
 *    `desktopFavoriteFolderNames` 交出"名字不能当筛选值用"的第二套语义 ⇒ 筛的时候按原值比，
 *    那一本只在「全部」里看得见，并由它自己那一行说清"没记夹名"。
 */

/** 顶部那一排里「不按夹筛」那一档的名字（屏上念它，而它**不是**一夹名）。 */
internal const val DESKTOP_FAVORITE_ALL_TAB: String = "全部"

/**
 * 一排把手的渲染输入：那一档筛哪一夹（`null` = 全部）、念哪一句、选中没有。
 *
 * 为什么交成品而不把 `names` + `selected` 递进屏让屏自己拼：标签那一句要吃**每一夹的条数**，
 * 而条数是筛选判据的副产品（同一份表数两遍就会与"只看这一夹"筛出来的行数漂）。
 */
internal data class DesktopFavoriteFolderTab(
    val folder: String?,
    val label: String,
    val selected: Boolean,
)

/** 当下挑中的那一档：挑过而那一夹还在就跟着挑，那一夹空了就回「全部」（不猜另一夹）。 */
internal fun desktopFavoriteFolderTabChoice(picked: String?, names: List<String>): String? =
    picked?.takeIf { it in names }

/** 只看某一夹（`null` = 全部）。比的是**原值**，空白夹名那一本因此只出现在「全部」里。 */
internal fun desktopFavoriteFoldersShown(
    folder: String?,
    favorites: List<FavoriteRecord>,
): List<FavoriteRecord> = if (folder == null) favorites else favorites.filter { it.folderName == folder }

/**
 * 那一排把手：「全部」恒在首位，其后是在用夹（次序取自 [desktopFavoriteFolderNames]）。
 *
 * 只有「全部」一枚的时候**照样摆**：那一屏今天有零有几本都是筛得动的，而"只有一夹就把整排撤了"
 * 会让那一排随着数据出现/消失（用户刚把最后一本移出「追更」，那一排整个没了，回不来）。
 */
internal fun desktopFavoriteFolderTabRows(
    names: List<String>,
    selected: String?,
    favorites: List<FavoriteRecord>,
    /** 那张表读回来没有。`false` 时一枚把手都**不念条数**（还没读回来时念 0 是假话）。 */
    countsKnown: Boolean,
): List<DesktopFavoriteFolderTab> {
    val rows = mutableListOf(
        DesktopFavoriteFolderTab(
            folder = null,
            label = desktopFavoriteFolderTabLabel(
                DESKTOP_FAVORITE_ALL_TAB,
                if (countsKnown) favorites.size else null,
                selected == null,
            ),
            selected = selected == null,
        ),
    )
    names.forEach { name ->
        rows += DesktopFavoriteFolderTab(
            folder = name,
            label = desktopFavoriteFolderTabLabel(
                name,
                if (countsKnown) desktopFavoriteFoldersShown(name, favorites).size else null,
                selected == name,
            ),
            selected = selected == name,
        )
    }
    return rows
}

/**
 * 一枚把手的成品文案：名字 +（读回来了才有的）条数，选中的那一枚带钩。
 *
 * 两条措辞纪律：①**不许出现「收藏」那两个字**（与 [desktopFavoriteFolderActionLabel] 同一族：
 * 无头判据 `onNodeWithText` 默认子串匹配，而那一屏的标题念的就是「收藏」）；
 * ②名字**不许带「夹：」前缀**（每一格那一行念的正是「夹：X」，两处同串会让子串判据撞成 2 枚）。
 */
internal fun desktopFavoriteFolderTabLabel(name: String, count: Int?, selected: Boolean): String =
    name + (count?.let { " $it" } ?: "") + if (selected) " ✓" else ""

/**
 * 表头右侧那一句条数。
 *
 * 未筛（或一本都没有）时**逐字保持从前那句**「N 本」—— 那是既有判据钉着的形状；
 * 筛了之后必须念得出"筛过"：只念 `$shown 本` 会让用户以为收藏只剩这一夹那么多。
 * 挑中一夹而整张表是空的（`total == 0`）时退回「N 本」：那句「共 0 本中的 0 本」是空壳话。
 */
internal fun desktopFavoriteFolderCountNote(
    folder: String?,
    total: Int,
    shown: Int,
    countsKnown: Boolean,
): String = when {
    !countsKnown -> ""
    folder == null || total == 0 -> "$shown 本"
    else -> "$folder 那一夹 · 共 $total 本中的 $shown 本"
}

/**
 * 挑中某一夹而那一夹里没本子时那句（**成品文案**，`null` = 不许念这一句）。
 *
 * 为什么要有这一颗而不让屏沿用「还没有收藏。在探索页点开一本，详情页可以收藏。」：
 * 那一本明明收藏着，只是不在这一格里 ⇒ 沿用那句就是假话，而它会劝用户去"再收一遍"。
 * `total == 0`（整张表本来就没东西）时交回 `null`：那种情形该念的就是原来那句。
 */
internal fun desktopFavoriteFolderFilterNote(folder: String?, total: Int, shown: Int): String? =
    if (folder != null && total > 0 && shown == 0) "「$folder」这一格里还没有本子。" else null

/**
 * stdout 那一行：只看哪一夹、整张表几本、筛完摆几本。
 *
 * 为什么屏上已经有那一排还要这一行：屏上那枚 ✓ 只说"光标在这儿"，而**筛出来的是哪一批**
 * 要有读数可对账（`D_收藏` 那一族量的都是 stdout，不是像素）。
 */
internal fun desktopFavoriteFolderTabReading(
    folder: String?,
    total: Int,
    shown: Int,
    tabCount: Int,
): String =
    "D_收藏 夹筛 选中=${folder ?: DESKTOP_FAVORITE_ALL_TAB} 全=$total 显=$shown 档=$tabCount"

/**
 * stdout 那一行：那一本收在哪一格、按下会落到哪一格、那张表读回来没有。
 *
 * 为什么要这一行而屏上已经有那一排：屏上那枚 ✓ 只说"光标在这儿"，而**写下去落在哪一格**
 * 这件事要有读数可对账（`D_收藏` 那一族：包内取证量的是 stdout，不是像素）。
 */
internal fun desktopFavoriteFolderReading(
    comicId: String,
    currentFolder: String?,
    selected: String,
    folderCount: Int,
): String =
    "D_收藏 夹 号=$comicId 现在=${currentFolder ?: "还没读回"} 选中=$selected 在用=$folderCount"
