# P1 阶段方案：Prowlarr 搜索体验增强 + 页签显隐管理

> 承接 `docs/prowlarr-integration-plan.md`（P0 方案 + 实施纪要，第 8 节是权威现状）。P0 已在
> `feature/prowlarr-connection` 分支验证通过（round 5，CI 构建出可安装 APK）。本文档是 P0 验收通过后
> 用户提出的下一阶段需求，同时把原方案第 3 节列的 P1 待办（indexer 多选、分类映射）并入。
>
> 本文档只是**方案设计**，尚未开始编码（对照协作规范，编码会在获得确认后按"分阶段实施步骤"里的每一步
> 单独提交+推送）。以下设计均对照本分支当前真实代码核对过路径/类名/字段名。

---

## 0. 本轮需求原文（用户提出，2026-08-09）

1. Prowlarr 搜索页参照 Prowlarr 自身搜索界面重新设计：除关键字外，增加**勾选索引器（站点）**、
   **对应的分类**，以及**对搜索结果的筛选**功能
2. Prowlarr 的底部导航 tab 按钮位置：从"追加在最后"改为"排在搜索 tab 按钮之后"
3. 设置页"外观"菜单下方，新增"页签显示"勾选：默认全选，可以去掉"搜索"、"RSS"、"日志"这几个 tab，
   "种子"和"设置"必须保留（不可隐藏）

> **追加讨论（同一天，方案初稿写完后）**：用户追问"搜到种子点下载，最终发到哪台 qBit 服务器"这个交互
> 该怎么设计（App 支持配置多台服务器），讨论过程中确认 Prowlarr 的 `downloadUrl`/`magnetUrl` 可以直接
> 作为 URL 交给下载器（不必强制客户端先下载字节），进而扩展出"下载方式"（服务端直连 URL / 客户端直传
> 字节）、单条下载交互、批量下载三块新设计，一并归入本文档第 2.4 节。分类多选也在这轮讨论中从"只做
> 大类"改为"要做子分类"，第 2.2 节已同步更新。

---

## 1. 现状代码基线（写方案前对照的真实代码，避免脱离实际）

- `ui/main/MainScreen.kt`：`tabs` 是一个 `buildList { ... }` 构建的固定顺序列表——
  Torrents(0) → Search(1) → Rss(2) → Logs(3) → Settings(4) → **Prowlarr(条件追加，5)**。
  `navigateToStartChannels` 是 `List(tabs.size) { Channel<Unit>() }`，按 tabs 顺序一一对应。
  文件里至少 7 处硬编码了 tab 下标字面量：
  - `onNavigateToRss = { selectedTabIndex = 2 }`（TorrentsNavHost 回调）
  - `onNavigateToSearch = { selectedTabIndex = 1 }`（同上）
  - `DeepLinkDestination.Settings -> selectedTabIndex = 4`
  - 6 个 `composable<NavHostDestination.Xxx> { ... navigateToStartChannels[N] ... }`（N = 0~5）
  - `ProwlarrSearchScreen(onNavigateToSettings = { selectedTabIndex = 4 }, ...)`
  这套写死下标的方案是 round 4 故意为之（见 `prowlarr-integration-plan.md` 8.1 节）：把 Prowlarr
  **追加在末尾**，这样已有 5 个 tab 的下标完全不受影响，改动面最小。这次要把 Prowlarr 挪到 Search
  之后，这个前提不再成立，必须重构（见第 3 节）。
- `data/SettingsManager.kt`：`showProwlarrTab: Boolean`（默认 `false`）是目前唯一一个"tab 显隐"开关，
  只管 Prowlarr 一个 tab，入口在 `ui/settings/prowlarr/ProwlarrSettingsScreen.kt` 里一个独立的
  `SwitchPreference`（"在底部导航栏中显示"）。Search/RSS/Logs 目前**没有**任何显隐开关，
  `enabled = currentServer != null` 只是"未选服务器时置灰"，不是"隐藏"。
- `ui/settings/appearance/AppearanceSettingsScreen.kt`：294 行，`LazyColumn` 里依次是
  语言、主题、动态取色开关、取色器、纯黑暗色模式开关、相对时间戳开关。全部是 `item { }` 平铺，
  没有用到 `PreferenceCategory` 分组（仓库里 `PreferenceCategory` 目前只在
  `AdvancedServerSettingsScreen.kt` 用过一次）。
