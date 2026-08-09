# qBitController 集成 Prowlarr 搜索源 —— 实施方案

> 目标仓库：https://github.com/Bartuzen/qBitController
> 本方案基于对 `master` 分支源码的实际拉取核查（而非纯粹的规划推测），所有文件路径、类名、字段名均对照真实代码。文末列出了仍需在开发阶段用 Prowlarr 的 Swagger（`http://<prowlarr>:9696/docs`）二次确认的细节。

> **⚠️ 本节以下（第 0～7 节）是 P0 阶段动工前写的最初方案，保留作为背景记录。实际开发中方向有过一次重大调整（复用现有搜索 UI → 完全独立的 Prowlarr 页面），已实施的部分以「实施纪要」一节（在文末）为准，两者冲突时以实施纪要为准。**

---

## 0. 先说结论：这与附件里的原始方案有本质区别

拉源码之前拟的方案（附件 pasted.txt）是按"给一个纯 Android App 从零加一个搜索+下载模块"来设计的。实际把仓库克隆下来后发现，情况完全不同，直接照搬会做大量重复劳动，甚至方向性走偏：

| 原方案的假设 | 实际情况 |
|---|---|
| 这是一个 Android-only App，要在 `AndroidManifest.xml` 里处理明文流量 | **这是一个 Kotlin Multiplatform / Compose Multiplatform 项目**，同时构建 Android / iOS / Desktop(Win/Linux/macOS)。`composeApp/src` 下有 `androidMain`、`iosMain`、`jvmMain`、`commonMain` 等多个 source set。明文流量、自签证书信任、DoH 这些网络策略是通过 `expect/actual` 的 `createHttpClient()` 按平台各自实现的，Android 的 cleartext 配置只是其中一环 |
| 要新建"搜索 Tab、结果卡片列表、排序过滤、下载联动 Bottom Sheet" | **这些全部已经存在**。仓库里有完整的 `ui/search/{start,result,plugins}` 模块，是基于 qBittorrent **自带的搜索插件 API**（`search/start`、`search/results`、`search/stop`，对应 Python 搜索插件如 M-Team、Nyaa 等）实现的一套异步轮询式搜索+排序+过滤+一键推送下载的完整闭环 |
| "一键下载联动"要自己设计 Bottom Sheet、区分 magnet/downloadUrl 策略 | **下载联动已经存在且策略已经被 qBittorrent 服务端自己解决了**。现有搜索结果点击下载时调用 `onNavigateToAddTorrent(fileUrl)`，直接跳转到已有的 `AddTorrentScreen`，把 URL（磁力链或种子文件 HTTP 链接）整体作为 `links: List<String>` 传给 qBittorrent 的 `torrents/add`，由 **qBittorrent 服务端自己判断磁力还是种子文件**，客户端不需要任何"降级判断树" |

**结论：这次要做的不是"从零造一个搜索下载模块"，而是"给已有的、成熟的搜索模块新增一路 Prowlarr 数据源，尽可能复用现成的结果列表 UI、排序过滤逻辑和下载联动"。** 工作量比原方案预估的小得多，风险也低得多。

---

## 1. 现状代码梳理（已核实的事实，作为后续设计的地基）

项目结构（`composeApp/src/commonMain/kotlin/dev/bartuzen/qbitcontroller/`）：

```
data/
  SettingsManager.kt          # 全局偏好，multiplatform-settings 的 Settings 包装，preference() 委托
  ServerManager.kt            # 多服务器配置的增删改查，存成 JSON 塞进单独的 "servers" Settings 实例
  repositories/search/
    SearchStartRepository.kt  # 拉取 qBit 搜索插件列表
    SearchResultRepository.kt # startSearch / stopSearch / deleteSearch / getSearchResults
network/
  RequestManager.kt           # 每个 serverId 对应一个 Ktor HttpClient + TorrentService（登录态、超时、DoH、自签证书都在这里）
  TorrentService.kt           # qBittorrent WebAPI 的全部端点定义（get/post 封装）
model/
  ServerConfig.kt             # 单个 qBit 服务器的连接配置（url/用户名/密码/高级选项）
  Search.kt                   # 搜索结果模型 Search.Result(descriptionLink, fileName, fileSize, fileUrl, leechers, seeders, siteUrl)
  StartSearch.kt / Plugin.kt
ui/search/
  start/SearchStartScreen.kt + ViewModel   # 输入关键字、选分类、选插件
  result/SearchResultScreen.kt + ViewModel # 结果列表、排序（名称/大小/做种/吸血/来源站点）、过滤、下拉刷新、点击下载
  plugins/SearchPluginsScreen.kt + ViewModel # 管理/启用 qBit 搜索插件
ui/addtorrent/
  AddTorrentScreen.kt + ViewModel          # 已有的"添加种子"页面，接受 links: List<String>?
di/AppModule.kt                            # Koin 依赖注入，viewModel { (serverId, ...) -> ... } 工厂模式
```

