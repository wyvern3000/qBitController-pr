# Prowlarr 下载默认参数 —— 方案（已批准，2026-08-10）

> 状态：**已批准，按第 6 节提交计划实施中**。第 7 节两处开放问题的答复：
> 1. 分类专属路由**只覆盖保存路径/分类/标签**，其余参数始终用全局默认（不做"覆盖全部参数"这个更复杂的版本）
> 2. 设置页保存路径/分类**用纯文本框**，不做"选参考服务器自动补全"

## 0. 需求原文（用户提出，2026-08-10）

> 追加一个需求，prowlarr搜索之后点下载，传递给qbit服务器，下载参数全部是服务端默认的，比如下载路径/顺序
> 下载/先下载首尾/做种策略等等，不希望每次弹出设置，至少在设置页应该有个默认参数的配置，能灵活配置更好，
> 比如movie分类去一处，music分类去另外一处

拆成两层要求：
1. **底线**：设置页里能配一套"默认下载参数"，点下载时自动套用，不弹窗、不用每次手动填
2. **加分项**：更灵活——按分类分流，比如 movie 分类走一套保存路径，music 分类走另一套

## 1. 现状代码基线（已核实）

- `ProwlarrSearchViewModel.addTorrent()` 目前调用 `AddTorrentRepository.addTorrent()` 时，除了
  `links`/`files` 之外**全部字段硬编码为空/关闭**（`savePath=null`、`category=null`、`tags=emptyList()`、
  `stopCondition=null`、`contentLayout=null`、限速/比例/做种时间全部 `null`、`isPaused=false`、
  `skipHashChecking=false`、`isAutoTorrentManagementEnabled=null`、`isSequentialDownloadEnabled=false`、
  `isFirstLastPiecePrioritized=false`）——也就是用户描述的"下载参数全部是服务端默认的"，跟代码对得上。
- `AddTorrentRepository.addTorrent()`/`AddTorrentViewModel.addTorrent()` 本身早就支持这一整套字段
  （`AddTorrentScreen` 手动添加种子时用的就是这套参数），只是 Prowlarr 这条路径从 P0 阶段起就没传。
- `docs/prowlarr-p1-search-ui-and-tabs-plan.md` 第 2.4 节曾经规划过"单条下载一律跳转 `AddTorrentScreen`
  走完整表单"，但**这跟用户这次"不希望每次弹出设置"的要求直接冲突**，从未实施，本方案里正式废弃这个方向
  （原文档保留作为历史记录，不删除，但会在第 8 节加一条注记标注废弃原因）。
- `Search.Result`（`model/Search.kt`）/`ProwlarrSearchResult`（`model/ProwlarrSearchResult.kt`）目前都
  **没有** `categories` 字段——虽然 `docs/prowlarr-integration-plan.md` 第 2 节文档核实过 Prowlarr
  `/api/v1/search` 真实响应里确实带 `categories` 数组，但当时没有接进模型/映射。要做"按分类分流"，这个
  字段必须先补上。
- `ProwlarrSearchScreen.kt` 里已经有一套成熟的分类多选 UI（`CategoryGroup`/`buildCategoryGroups`/
  `CategorySelectionSection`/`CategoryGroupRow`，Round 8/9 做的），但目前是该文件内的 `private` 声明，
  要在新设置页复用需要先提取成 `internal` 共享组件（纯移动，不改逻辑）。
- 设置持久化用的是 `SettingsManager` 的 `jsonPreference(settings, key, default)`（已有 `prowlarrConfig`
  这个先例，`@Serializable` 对象整体编码成一个 JSON 字符串存一个 key），本方案沿用同一套机制，不新增
  存储层。

## 2. 数据模型设计

### 2.1 全局默认参数（始终存在，作为兜底）