- Prowlarr 搜索页 `ui/prowlarr/search/ProwlarrSearchScreen.kt`（365 行）+
  `ProwlarrSearchViewModel.kt`（165 行）：目前只有关键字输入框 + 结果列表（标题/大小/来源/做种吸血数
  /下载按钮），**没有**排序菜单、过滤 Dialog、indexer 选择、分类选择——这些是 qBit 原生搜索结果页
  （`ui/search/result/SearchResultScreen.kt`）已有、但 Prowlarr 页面当初为了"零侵入"选择独立实现、
  没有搬过来的部分（8.1 节记录的取舍）。
- `network/ProwlarrService.kt`：只有 `getSystemStatus()`、`downloadFile()`、
  `search(query, indexerIds)` 三个方法，**没有** `getIndexers()`，`search()` **没有** `categories`
  参数。
- `model/ProwlarrSearchResult.kt`：没有 `categories` 字段（Prowlarr 实际返回里应该有，之前没接）。

---

## 2. 索引器多选 + 分类多选 + 结果筛选（对应需求 1）

### 2.1 索引器（站点）多选

**新增接口封装**（`network/ProwlarrService.kt` 追加一个方法）：

```kotlin
suspend fun getIndexers(): Response<List<ProwlarrIndexer>> = get("indexer")
```

**新增数据模型**（`model/ProwlarrIndexer.kt`，字段对照 Prowlarr 官方 SDK 命名，具体字段名需要开发
时用真实 Prowlarr 实例的 Swagger 核实一遍，下面是基于文档的预期结构）：

```kotlin
@Serializable
data class ProwlarrIndexer(
    val id: Int,
    val name: String,
    val enable: Boolean = true,
    // Prowlarr 里每个 indexer 支持的分类集合不完全一样（有的站点没有 XXX 分类，有的没有 Console），
    // 后续做分类多选时可以用这个字段来"只显示当前已勾选 indexer 集合支持的分类"，P1 先不做这层联动，
    // 先把数据模型占位出来
    val capabilities: ProwlarrIndexerCapabilities? = null,
)

@Serializable
data class ProwlarrIndexerCapabilities(
    val categories: List<ProwlarrCategory> = emptyList(),
)

@Serializable
data class ProwlarrCategory(
    val id: Int,
    val name: String,
)
```

**UI 交互**：直接照搬 qBit 搜索起始页（`ui/search/start/SearchStartScreen.kt`）已有的插件多选三态模式
（`PluginSelection.Enabled` / `.All` / `.Selected` + `FilterChip` 勾选列表），这套交互已经在生产代码里
跑了很久、用户也熟悉，索引器和插件是同构的概念（"一路可勾选的数据源"），没有理由另起一套新交互：

- 三个分段选项：**已启用的索引器** / **全部索引器** / **自选**
- 选"自选"时展开一个 indexer 名称的勾选列表（`FilterChip`，参照 `SearchStartScreen.kt` 268~360 行左右
  分类下拉菜单和插件 chip 列表的写法）
- 页面首次加载时用**已启用的索引器**（对应 `enable == true`）作为默认值，和现状"用全部已配置 indexer"
  相比更贴近 Prowlarr 自己网页版的默认行为
- 索引器列表通过新增的 `ProwlarrRepository.getIndexers()` 拉取（挂一个 `IO` 状态：加载中/失败/成功），
  失败时不阻塞搜索，退化成"传空 indexerIds"（等价于查全部）

### 2.2 分类多选

Prowlarr 走 Torznab/Newznab 标准分类体系，大类固定 8 个（数值是标准分类号，非 Prowlarr 自定义）：

| 分类号 | 含义 |
|---|---|
| 1000 | Console |
| 2000 | Movies |
| 3000 | Audio |
| 4000 | PC |
| 5000 | TV |
| 6000 | XXX |
| 7000 | Books |
| 8000 | Other |

**P1 范围（改定）**：原方案初稿建议 P1 只做大类、子分类留 P2，讨论后用户明确要求 **P1 就做子分类**
（如 `2040 HD`、`5070 Anime` 这类三级分类），一并纳入本轮，不再拆到 P2。这意味着 2.1 节里原本只是
占位、"P1 先不做这层联动"的 `ProwlarrIndexerCapabilities.categories` 字段，**现在要真正用起来**：