几个对本方案至关重要的实现细节：

1. **搜索是"异步轮询"模型**：`SearchResultViewModel.startSearch()` 调用 qBit 的 `search/start` 拿到 `searchId`，然后每秒轮询 `search/results` 直到 `status == STOPPED`。排序/过滤全部在客户端对 `List<Search.Result>` 做（`SearchResultViewModel` 里的 `sortedResults`/`searchResults`）。
2. **下载联动只有一行代码的成本**：`SearchResultScreen.kt` 里点击下载时 `onNavigateToAddTorrent(dialog.searchResult.fileUrl)` → `SearchNavHost.kt` 里 `navController.navigate(Destination.AddTorrent(serverId, torrentUrl))` → `AddTorrentViewModel.addTorrent(serverId, links = listOf(torrentUrl), ...)`。**只要能拿到一个磁力链或种子直链字符串，剩下的全部复用现成代码。**
3. **每台 qBit 服务器一个独立 `HttpClient`**（`RequestManager.getHttpClient(serverId)`），带 session 登录态、超时、DoH、自签证书信任等配置，这套机制是**为 qBittorrent 的 cookie 登录设计的**，Prowlarr 用的是 `X-Api-Key` 头鉴权，不能直接复用 `RequestManager`/`TorrentService`，需要一个平行的轻量网络客户端。
4. **多平台 Ktor engine 是 `expect/actual`**：`createHttpClient()` 在 `androidMain`(OkHttp) / `jvmMain`(OkHttp) / `iosMain`(Darwin) 里各有实现，Android/Desktop 支持"信任自签证书"和"DNS over HTTPS"，iOS 目前不支持（`supportsSelfSignedCertificates()` 在 iOS 返回 `false`）。新增 Prowlarr 网络客户端要遵守同样的平台差异。
5. **设置存储是"按用途分命名空间"的**：Koin 里 `single<Settings>(named("settings"))` / `named("servers"))` 分别对应两个独立的 `Settings` 实例（Android 上是两个不同的 SharedPreferences 文件，iOS 是两个不同 suite 的 NSUserDefaults，Desktop 是两个不同的 JSON 文件），在 `androidMain/di/AppModule.android.kt`、`desktopMain/di/AppModule.desktop.kt`、`iosMain/di/AppModule.ios.kt` 里各自的 `listOf("settings", "servers", ...)` 里声明。
6. **"测试连接"已有现成模式可抄**：`AddEditServerViewModel` 里的 `testConnection()` 用 `isTesting: StateFlow<Boolean>` + `testJob: Job?` + 调用登录接口 + 通过 `eventChannel` 发送 `TestSuccess`/`TestFailure`，UI 侧监听事件弹 Snackbar。Prowlarr 的连接测试可以照搬这个骨架。

---

## 2. Prowlarr API 关键事实（已核实部分 + 待核实部分）

**已通过官方文档 / SDK 源码核实：**

- 搜索端点：`GET /api/v1/search?query={q}&indexerIds={逗号分隔}&categories={cat}&type={search|tvsearch|moviesearch|...}`
- 鉴权：请求头 `X-Api-Key: {apikey}`（也支持 query 参数 `apikey=`，但官方 SDK 默认走请求头）
- 搜索结果字段（对照 Prowlarr 官方 Go/Python SDK 的结构体，字段名一致）：
  `guid`、`title`、`fileName`、`size`、`indexerId`、`indexer`、`seeders`、`leechers`、`protocol`（`torrent`/`usenet`）、`downloadUrl`、`magnetUrl`、`infoUrl`、`infoHash`、`publishDate`、`categories`、`indexerFlags`