```kotlin
// model/ProwlarrDownloadDefaults.kt
@Serializable
data class ProwlarrDownloadDefaults(
    val savePath: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val stopCondition: String? = null,          // null | "None" | "MetadataReceived" | "FilesChecked"
    val contentLayout: String? = null,           // null | "Original" | "Subfolder" | "NoSubfolder"
    val downloadSpeedLimit: Int? = null,          // KiB/s，null = 不限速
    val uploadSpeedLimit: Int? = null,
    val ratioLimit: Double? = null,
    val seedingTimeLimit: Int? = null,            // 分钟
    val isPaused: Boolean = false,
    val skipHashChecking: Boolean = false,
    val isAutoTorrentManagementEnabled: Boolean? = null,  // null = 跟随服务器默认
    val isSequentialDownloadEnabled: Boolean = false,
    val isFirstLastPiecePrioritized: Boolean = false,
)
```

字段集合直接照抄 `AddTorrentRepository.addTorrent()` 的参数列表（去掉 `torrentName`——批量默认场景下
"重命名成固定名字"没有意义，这个字段永远传 `null`，跟 P0 现状一致）。

### 2.2 分类专属路由（列表，可选覆盖）

```kotlin
// model/ProwlarrCategoryRoute.kt
@Serializable
data class ProwlarrCategoryRoute(
    val id: String,                 // 创建时生成的稳定 key，仅用于编辑/删除定位，不展示
    val name: String,               // 用户自己起的名字，比如"电影"、"音乐"
    val categoryIds: List<Int>,     // 命中这些 Torznab 分类 id（含子分类）之一就套用这条路由
    val savePath: String? = null,   // null = 落回全局默认的 savePath
    val category: String? = null,   // null = 落回全局默认的 category
    val tags: List<String> = emptyList(),  // 空 = 落回全局默认的 tags
)
```

**关键的范围取舍（需要用户确认，见第 7 节）**：分类路由**只覆盖 `savePath`/`category`/`tags` 这三项**，
其余参数（限速/比例/做种时间/暂停/跳过校验/自动管理/顺序下载/首尾优先）**始终用全局默认，不做分类区分**。
理由：
- 用户给的例子（"movie 分类去一处，music 分类去另外一处"）本质是"存放位置 + qBit 分类标签"这类**路由**
  需求，不是"电影要顺序下载、音乐不要"这类**行为策略**需求——按字面需求最小化实现，复杂度可控很多
- 如果确认要按分类覆盖全部参数，模型和编辑 UI 都要在 `ProwlarrCategoryRoute` 里把全局默认的 14 个字段
  整套复制一遍（每个字段从"必填"变成"null = 继承全局"的可选覆盖），编辑界面的字段数量会翻倍，复杂度
  明显上升——**这是本方案想请用户确认是否值得做的地方**，不是遗漏

### 2.3 结果分类信息补全

```kotlin
// ProwlarrSearchResult.kt 新增字段
val categories: List<Int>? = null

// Search.Result（model/Search.kt）新增字段，默认空列表，不影响 qBit 自带搜索的现有解码路径
val categories: List<Int> = emptyList()

// toSearchResult() 新增映射
categories = categories ?: emptyList(),
```

`Search.Result` 是直接对应 qBittorrent 自带搜索插件 API 返回格式的 `@Serializable` 类（字段带
`@SerialName` 精确对应 qBit 的 JSON key），Round 3 起就把它复用给 Prowlarr 结果显示。这里新增一个带
默认值、且不带 `@SerialName` 的字段，对 qBit 自带搜索的解码路径没有任何影响（多出来的字段永远用默认
空列表），跟 Round 3 确立的"最大化复用现有模型"原则一致。

## 3. 匹配与解析逻辑

```kotlin
// 一个纯函数，不依赖 ViewModel/State，方便复用和后续测试
fun resolveDownloadParams(
    resultCategoryIds: List<Int>,
    routes: List<ProwlarrCategoryRoute>,
    defaults: ProwlarrDownloadDefaults,
): Triple<String?, String?, List<String>> {  // (savePath, category, tags)
    val route = routes.firstOrNull { route -> route.categoryIds.any { it in resultCategoryIds } }
    return if (route == null) {
        Triple(defaults.savePath, defaults.category, defaults.tags)
    } else {
        Triple(
            route.savePath ?: defaults.savePath,
            route.category ?: defaults.category,
            route.tags.ifEmpty { defaults.tags },
        )
    }
}
```