- **UI 改成两级勾选**：8 个大类作为可展开的分组（点击展开/收起，而不是像 2.1 节 indexer chip 那样一
  个平铺列表），展开后显示该大类下的子分类 `FilterChip`。勾选大类本身也是一个独立的可选项（对应
  Torznab 惯例：直接搜大类分类号，语义上通常已经覆盖其全部子分类，具体行为仍需第 7 节待确认）
- **子分类列表是动态的**：不同 indexer 支持的子分类集合不一样（有的站点没有 `2060 3D`，有的没有
  `6000 XXX` 大类），所以子分类候选集要跟随 2.1 节"已勾选的索引器集合"联动重新计算——从选中的每个
  indexer 的 `capabilities.categories` 里取并集，勾选索引器变化时子分类列表要重新刷新，之前勾选的
  子分类如果不再属于并集范围要自动去掉勾选（避免选中一个已经不存在的分类导致搜索行为不可预期）
- **数据结构不用改**，`ProwlarrIndexer`/`ProwlarrIndexerCapabilities`/`ProwlarrCategory`（2.1 节已经
  设计好）本来就是为这层联动准备的，只是这次要真正接上，不是占位
- 这块 UI 复杂度确实比最初"8 个大类平铺 chip"的方案高一截（两级展开 + 跟索引器选择动态联动），第 6
  节的实施步骤顺序不变，但第五步的工作量预期要相应上调

**接口改动**：`ProwlarrService.search()` 追加 `categories: List<Int>? = null`，用和 `indexerIds`
一样的重复 query 参数方式追加（`url.parameters.append("categories", id.toString())`）。不勾选任何分类
时不传该参数，等价于 Prowlarr 默认行为"查全部分类"。

### 2.3 搜索结果筛选（排序 + 过滤）

现状 Prowlarr 页面完全没有排序/过滤，qBit 原生结果页（`SearchResultScreen.kt`）已有：
- 排序菜单：按名称/大小/做种数/吸血数/来源站点，可正序/倒序（`SearchResultViewModel.kt` 里
  `SearchSort` 枚举，`SettingsManager.searchSort` / `isReverseSearchSorting` 记忆上次选择）
- 过滤 Dialog（`SearchResultScreen.kt` 969~1100+ 行 `FilterDialog`）：做种数区间、大小区间（带单位
  下拉）

**实现路径两个选项**：

- **选项 A**：把 `FilterDialog`/排序菜单从 `SearchResultScreen.kt` 抽成不依赖 `SearchResultViewModel`
  的独立 Composable，qBit 结果页和 Prowlarr 页共用。这正是 P0 最初方案（`prowlarr-integration-plan.md`
  4.6 节）设想过、但 P0 实际开发时为了"零侵入 `ui/search/*`"放弃的方案（8.1 节有记录）。
- **选项 B**：Prowlarr 页面照抄一份排序菜单 + `FilterDialog` 的简化版，独立维护，不碰
  `ui/search/*` 任何文件。

**建议选 B**，和 P0 阶段"完全独立页面、可单独回退"的既有取舍保持一致——这条分支上 Prowlarr 页面出
任何问题都不会牵连到原有 qBit 插件搜索功能。代价是两处各维护一份相似 UI，但目前只有两处，复制成本可
接受；如果以后出现第三处需要同样的排序/过滤 UI，再回头做选项 A 的抽象更合适。

在照抄的简化版基础上，针对 Prowlarr 场景**新增一项 qBit 结果页没有的过滤维度**：**来源索引器关键词
过滤**（因为 Prowlarr 一次搜索可能命中几十个不同站点，比 qBit 插件搜索的"来源"更多元，单独筛选更有
用）。做种/大小区间过滤逻辑直接复用 `SearchResultViewModel.Filter` 数据类的比较逻辑（拷贝一份，不共
享类型，理由同上）。

排序/过滤状态记忆：新增独立的 `SettingsManager` 偏好项（不复用 `searchSort`/`isReverseSearchSorting`，
避免两个数据源来回切换排序时相互干扰）：

```kotlin
val prowlarrSearchSort = preference(settings, "prowlarrSearchSort", SearchSort.NAME)
val isReverseProwlarrSearchSort = preference(settings, "isReverseProwlarrSearchSort", false)
```

### 2.4 下载目标：单条下载走 `AddTorrentScreen`、下载方式（直连/直传）、批量下载

这三块是方案初稿写完后追加讨论出来的，起因是"App 配置了多台 qBit 服务器时，Prowlarr 下载该发到哪
台"这个问题——现状 P0 是隐式用 `currentServer?.id`（也就是种子列表 tab 当前选中的那台），配置多台
服务器时用户很容易在没意识到的情况下发错服务器。