- 官方文档明确写了 Prowlarr 内部生成 GUID 时的取值优先级是 `DownloadUrl → MagnetUrl → InfoUrl`，可以作为客户端"优先用哪个字段发起下载"的参考顺序
- 获取已配置的 indexer 列表：`GET /api/v1/indexer`

**未在本次检索中拿到一手确认、需要开发时用 Prowlarr 自己的 Swagger UI 核实的点：**

- "测试连接"用哪个端点最轻量（候选：`GET /api/v1/indexer` 返回 200 即视为连通；或 Servarr 系列惯用的 `GET /api/v1/system/status`，本方案默认按后者设计，但需要实测确认该端点在 Prowlarr 上确实存在且不需要额外权限）
- `categories` 参数的分类体系（Newznab/Torznab 标准分类号，如 2000=Movies、5000=TV，量比较大）是否需要在 P0 阶段接入，还是先不做分类映射
- 分页参数：官方文档提示 `limit`/`offset` 在部分版本"目前不生效"，需要实测确认当前版本的行为，避免 UI 上设计了无效的分页控件

---

## 3. 目标与分期

### P0（最小可用版本，优先做）
- 新增全局 Prowlarr 连接配置（URL + API Key）+ 测试连接
- 在现有"搜索起始页"新增一个数据源开关：**qBittorrent 插件 搜索** / **Prowlarr 搜索**
- 切到 Prowlarr 时，用 Prowlarr 结果喂给**复用的**结果列表 UI（排序、过滤、点击下载全部沿用现有组件），但因为 Prowlarr 是一次性同步返回而不是轮询式，去掉"停止搜索"相关的状态
- 点击下载复用现有 `AddTorrentScreen` 流程，`magnetUrl ?: downloadUrl` 作为 URL 传入
- 不做的事：不做 Prowlarr 结果与 qBit 插件结果的"合并展示"，不做分类映射，不做 indexer 多选（先用"全部已配置 indexer"）

### P1（增强，P0 验证稳定后再做）
- Prowlarr indexer 多选（对照 `GET /api/v1/indexer` 拉取列表做勾选）
- 搜索起始页支持"两路数据源同时勾选、结果合并展示"，更贴近附件原方案里"即搜即推即看"的一体化体验
- 分类映射（Torznab category ↔️ qBit 插件的 category 下拉框统一成一套 UI）

这样分期的原因：P0 复用度最高、改动面最小、能最快验证"Prowlarr 数据能不能顺利喂进现有 UI、下载联动是否真的零成本复用"这个核心假设；P1 才是原方案里"统一体验"的产品目标，放在 P0 跑通之后风险更可控。

---

## 4. 详细设计

### 4.1 新增数据模型（`commonMain/kotlin/.../model/`）

```kotlin
// model/ProwlarrConfig.kt
@Serializable
data class ProwlarrConfig(
    val url: String,
    val apiKey: String,
    val isEnabled: Boolean = false,
    val trustSelfSignedCertificates: Boolean = false,
)
```

```kotlin
// model/ProwlarrSearchResult.kt —— 对照 Prowlarr /api/v1/search 真实返回字段
@Serializable
data class ProwlarrSearchResult(
    val guid: String,
    val title: String,
    val fileName: String? = null,
    val size: Long? = null,
    val indexerId: Int,
    val indexer: String,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val protocol: String,       // "torrent" | "usenet"
    val downloadUrl: String? = null,
    val magnetUrl: String? = null,
    val infoUrl: String? = null,
)

// 关键设计点：直接映射成已有的 Search.Result，最大化复用现有排序/过滤/下载 UI
fun ProwlarrSearchResult.toSearchResult() = Search.Result(
    descriptionLink = infoUrl ?: "",
    fileName = fileName ?: title,
    fileSize = size,
    fileUrl = downloadUrl ?: magnetUrl ?: infoUrl ?: "",
    leechers = leechers,
    seeders = seeders,
    siteUrl = indexer,
)
```