- 匹配规则：路由列表按用户排列的顺序，**第一个** `categoryIds` 与结果分类有交集的路由命中，不做"最长/
  最精确匹配"之类的优先级——用户自己拖/排列顺序来解决歧义（比如同时匹配 Movies 大类和某个更细分类的
  两条路由时，谁排在前面谁生效），列表本身够短（预期几条到十几条），不需要复杂的优先级算法
- 结果没有分类信息（`categories` 为空，比如索引器没上报，或 usenet 结果理论上已被现有逻辑过滤掉）时，
  直接落回全局默认，不报错

`ProwlarrSearchViewModel.addTorrent()` 改造：

```kotlin
private suspend fun addTorrent(serverId: Int, links: List<String>?, files: List<Pair<String, ByteArray>>?) {
    val defaults = settingsManager.prowlarrDownloadDefaults.value
    val routes = settingsManager.prowlarrCategoryRoutes.value
    val (savePath, category, tags) = resolveDownloadParams(currentResult.categories, routes, defaults)

    addTorrentRepository.addTorrent(
        serverId = serverId,
        links = links,
        files = files,
        savePath = savePath,
        category = category,
        tags = tags,
        stopCondition = defaults.stopCondition,
        contentLayout = defaults.contentLayout,
        torrentName = null,
        downloadSpeedLimit = defaults.downloadSpeedLimit,
        uploadSpeedLimit = defaults.uploadSpeedLimit,
        ratioLimit = defaults.ratioLimit,
        seedingTimeLimit = defaults.seedingTimeLimit,
        isPaused = defaults.isPaused,
        skipHashChecking = defaults.skipHashChecking,
        isAutoTorrentManagementEnabled = defaults.isAutoTorrentManagementEnabled,
        isSequentialDownloadEnabled = defaults.isSequentialDownloadEnabled,
        isFirstLastPiecePrioritized = defaults.isFirstLastPiecePrioritized,
    )
}
```

（需要把 `Search.Result` 一路带到这个私有 `addTorrent` 重载里，而不是像现状只传 `links`/`files`——具体
是给这个私有重载加一个 `categories: List<Int>` 参数，从公开的 `addTorrent(serverId, searchResult)` 往下传。）

## 4. 设置存储

```kotlin
// SettingsManager 新增两行，跟现有 prowlarrConfig 用同一套 jsonPreference 机制
val prowlarrDownloadDefaults = jsonPreference(settings, "prowlarrDownloadDefaults", ProwlarrDownloadDefaults())
val prowlarrCategoryRoutes = jsonPreference(settings, "prowlarrCategoryRoutes", emptyList<ProwlarrCategoryRoute>())
```

## 5. UI 设计

新增一个设置子页 `ui/settings/prowlarr/download/ProwlarrDownloadDefaultsScreen.kt`（+ 配套
`ProwlarrDownloadDefaultsViewModel.kt`），从 `ProwlarrSettingsScreen.kt` 加一个入口按钮跳转过去（跟现在
"跳转到外观设置去管理页签显示"那个按钮是同一个交互模式）。导航：`Destination.Settings` 密封类下加
`ProwlarrDownloadDefaults` 这个 `data object`，在 `SettingsNavHost.kt` 注册。

页面结构（表单式，顶部 App Bar 一个"保存"菜单项，跟 `ProwlarrSettingsScreen` 现有风格一致，不是逐字段
自动保存）：

**第一部分「默认参数」**（始终存在，永远生效的兜底）：
- 保存路径（文本框，纯文本，不做服务器路径自动补全——因为这套默认值要在"当前隐式选中的那台 qBit
  服务器"之外仍然通用，不方便在设置页绑死某一台服务器去查询真实路径列表；如果用户反馈需要自动补全，
  可以后续加一个"参考服务器"下拉去查`getDirectoryContent`，本轮先用纯文本换取实现简单）