**现状代码基线**：`AddTorrentScreen.kt`/`AddTorrentViewModel.kt` 已经解决过一次同样的问题——
`initialServerId == null && servers.size > 1` 时会显示一个服务器下拉选择器（默认选中第一台），qBit
自带搜索点下载就是跳到这个屏（`onNavigateToAddTorrent(fileUrl)`，`fileUrl` 可以是磁力链也可以是
种子文件的 HTTP 直链，qBittorrent 服务端自己抓取），Prowlarr 页面 P0 阶段没有复用这条路，是自己写的
一套"固定默认参数、不跳转、直接调 `addTorrentRepository.addTorrent()`"的快速逻辑。

**关键澄清（这轮讨论调研出来的事实，不是猜测）**：Prowlarr 的 `downloadUrl` 对大多数 indexer 而言
是 **Prowlarr 自己的下载代理端点**，形如
`http://<prowlarr-host>:9696/{indexerId}/download?apikey=xxx&link=<token>&file=xxx.torrent`，
**apikey 已经内嵌在 URL 的 query string 里**，不需要额外带 `X-Api-Key` 请求头去抓取。这意味着可以
把这个 URL 原样交给 qBittorrent 服务端抓取（走 `torrents/add` 的 `links` 参数，跟 qBit 自带搜索现在
的机制完全一致），**不需要 P0 那套"客户端先把字节下下来再以文件形式上传"的方案**，也不需要为了走
`AddTorrentScreen` 而新增"字节转临时文件"的跨平台工程（`AddTorrentScreen` 本来就有
`torrentUrl: String?` 参数，直接传这个 URL 即可）。

但服务端直连有两个真实存在的坑（Prowlarr 官方 GitHub issue tracker 里有实测反馈，不是猜测）：
1. **网络可达性**：`downloadUrl` 的主机名/端口必须从"发起下载抓取的那一端"（这里是 qBit 服务端）
   连得通。Prowlarr 部署在反代/Docker 后面时，`downloadUrl` 里的主机名可能是 `127.0.0.1` 或内部
   Docker 服务名，qBit 服务端如果和 Prowlarr 不在同一台机器/同一网络，会直接抓取失败——这正是 P0
   round 2→3 当初改成"客户端直传"的原因
2. **部分 indexer 只提供磁力链**：这种情况下 Prowlarr 的下载代理会返回 HTTP 301 跳转到 `magnet:`
   URI 而不是真的 `.torrent` 字节流，qBittorrent 自己的抓取器（基于 libtorrent，本来就是为 BT
   场景设计的）大概率能正确处理，但没有在真实环境验证过

**结论与决定（讨论后拍板）**：

- **下载方式做成可切换的开关，放在 `ui/settings/prowlarr/ProwlarrSettingsScreen.kt`**："服务端直连
  URL" / "客户端直传字节"（P0 现状逻辑原样保留，作为兜底选项），**默认服务端直连**
- **单条下载点击后一律跳转 `AddTorrentScreen`**（不再是 P0 那套"固定默认参数直接提交"的快速逻辑），
  `Destination.AddTorrent(initialServerId = null, torrentUrl = result.fileUrl)`——`initialServerId`
  显式传 `null` 而不是 `currentServer?.id`，让"配置了多台服务器时是否弹选择器"这件事交给
  `AddTorrentScreen` 已有的逻辑去处理，不再由 Prowlarr 页面自己隐式决定目标服务器。这样做的好处：
  - 跟 qBit 自带搜索的下载体验完全对齐（这条本来就是本 App 里"搜索结果点下载"的统一约定，Prowlarr
    P0 阶段是唯一的例外）
  - 白嫖到 `AddTorrentScreen` 已有的存储路径/分类/标签选择器，P0 备注里提到的"这轮没有这些选项"的
    缺口一并补上
  - "服务端直连"模式下 `torrentUrl` 直接是 `result.fileUrl`；"客户端直传"模式下磁力链同样直接传
    `torrentUrl`（磁力链不受直连/直传开关影响，两种模式下都是直接传 link，无需下载字节），非磁力
    直链则需要 `AddTorrentViewModel` 提交前先把字节下载好、转成 `files` 参数——这部分复用 P0 已有
    的 `ProwlarrSearchRepository.downloadTorrentFile()` 逻辑，只是调用方从
    `ProwlarrSearchViewModel.addTorrent()` 挪到 `AddTorrentViewModel` 的提交路径上，具体挪法留到
    编码阶段设计（`AddTorrentViewModel` 目前是 `ui/addtorrent` 包内的通用组件，不感知 Prowlarr，
    这里需要一个不侵入原有 addtorrent 逻辑的接入方式，比如提交前的一个可选"来源是 Prowlarr 直链，
    先转字节"步骤，仅在 Prowlarr 入口触发时启用）