> 把 `ProwlarrSearchResult` 映射成已有的 `Search.Result`，是本方案里复用度最高的一步棋：`SearchResultViewModel` 里现成的排序（按 `seeders`/`fileSize`/`fileName`/`siteUrl`）、过滤（按做种数区间/大小区间/文件名关键词）、以及点击下载的 `fileUrl` 传递逻辑，**一行都不用改**，只要能拿到一个 `List<Search.Result>` 塞进去就行。

同时按 `protocol == "torrent"` 过滤掉 usenet 结果（附件原方案里提到的这条前置过滤，在这里依然适用且必要，因为 qBittorrent 只能处理 BT）。

### 4.2 网络层：`network/ProwlarrService.kt`（新建，与 `TorrentService` 平级）

Prowlarr 是独立主机、独立鉴权方式，**不接入 `RequestManager` 的 per-server session 逻辑**，而是做一个全局单例的轻量 Ktor 客户端：

```kotlin
class ProwlarrService(
    private val client: HttpClient, // 由平台 expect/actual 的 createHttpClient 构建，复用自签证书/DoH 的处理逻辑
    private val baseUrl: String,
    private val apiKey: String,
) {
    suspend fun search(query: String, indexerIds: List<Int>? = null): Response<List<ProwlarrSearchResult>> =
        get("api/v1/search") {
            parameter("query", query)
            indexerIds?.forEach { parameter("indexerIds", it) }
            parameter("type", "search")
        }

    suspend fun getIndexers(): Response<List<ProwlarrIndexer>> = get("api/v1/indexer")

    // 待核实：system/status 是否是最合适的连通性测试端点
    suspend fun testConnection(): Response<Unit> = get("api/v1/system/status")

    private suspend inline fun <reified T> get(path: String, block: HttpRequestBuilder.() -> Unit = {}) =
        // 复用与 TorrentService 里 get()/post() 相同的 Response<T> 包装 + 错误处理约定
        ...
}
```

网络客户端的构建建议**复用现有的 `createHttpClient()` expect/actual 函数**，而不是另起一套。目前它的签名是 `createHttpClient(serverConfig: ServerConfig, block: ...)`，是绑定在 `ServerConfig` 上的（用来读取该服务器的自签证书/DoH 设置）。两个可选做法：

- **方案 A（推荐，改动小）**：把 `createHttpClient` 的参数从 `ServerConfig` 收窄成一个更通用的接口/data class（例如抽出 `NetworkOptions(trustSelfSignedCertificates, dnsOverHttps)`），`ServerConfig` 和新的 `ProwlarrConfig` 都能提供这个接口。三个平台的 `actual fun createHttpClient` 改动量很小（把 `serverConfig.protocol`/`serverConfig.advanced` 换成 `options.*`）。
- **方案 B（改动更小，但有代码重复）**：给 Prowlarr 单独写一份平台向 `expect/actual createProwlarrHttpClient()`，逻辑基本从现有三份 `createHttpClient` 里抄一份简化版（去掉 cookie/basic auth 部分，只留自签证书信任）。

个人建议方案 A，长期看更干净；如果赶进度可以先方案 B，P1 阶段再收敛。

### 4.3 配置存储：新增 `"prowlarr"` 命名空间

跟随现有 `"settings"`/`"servers"` 的分离约定，不要把 API Key 塞进已有的 `SettingsManager`（那是一堆零散偏好项的扁平列表，混入密钥不合适）。新增一个独立的 `Settings` 命名空间，三个平台各改一行：

```diff
# androidMain/di/AppModule.android.kt
- listOf("settings", "servers", "torrents").forEach { name ->
+ listOf("settings", "servers", "torrents", "prowlarr").forEach { name ->

# desktopMain/di/AppModule.desktop.kt 和 iosMain/di/AppModule.ios.kt 同理
- listOf("settings", "servers").forEach { name ->
+ listOf("settings", "servers", "prowlarr").forEach { name ->
```

然后仿照 `ServerManager.kt` 写一个 `ProwlarrManager.kt`（比 `ServerManager` 简单得多，因为 P0 阶段只存一份配置，不是列表）：