- qBit 分类（文本框，纯文本，原因同上——不去某台服务器查真实分类列表）
- 标签（文本框，逗号分隔）
- 停止条件（下拉：跟随服务器默认 / 不停止 / 元数据接收后停止 / 文件校验后停止）
- 内容布局（下拉：跟随服务器默认 / 原始 / 创建子文件夹 / 不创建子文件夹）
- 下载/上传限速（两个数字文本框，KiB/s，留空 = 不限速）
- 分享率限制（数字文本框，留空 = 跟随服务器默认）
- 做种时间限制（数字文本框，分钟，留空 = 跟随服务器默认）
- 添加后暂停（开关）
- 跳过哈希校验（开关）
- 自动管理模式（三态单选，复用现有 `RadioButtonWithLabel` 组件：跟随服务器默认 / 关闭 / 开启）
- 顺序下载（开关）
- 优先下载首尾文件块（开关）

**第二部分「分类专属路由」**：
- 列表展示已有路由（名字 + 分类 chip 摘要 + 编辑/删除按钮），支持拖拽或上下箭头调整顺序（决定第 3 节
  提到的"第一个命中的生效"的优先级）
- "+ 添加" 打开一个对话框（`AlertDialog`，字段少，不需要整页）：名字、分类多选（复用提取出来的
  `CategorySelectionSection`，需要先加载 `ProwlarrRepository.getIndexers()`）、保存路径/分类/标签
  （留空 = 继承默认参数，文本框 placeholder 直接写"跟随默认参数"提示）

## 6. 实施拆分（提交计划，审批后按此执行）

1. **重构（零行为变化）**：把 `ProwlarrSearchScreen.kt` 里的 `CategoryGroup`/`buildCategoryGroups`/
   `CategorySelectionSection`/`CategoryGroupRow`/`CategorySectionLabel`/
   `SITE_SPECIFIC_CATEGORY_ID_THRESHOLD` 原样挪到新文件 `ui/prowlarr/ProwlarrCategoryPicker.kt`，
   可见性从 `private` 改 `internal`。纯移动，diff 应该只有"删除+粘贴+改可见性"，不改一行逻辑。
2. **数据层**：`ProwlarrDownloadDefaults.kt`、`ProwlarrCategoryRoute.kt`、`Search.Result`/
   `ProwlarrSearchResult` 加 `categories` 字段、`SettingsManager` 两个新 `jsonPreference`、
   `resolveDownloadParams()` 纯函数。
3. **接入下载逻辑**：`ProwlarrSearchViewModel.addTorrent()` 改造成读取默认参数 + 路由匹配，替换掉现在
   全部硬编码 null/false 的那个私有重载。这步完成后，即使还没有设置页 UI，也可以先手动改
   `SettingsManager` 默认值验证链路通不通（开发期临时手段，不代表最终交付形态）。
4. **设置页 UI（默认参数部分）**：新增 `ProwlarrDownloadDefaultsScreen`/`ViewModel`，先只做第 5 节
   "第一部分「默认参数」"，导航注册，`ProwlarrSettingsScreen` 加入口按钮。到这一步"用户能在设置页配置
   默认参数，下载真的套用"这条底线需求就已经闭环。
5. **设置页 UI（分类路由部分）**：在同一个页面加第 5 节"第二部分「分类专属路由」"的列表 + 添加/编辑
   对话框。
6. 每步 push，在步骤 5 完成后 dispatch 一次完整 CI 构建验证。

## 7. 开放问题（已确认，供实施阶段留痕）

1. **分类路由的覆盖范围** → 用户确认：只覆盖保存路径/分类/标签三项（第 2.2 节的取舍），不做"每个字段
   都能按分类覆盖"的更复杂版本。第 2.2 节/第 3 节的设计按此定稿，无需再改。
2. **保存路径/分类的输入方式** → 用户确认：纯文本框，不做"选参考服务器自动补全"。第 5 节 UI 设计按此
   定稿，无需再改。
3. **路由匹配优先级**（"列表顺序，第一个命中生效"）、**批量下载复用同套默认参数的方向**——这两点用户
   没有异议，按第 3/6 节原方案执行。