- **服务端直连失败的检测与兜底**：提交后轮询种子列表几秒，超时判定为"大概率失败"（qBittorrent 的
  `torrents/add` 用 URL 添加时接口本身通常立刻返回成功，真正的抓取是异步的，无法从提交请求的响应里
  直接拿到"抓取失败"这个信号，只能靠事后观察种子列表有没有出现来推断——这个探测机制本身的可靠性
  仍是本轮的技术不确定项，见第 7 节），超时后在界面上给一个"改用客户端直传重试"的按钮，**不做静默
  自动重试**，同时允许用户直接取消操作
- **批量下载**：结果列表加多选模式（长按进入，其余条目左侧出现勾选框），顶部显示"已选 N 项"+
  "添加"按钮；点击后如果配置了多台服务器，弹一次服务器选择（一次选择应用到本次全部已选项，不逐条
  问），选定后用固定默认参数批量提交——批量场景不可能让用户对每条种子都过一遍 `AddTorrentScreen`
  的完整表单，所以批量走的是和 P0 时期类似的"快速提交"逻辑，跟"单条下载走 `AddTorrentScreen`"是两
  条并存的路径，这是刻意的设计取舍，不是遗漏
  - **服务端直连模式下**，批量提交可以一次 API 调用搞定：qBittorrent 的 `torrents/add` 本来就支持
    `urls` 参数一次传多行（一行一个 URL），把已选结果的 `fileUrl` 拼成一份多行文本传过去即可，
    效率很高
  - **客户端直传模式下**，无法这样批量：非磁力的直链种子需要逐条下载字节、逐条上传，UI 要做成"按条
    显示下载/上传进度"的列表，而不是一次性提交；如果批量选择里全部是磁力链结果，两种模式下行为其实
    是一样的（磁力链本来就不受直连/直传开关影响）

---

## 3. Prowlarr tab 位置调整为"排在搜索 tab 之后"（对应需求 2）

### 3.1 为什么必须先重构硬编码下标

第 1 节列的 7 处硬编码下标，全部假设"tabs 顺序固定为 Torrents/Search/Rss/Logs/Settings/[Prowlarr]"。
把 Prowlarr 插入到 Search 和 Rss 之间后，顺序变成
`Torrents(0)/Search(1)/Prowlarr(2)/Rss(3)/Logs(4)/Settings(5)`，所有写死的数字全部错位（比如
`selectedTabIndex = 4` 原本指向 Settings，现在会指向 Logs）。

这个重构同时是**第 4 节"页签可显隐"需求的必要前提**：一旦 Search/RSS/Logs 也能被单独隐藏，tabs 数组
的长度和顺序组合会更多（比如"隐藏 RSS"时顺序变成 Torrents/Search/Prowlarr/Logs/Settings），继续维护
任何形式的硬编码下标都不可持续。两个需求（tab 重新排序 + tab 可显隐）本质上要求同一个底层重构，因此
放在同一轮里一起做。

### 3.2 重构方案：按 destination 查找下标，不再写死数字

```kotlin
// MainScreen.kt 内新增
private fun List<BottomNavigationItem>.indexOfDestination(destination: NavHostDestination): Int =
    indexOfFirst { it.destination == destination }.takeIf { it != -1 } ?: 0
```

所有 `selectedTabIndex = <字面量>` 替换为 `selectedTabIndex = tabs.indexOfDestination(NavHostDestination.Xxx)`；
所有 `navigateToStartChannels[<字面量>]` 同理替换。`navigateToStartChannels` 本身不用改
（`List(tabs.size) { Channel<Unit>() }` 已经是按 `tabs` 顺序 1:1 生成，只要查下标的逻辑和 `tabs`
构建顺序保持一致，天然正确）。

`tabs` 的 `buildList { }` 构建顺序改为：

```kotlin
add(Torrents)   // 始终存在
if (SEARCH in visibleTabs) add(Search)
if (PROWLARR in visibleTabs) add(Prowlarr)   // 插入在 Search 之后、Rss 之前
if (RSS in visibleTabs) add(Rss)
if (LOGS in visibleTabs) add(Logs)
add(Settings)   // 始终存在
```