```kotlin
class ProwlarrManager(private val prowlarrSettings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _configFlow = MutableStateFlow(
        prowlarrSettings.getStringOrNull(Keys.Config)?.let { json.decodeFromString<ProwlarrConfig>(it) },
    )
    val configFlow = _configFlow.asStateFlow()

    fun setConfig(config: ProwlarrConfig) {
        prowlarrSettings[Keys.Config] = json.encodeToString(config)
        _configFlow.value = config
    }
}
```

> 安全性说明：这个存法和现有 `ServerConfig.password` 的存法（明文 JSON 塞进 `Settings`）是一致的，**没有引入新的安全短板，但也没有比现状更安全**。如果未来想加固，应该是 `ServerConfig.password` 和 `ProwlarrConfig.apiKey` 一起迁移到系统级 Keystore/Keychain，不建议只单独给 Prowlarr 加密而服务器密码不加密，那样不一致。

### 4.4 Repository 层：`data/repositories/search/ProwlarrSearchRepository.kt`

```kotlin
class ProwlarrSearchRepository(
    private val prowlarrManager: ProwlarrManager,
    // 用于按需构建/复用 ProwlarrService 实例，参考 RequestManager 里 httpClientMap 的懒加载+失效模式
) {
    suspend fun search(query: String, indexerIds: List<Int>? = null): RequestResult<List<Search.Result>> {
        val config = prowlarrManager.configFlow.value ?: return RequestResult.Error.RequestError.Unknown("Prowlarr 未配置")
        // ... 调用 ProwlarrService.search，映射成 Search.Result，过滤掉 protocol == "usenet"
    }
}
```

### 4.5 DI 注册（`di/AppModule.kt`）

```kotlin
single { ProwlarrManager(get(named("prowlarr"))) }
singleOf(::ProwlarrSearchRepository)
viewModel { (serverId: Int) -> ProwlarrSearchViewModel(serverId, get(), get()) }
viewModelOf(::ProwlarrSettingsViewModel)
```

### 4.6 UI/交互设计

**新增设置页**：仿照 `ui/settings/network/`（这是目前最简单的一个设置子页，适合当模板）新建 `ui/settings/prowlarr/ProwlarrSettingsScreen.kt` + `ProwlarrSettingsViewModel.kt`：
- 输入框：Server URL、API Key
- 开关：启用 Prowlarr 搜索
- "测试连接"按钮 —— 直接照抄 `AddEditServerViewModel.testConnection()` 的 `isTesting`/`testJob`/`eventChannel` 骨架
- 入口：在 `ui/settings/SettingsScreen.kt` 里现有的几个入口（服务器/通用/外观/网络）旁边加一个"Prowlarr"入口

**搜索起始页改造**（`ui/search/start/SearchStartScreen.kt`）：
- 顶部加一个两态的分段控制器（Segmented Button）："qBittorrent 插件" / "Prowlarr"，默认记住上次选择（存一个 `SettingsManager.searchSource` 偏好项即可，复用现有 `preference()` 委托模式）
- 选中 Prowlarr 且未配置时，直接引导跳转到 Prowlarr 设置页，而不是显示一个空的插件列表
- P0 阶段不做 indexer 多选，直接用全部已启用 indexer

**结果页复用**：现有 `SearchResultScreen`/`SearchResultViewModel` 是强绑定 qBit 轮询语义的（`startSearch`/`stopSearch`/`deleteSearch`/持续轮询）。给 Prowlarr 走同一个 ViewModel 类会让语义变得混乱（"停止搜索"这个操作对 Prowlarr 没有意义），所以建议：
- 把 `SearchResultScreen.kt` 里**纯展示部分**（结果卡片列表、排序菜单、过滤 Dialog、点击下载的处理）抽成一个不依赖 ViewModel 内部实现的独立 Composable，接收 `results: List<Search.Result>`、`isLoading: Boolean`、`onRefresh`、`onDownloadClick` 等参数
- 新建一个轻量 `ProwlarrSearchViewModel`（只有 `isLoading`/`results`/`refresh()`，没有 `startSearch`/`stopSearch`/轮询），驱动同一个抽出来的展示 Composable
- 这样两条数据源各自的 ViewModel 保持职责单一，UI 层的视觉/交互体验完全一致

**下载联动**：两条路径殊途同归，最终都是 `onNavigateToAddTorrent(result.fileUrl)`，`Destination.AddTorrent` 和 `AddTorrentScreen` 完全不用改。

### 4.7 关键坑点核对（对照附件原方案，标注哪些依然成立、哪些已被现状架构解决）

| 附件原方案提到的坑 | 在当前架构下是否依然成立 |
|---|---|
| qBit 服务器要能访问到 Prowlarr 返回的 downloadUrl（不能是 Prowlarr 自己的内网回环地址） | **依然成立**，而且更明确了：因为下载是 qBittorrent 服务端发起的（`torrents/add` 的 `urls` 参数），如果 Prowlarr 部署在和 qBittorrent 不同网络域，一定要确保 Prowlarr 的 `downloadUrl` 里的主机名/端口从 qBittorrent 所在容器/主机可达。这一条应该写进 Prowlarr 设置页的帮助文案里 |
| Android 明文流量限制 | 只对 **Android target** 成立；iOS 有自己的 ATS（App Transport Security）限制 HTTP，Desktop（OkHttp）默认没有这个限制。需要在 `androidMain` 的 network security config 里对 Prowlarr 常见的局域网网段放行（如果原来已经为 qBittorrent 做过这件事，直接复用同一份 `network_security_config.xml` 即可，不用重复配置） |
| 分页/性能：一次搜索可能命中上千条结果，LazyColumn 要用稳定的 key | 现有 `SearchResultScreen` 已经在用 `LazyColumn` + `key`（对照 `Search.Result` 的字段组合或 `guid`），这条不需要额外处理，直接复用即可 |
| "策略判断树"（优先 magnetUrl，降级 downloadUrl） | **判断逻辑还在，但从"客户端要写 if/else 分支"简化成"映射时按优先级取第一个非空字段"这一行代码**（`downloadUrl ?: magnetUrl ?: infoUrl`），因为下游的 `AddTorrentScreen` 不关心这个字符串具体是磁力链还是种子直链，都是原样传给 qBittorrent 处理 |

---

## 5. 文件改动清单

**新增文件：**

```
commonMain/kotlin/.../model/ProwlarrConfig.kt
commonMain/kotlin/.../model/ProwlarrIndexer.kt
commonMain/kotlin/.../model/ProwlarrSearchResult.kt
commonMain/kotlin/.../network/ProwlarrService.kt
commonMain/kotlin/.../data/ProwlarrManager.kt
commonMain/kotlin/.../data/repositories/search/ProwlarrSearchRepository.kt
commonMain/kotlin/.../ui/search/result/ProwlarrSearchViewModel.kt
commonMain/kotlin/.../ui/settings/prowlarr/ProwlarrSettingsScreen.kt
commonMain/kotlin/.../ui/settings/prowlarr/ProwlarrSettingsViewModel.kt
```

**修改文件：**

```
commonMain/.../di/AppModule.kt                     # 注册上面几个新类
commonMain/.../data/SettingsManager.kt              # 加一个 searchSource 偏好项（枚举：QBIT_PLUGIN / PROWLARR）
commonMain/.../ui/search/start/SearchStartScreen.kt # 加数据源分段控制器
commonMain/.../ui/search/start/SearchStartViewModel.kt
commonMain/.../ui/search/result/SearchResultScreen.kt # 抽出可复用的纯展示 Composable
commonMain/.../ui/search/SearchNavHost.kt           # 加 Prowlarr 搜索结果的路由分支
commonMain/.../ui/settings/SettingsScreen.kt        # 加 Prowlarr 设置入口
commonMain/.../network/RequestManager.kt (或抽出的 NetworkOptions) # 视 4.2 节选择的方案而定
androidMain/.../di/AppModule.android.kt             # 加 "prowlarr" 命名空间
desktopMain/.../di/AppModule.desktop.kt             # 同上
iosMain/.../di/AppModule.ios.kt                     # 同上
```

---

## 6. 分阶段实施步骤与验收标准