（`visibleTabs` 是第 4 节新增的偏好项，这里先展示 tabs 顺序和第 4 节是同一次改动，不是两次独立改动）

### 3.3 需要额外处理的边界情况

- `rememberSaveable { mutableIntStateOf(0) }` 保存的 `selectedTabIndex`，在"上次退出时 tabs 顺序 A，
  这次启动因为用户改了显隐设置变成顺序 B"的场景下，可能出现**下标没有越界、但指向了另一个 tab**的
  隐蔽错位（比如上次停在 index 2 = Prowlarr，这次 Prowlarr 被隐藏后 index 2 变成 Rss）。现有
  `LaunchedEffect(tabs) { if (selectedTabIndex !in tabs.indices) selectedTabIndex = 0 }` 只检查越界，
  覆盖不到这种场景。需要改成同时记录"上次选中的是哪个 `NavHostDestination`"（而不只是下标），重启/
  设置变化后按 destination 重新查找下标，查不到（tab 被隐藏了）才退回 index 0。
- 4 处 `BackHandler { selectedTabIndex = 0 }` 不用改（0 永远是 Torrents，Torrents 强制常显）。

---

## 4. 外观设置新增"页签显示"勾选组（对应需求 3）

### 4.1 数据模型

```kotlin
// SettingsManager.kt
enum class OptionalTab { SEARCH, PROWLARR, RSS, LOGS }

val visibleTabs = preference(
    settings,
    "visibleTabs",
    setOf(OptionalTab.SEARCH, OptionalTab.PROWLARR, OptionalTab.RSS, OptionalTab.LOGS), // 默认全选
    serializer = { it.joinToString(",") { tab -> tab.name } },
    deserializer = { str -> str.split(",").filter { it.isNotBlank() }.map { OptionalTab.valueOf(it) }.toSet() },
)
```

Torrents、Settings **不放进这个枚举**，因为它们不可隐藏——不需要一个"永远为 true 且不能取消勾选"的
特殊状态，直接不给它们建模更简单，UI 层也不需要做"禁用勾选框"这种容易让人误以为能操作的设计。

### 4.2 UI（`AppearanceSettingsScreen.kt`，"下方"追加）

复用 `preferences` 模块已有的 `PreferenceCategory` 分组 + `SwitchPreference`（不新建"多选控件"这种
一次性组件——四个独立开关本质上就是四个独立布尔值，`SwitchPreference` 足够表达，额外做一个通用多选
弹窗组件对这个场景是过度设计）：

```kotlin
item {
    PreferenceCategory(title = { Text(text = stringResource(Res.string.settings_visible_tabs)) })
}
item {
    Text(
        text = stringResource(Res.string.settings_visible_tabs_summary), // "种子和设置始终显示"
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = ..., vertical = 4.dp),
    )
}
items(listOf(
    OptionalTab.SEARCH to Res.string.destination_search,
    OptionalTab.PROWLARR to Res.string.destination_prowlarr,
    OptionalTab.RSS to Res.string.destination_rss,
    OptionalTab.LOGS to Res.string.destination_logs,
)) { (tab, titleRes) ->
    val visibleTabs by viewModel.visibleTabs.flow.collectAsStateWithLifecycle()
    SwitchPreference(
        value = tab in visibleTabs,
        onValueChange = { checked ->
            viewModel.visibleTabs.value = if (checked) visibleTabs + tab else visibleTabs - tab
        },
        title = { Text(text = stringResource(titleRes)) },
    )
}
```

四行文案直接复用已有的 `destination_search`/`destination_prowlarr`/`destination_rss`/
`destination_logs` 字符串资源，不用新增翻译词条（只需新增"页签显示"分组标题和一行说明文案两条新字符
串，其余全部沿用底部导航已有的名字，保证两处名字视觉一致）。

### 4.3 与现有 `showProwlarrTab` 的整合

现状 `showProwlarrTab` 和这次新增的 `visibleTabs` 是同一件事（Prowlarr tab 显隐）在两个不同设置页
各存一份状态，如果不整合，会出现"外观设置里勾掉了 Prowlarr，但 Prowlarr 设置页那个开关还显示开着"
的不一致体验。方案：