**第一步：打通 Prowlarr 连接（不涉及搜索 UI）**
- 完成 `ProwlarrConfig`/`ProwlarrManager`/`ProwlarrService`/Prowlarr 设置页
- 验收：填入真实的 Prowlarr URL + API Key，点"测试连接"能正确返回成功/失败，重启 App 后配置还在

**第二步：跑通一次 Prowlarr 搜索并映射成 `Search.Result`**
- 完成 `ProwlarrSearchRepository`，先不接 UI，写个简单的单元测试或临时按钮验证：给定关键词，能拿到过滤掉 usenet 之后的 `List<Search.Result>`，字段（尤其是 `fileUrl`）符合预期
- 验收：拿几个真实搜索词（含有磁力链结果的、只有种子直链结果的两种）分别验证 `fileUrl` 取值正确

**第三步：接入结果展示 UI + 下载联动**
- 抽出可复用展示 Composable，接入 `ProwlarrSearchViewModel`，打通"点击下载"到 `AddTorrentScreen` 的路由
- 验收：Prowlarr 搜到的结果点下载，qBittorrent 里能看到任务被正确添加（分别验证磁力链结果和种子直链结果两种情况）

**第四步：搜索起始页数据源切换**
- 加分段控制器，未配置 Prowlarr 时的引导跳转
- 验收：两种数据源来回切换，状态不串（比如切回 qBit 插件搜索时不应该还带着 Prowlarr 的过滤条件）

**第五步（P1，可延后）：indexer 多选、结果合并、分类映射**

---

## 7. 待确认事项（Open Questions，建议开发前用 Prowlarr 实例过一遍 Swagger 核实）

1. `testConnection` 用 `system/status` 还是 `indexer` 更合适、返回结构长什么样
2. 当前部署的 Prowlarr 版本上 `limit`/`offset` 分页参数是否生效，如果不生效，一次搜索可能返回全部结果，是否需要客户端自己截断/虚拟分页
3. `categories` 参数（Torznab 分类号）是否要在 P0 就接入，还是先不传（不传时 Prowlarr 默认查全部分类）
4. 是否要支持 Prowlarr 侧配置了 Basic Auth/反向代理鉴权的场景（如果部署方式和 qBittorrent 一样套了 Caddy/Nginx，可能需要复用 `ServerConfig.advanced.customHeaders` 这一套机制）

---

## 8. 实施纪要（Round 1-4，方案变更记录）

> 本节记录实际开发中相对第 0～7 节最初方案的偏离，以及原因。以下描述均对照 `feature/prowlarr-connection` 分支的真实代码，是当前的权威状态。

### 8.1 核心方向调整：不复用 `ui/search/*`，改成完全独立的页面

最初方案（第 4.6 节）设想的是"抽出 `SearchResultScreen` 里纯展示部分的 Composable，两条数据源共用一套 UI"。实际开发时改成了**完全独立、零侵入**的方案：

- 新建 `ui/prowlarr/search/ProwlarrSearchScreen.kt` + `ProwlarrSearchViewModel.kt`，**没有修改 `ui/search/*` 下任何一个文件**
- 结果列表是独立写的一份简化版展示（标题/大小/来源/做种吸血数/下载按钮），没有复用 `SearchResultScreen` 原有的排序菜单、过滤 Dialog、详情弹窗
- 代价：牺牲了一部分 UI 复用（排序/过滤这些交互目前 Prowlarr 页面没有），换来对现有功能**零侵入、可独立回退**——这条分支上出的任何问题都不会牵连到原有的 qBit 插件搜索功能，符合协作文档里"小步提交、可验证的最小单元"的约定
- 入口：Prowlarr 页面作为**可选的第 6 个底部导航 Tab**（`SettingsManager.showProwlarrTab` 控制显隐），追加在 Settings 之后，而不是插入到中间——这样能避开 `MainScreen.kt` 里好几处硬编码的 tab 下标（如 deep link 里的 `selectedTabIndex = 4`），显隐切换不需要改动其他 tab 的下标逻辑

如果后续要做"两条数据源结果合并展示"（原方案 P1），需要重新评估是否值得回头做 UI 层的抽象复用；目前 P0 阶段判断不值得，改动面/风险都更大。

### 8.2 下载方式调整：客户端直传，不依赖 qBit 服务端能访问 Prowlarr

最初方案（第 4.7 节坑点表格第一行）认为"qBit 服务器必须能访问到 Prowlarr 的 `downloadUrl`"这条限制依然成立。实际实现绕开了这个限制：

- **磁力链**（`fileUrl` 以 `magnet:` 开头）：原样作为 `links` 传给 `torrents/add`，和原方案一致，无变化
- **种子直链**（`downloadUrl`，非 magnet）：**改为客户端（手机/桌面）自己先把种子文件下载下来**，拿到字节数组后以文件上传的方式传给 qBittorrent（`AddTorrentRepository.addTorrent` 本来就支持的 `files: List<Pair<String, ByteArray>>?` 参数，无需新增能力）
- 效果：qBittorrent 服务端完全不需要有到 Prowlarr 的网络连通性，只需要客户端这一端同时连得上两边即可。第 4.7 节表格里"downloadUrl 主机名必须从 qBittorrent 所在容器可达"这条注意事项，对**这个实现**已经不成立，但如果部署环境比较特殊（比如客户端和 Prowlarr 不在同一局域网），需要客户端能连到 Prowlarr 这条新约束要记在设置页帮助文案里
- 实现位置：`ProwlarrSearchViewModel.addTorrent()` 判断 `fileUrl` 前缀分流；下载逻辑在 `ProwlarrSearchRepository.downloadTorrentFile()`

### 8.3 设置页新增项（对照第 4.6 节原方案，基本一致，补充了一项）

- `ui/settings/prowlarr/ProwlarrSettingsScreen.kt` + ViewModel：Prowlarr Server URL、API Key、测试连接按钮——与原方案一致
- **新增**（原方案未提及）：设置页最下面加了"在底部导航栏中显示"开关，对应 `SettingsManager.showProwlarrTab`，即改即生效（不需要点保存），用来控制 8.1 节提到的第 6 个 tab 的显隐

### 8.4 当前状态与本轮（继续第 4 轮之后）修的问题

Round 4（把 Prowlarr 页面接入底部导航栏）推送后触发 CI，编译失败，报错见 `build-error.log` 思路 / 用户贴的构建日志。排查后定位到两个独立问题，均已修复并推送到 `feature/prowlarr-connection`：

1. **源码 bug（真正的编译失败原因）**：`ProwlarrSearchScreen.kt` 的 KDoc 里字面写了 `ui/search/*` 这段路径，Kotlin 的块注释支持嵌套，`/*` 被解析成 KDoc 内部又开了一层嵌套注释，嵌套注释在 KDoc 自己的 `*/` 处关闭，但最外层的 `/**` 就再也没闭合，导致解析器把这之后的全部代码都吞成"注释"直到文件末尾——这就是编译器报的 `365:1 Unclosed comment`，以及 `MainScreen.kt` 里 `Unresolved reference 'ProwlarrSearchScreen'`（因为那个符号事实上"不存在"，被注释掉了）的根本原因。修复：把 KDoc 里的 `ui/search/*` 改写成不含 `/*` 序列的表述。
2. **CI 脚本 bug（导致上一次"看起来成功"的构建其实是假的）**：`build-prowlarr-apk.yml` 里 `./gradlew ... | tee build-output.log` 没有开 `pipefail`，导致管道最终的退出码是 `tee` 的（恒为 0），而不是 `gradlew` 真实的失败退出码。核对 Actions 运行记录发现：commit `30d72335`（只改了 workflow、没改任何源码）对应的那次运行，"Build debug APK" 步骤显示 success、耗时和上一次真实失败的构建几乎一样长（~5分52秒），但 `Upload artifact` 实际上传了 0 个文件——说明真实构建其实还是失败的，只是失败信号被吞掉了，日志兜底机制（写 `build-error.log` 回仓库那一步）从未真正触发过。已加 `set -o pipefail`，并给 `actions/upload-artifact` 加了 `if-no-files-found: error` 作为第二道保险。

这两个修复都还没有被 CI 真正验证通过（这个沙盒本身连不上 `google()`/`mavenCentral()`，无法本地跑 Gradle），需要等这一轮推送触发的 CI 跑完确认。