1. **迁移**：`SettingsManager` 初始化 `visibleTabs` 时，如果检测到旧的 `showProwlarrTab` 存在过
   （即用户运行过 P0 版本），用旧值决定初始集合里是否包含 `OptionalTab.PROWLARR`（`showProwlarrTab
   == true` 时包含，默认 `false` 时不包含——但要注意这和 `visibleTabs` 新用户默认值"全选"是反的，
   迁移只针对"确实存过旧 key"的老用户，新用户走"全选"默认值，不受这条迁移影响）
2. **收口**：`ProwlarrSettingsScreen.kt` 里原来的"在底部导航栏中显示"`SwitchPreference` 整体移除，
   换成一行说明文字 + 一个跳转到"外观设置"的按钮，避免两处入口维护同一个状态
3. `showProwlarrTab` 这个偏好项声明本身保留（仅用于第 1 步一次性迁移读取，不再写入），不删除以免
   已经存过该 key 的用户升级时读不到旧值

---

## 5. 数据模型/文件改动清单汇总

**新增文件：**

```
commonMain/kotlin/.../model/ProwlarrIndexer.kt          # ProwlarrIndexer + ProwlarrIndexerCapabilities + ProwlarrCategory
```

**修改文件：**

```
commonMain/.../network/ProwlarrService.kt                # 加 getIndexers()，search() 加 categories 参数
commonMain/.../model/ProwlarrSearchResult.kt              # 加 categories: List<Int>? 字段（如果 Prowlarr 返回里有）
commonMain/.../data/repositories/ProwlarrRepository.kt    # 加 getIndexers() 转发
commonMain/.../data/SettingsManager.kt                    # 加 visibleTabs（OptionalTab 集合）、
                                                            #   prowlarrSearchSort、isReverseProwlarrSearchSort；
                                                            #   showProwlarrTab 保留但仅用于迁移
commonMain/.../ui/prowlarr/search/ProwlarrSearchScreen.kt        # 加 indexer 勾选（三态）、分类勾选（大类 chip）、
                                                                   #   排序菜单、FilterDialog（照抄简化版）
commonMain/.../ui/prowlarr/search/ProwlarrSearchViewModel.kt     # 加 indexers 拉取状态、search() 传 indexerIds/categories、
                                                                   #   排序/过滤状态
commonMain/.../ui/main/MainScreen.kt                       # 去字面量化（indexOfDestination）、tabs 构建顺序按
                                                             #   visibleTabs 过滤 + Prowlarr 插入 Search 之后、
                                                             #   selectedTabIndex 的越界/错位校验改按 destination 比对
commonMain/.../ui/settings/appearance/AppearanceSettingsScreen.kt   # 新增"页签显示"分组 + 4 个 SwitchPreference
commonMain/.../ui/settings/appearance/AppearanceSettingsViewModel.kt # 暴露 visibleTabs 偏好委托
commonMain/.../ui/settings/prowlarr/ProwlarrSettingsScreen.kt       # 移除"在底部导航栏中显示"开关，
                                                                      #   换成说明文字 + 跳转按钮
commonMain/composeResources/values/strings.xml             # 新增 settings_visible_tabs、
                                                             #   settings_visible_tabs_summary 等词条
```

---

## 6. 分阶段实施步骤与验收标准

严格按此顺序推进，每步一个可验证的最小单元，对照协作规范逐步 commit + push：

**第一步：tab 下标去字面量化重构（不改变任何现有可见行为）**
- 完成 `indexOfDestination` 辅助函数，替换全部 7 处硬编码下标
- 验收：Prowlarr 仍然显示在最后一位（本步不改 tabs 构建顺序，只改查找方式），底部导航栏点击切换、
  长按跳转、deep link 到 Torrents/Settings、下拉刷新触发 `navigateToStartFlow` 行为与改动前完全一致

**第二步：`visibleTabs` 偏好项 + 外观设置页勾选组 + 迁移逻辑**
- 完成 `SettingsManager.visibleTabs`、`showProwlarrTab` 迁移读取、`AppearanceSettingsScreen.kt`
  新增分组、`ProwlarrSettingsScreen.kt` 移除旧开关
- 验收：默认全选（新用户）；升级用户原 `showProwlarrTab` 状态正确迁移；四个 tab 能单独勾掉/勾回，
  立即生效不需要重启 App；尝试隐藏全部四个可选 tab 后，底部导航栏只剩 Torrents 和 Settings 两项

**第三步：tabs 构建顺序调整为 Prowlarr 排在 Search 之后**
- 结合第一、二步的重构，调整 `buildList { }` 的插入顺序
- 验收：默认设置下底部导航顺序为 Torrents/Search/Prowlarr/Rss/Logs/Settings；各种显隐组合下
  Prowlarr 相对 Search 的位置关系始终正确；`selectedTabIndex` 在设置变化后不会指向错误的 tab
  （第 3.3 节的错位场景）

**第四步：Prowlarr 索引器多选**
- `ProwlarrService.getIndexers()` + `ProwlarrRepository` 转发 + `ProwlarrSearchScreen.kt` UI
- 验收：能看到已配置索引器列表；三态切换（已启用/全部/自选）正确；勾选子集后搜索结果确实只来自
  被勾选的索引器（用真实 Prowlarr 实例实测，至少验证两个不同索引器分别命中不同结果的场景）

**第五步：Prowlarr 分类多选（大类+小类）**
- `search()` 加 `categories` 参数 + UI 8 个大类 chip
- 验收：勾选"Movies"后结果只包含该分类（用一个同时命中电影和非电影资源的关键词实测对比勾选前后
  结果差异）

**第六步：Prowlarr 结果排序 + 过滤**
- 照抄简化版排序菜单 + `FilterDialog`（含新增的索引器关键词过滤）
- 验收：排序切换生效且刷新页面后记忆上次选择；做种/大小区间过滤生效；索引器关键词过滤生效；行为
  观感与 qBit 原生结果页一致（不要求代码复用，但交互体验要一致）

---

## 7. 待确认事项（Open Questions，本轮无法在沙盒里实测，需要用户在真实 Prowlarr 实例上确认）

1. `GET /api/v1/indexer` 的真实返回结构（字段名、`capabilities.categories` 是否确实长这样）——
   本沙盒连不上任何 Prowlarr 实例，第 2.1 节的 `ProwlarrIndexer` 模型是按官方文档推测的，开发时
   第一步应该是用真实响应核对字段名，而不是直接假设本文档写的字段名 100% 准确
2. 分类多选要不要支持子分类（如 `2040 HD`）——需要支持
3. 索引器/分类的勾选状态要不要跨会话记忆（存到 `SettingsManager`）还是每次进页面重置——本轮先不
   记忆（每次默认"已启用的索引器"+ 不勾分类=全部），减少状态复杂度，如果用户反馈"每次都要重新勾选
   很烦"再考虑加偏好项记忆
4. 结果过滤除了"做种/大小区间 + 索引器关键词"，用户截图里能看到顶部有一个"过滤"按钮但看不出具体
   维度（截图分辨率/遮挡问题），如果用户有更具体的过滤维度诉求（比如按发布日期、按 usenet/torrent
   协议类型过滤——虽然协议类型本来就会被过滤掉 usenet），本轮先按当前列的维度实现，后续再补
5. `showProwlarrTab` 迁移到 `visibleTabs` 后原开关直接移除是否合适——已在 4.3 节给出方案，但需要
   用户确认没有依赖这个具体设置页位置的平台特定深链（目前代码里没搜到相关深链，只是双重确认）
6. 原方案 P1 待办里的"两路数据源同时勾选、结果合并展示"（`prowlarr-integration-plan.md` 第 3 节）
   本轮**不**包含在内——本轮需求聚焦于"让 Prowlarr 独立页面自己更好用"（索引器/分类/过滤）+
   "tab 管理"，不涉及把 qBit 插件搜索和 Prowlarr 搜索结果合并到同一个列表里。这条继续留在 P1 backlog
   里，等本轮做完再评估要不要做

---

## 8. 与原方案的关系

- 本文档是 `prowlarr-integration-plan.md` 第 3 节 P1 的具体化 + 用户本轮新提出的 tab 管理需求，
  两者合并成一轮
- 第 2.1/2.2 节直接对应原方案第 3 节 P1 列的"indexer 多选"、"分类映射"
- 原方案第 7 节"待确认事项"里"`categories` 参数是否 P0 接入"已有结论（P0 不接，本轮接，见本文档
  第 2.2 节）；"分页 `limit`/`offset` 是否生效"仍未实测，不在本轮范围内，继续留在 backlog
- 完成后应该更新 `prowlarr-integration-plan.md`，把本文档收束的结论合并进那份文档的"实施纪要"，
  避免两份文档长期并存导致互相打架（参照协作规范里"以最新为准"的原则，建议届时把本文档内容合并
  进主文档第 9 节，本文档归档）
