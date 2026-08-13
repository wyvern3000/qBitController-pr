# Prowlarr P2：首次真机试用反馈（方案）

> 背景：P1 六步 + 下载默认参数/分类路由（round 12-13）合入后，用户第一次装 debug APK 实际用了一轮，
> 给了 5 条修改意见。本文档记录这 5 条的方案，实施完成后结论同样按惯例合并回
> `docs/prowlarr-integration-plan.md` 第 8 节，本文档可归档。

## 反馈原文（编号供后续 commit message 引用）

1. Prowlarr 菜单里 "Enable Prowlarr Search" 开关语义不清，配置保存后本来就该默认启用，去掉
2. "Configure download defaults" 入口应挪到 Settings 主页，独立一项，改名 "Prowlarr Download Defaults"
3. 下载默认参数 + 分类路由，第一项应该选"下载器"（对应 app 里的 servers）——用户会加很多个 qBit
   server，不选默认下到哪去？
4. 搜索结果过滤器要加"索引器标志"过滤（Freeleech/Halfleech 等优惠标志）
5. 下载按钮：单击=按默认配置直接下载（现状不变）；长按=手动模式，弹出下载询问窗口，问下载
   server、以及 app 原有 add torrent 窗口的各项设置（种子 url/文件选取不需要，来源已知）

## 1. 去掉 Enable 开关

`ProwlarrConfig.isEnabled` 目前只在 `ProwlarrSettingsScreen.kt` 里读写一处 `SwitchPreference`，没有任何
地方用它做门控（Prowlarr tab 的显隐是独立的 `visibleTabs`/`OptionalTab` 机制，不经过这个字段）——
确认过 `grep isEnabled` 全仓库只有 model 定义 + 这一个界面用到。直接删字段 + 删开关。

`SettingsManager.prowlarrConfig` 走 `jsonPreference`（kotlinx.serialization，非严格模式），删除一个有
default 值的字段对已保存的旧配置向前兼容，无需迁移。

## 2. Download Defaults 入口挪到 Settings 主页

`SettingsScreen.kt` 加一条 `Preference`（图标暂定 `Icons.Filled.Rule`，跟已有的
Dns/Settings/Palette/Public/TravelExplore 风格一致），直接导航到已存在的
`Destination.Settings.ProwlarrDownloadDefaults`（这个路由本来就在 `SettingsNavHost.kt` 里，不用新增）。
`ProwlarrSettingsScreen.kt` 里原来的 "Configure download defaults" 按钮 + `onNavigateToDownloadDefaults`
参数一并删掉。

新字符串 `settings_category_prowlarr_download_defaults` = "Prowlarr Download Defaults"（复用已有的
`prowlarr_download_defaults_title` 会导致改标题时要同时改菜单项文案和页面标题两处语义耦合，拆开更
干净）。

## 3. 默认参数 / 分类路由 加"下载器"（server）选择

**模型改动**：
- `ProwlarrDownloadDefaults` 加 `serverId: Int?`（放第一个字段，null = 未设置全局默认，退回当前 app
  正在浏览的 server）
- `ProwlarrCategoryRoute` 加 `serverId: Int?`（同样第一个字段，null = 不覆盖，继续用
  `ProwlarrDownloadDefaults.serverId`）

**解析函数**：`resolveProwlarrDownloadRouting()` 现在还要解出 serverId，返回类型从 `Triple` 换成一个
具名 data class（`ProwlarrResolvedDownloadRouting(serverId, savePath, category, tags)`），优先级
`route.serverId ?: defaults.serverId`——这一层不知道"当前 app 打开的 server"是谁，那是调用方
(`ProwlarrSearchViewModel`) 的兜底，不下沉进这个纯函数。

**最终兜底顺序**（`ProwlarrSearchViewModel.addTorrent`）：路由/默认参数里配的 server → 都没配就退回
调用方传入的"当前 app 选中的 server"（即原来唯一的行为）→ 两者都没有就是真的没法下载，发
`Event.NoServerAvailable`，文案沿用现有 `prowlarr_search_no_server_selected`（"先选一个 server"这句
话依然准确，只是现在有更多途径能满足它）。

这也顺带解决了现在下载按钮 `enabled = serverId != null` 的问题：以后每条结果的实际落点 server 可能
因为分类路由不同而不同，没法在渲染时统一判定"能不能点"，所以改成**按钮总是可点**（除了正在
addTorrent 的 loading 态），点击时才解析、解析失败才提示——原来 `isAddEnabled` 的语义收窄成
`!isAdding`。

**设置页 UI**：两处都加一个 server 下拉（复用 `ExposedDropdownMenuBox`，选项是
`ServerManager.serversFlow` 里每个 server 的 `displayName`，外加一项"跟随 app 当前选中的
server"/"不覆盖，用全局默认"对应 null）：
- `ProwlarrDownloadDefaultsScreen` 主表单第一项
- `CategoryRouteDialog`（新增/编辑分类路由弹窗）第一项

`ProwlarrDownloadDefaultsViewModel` 加 `ServerManager` 依赖（Koin 已经把它注册成
`single { ServerManager(...) }`，`viewModelOf(::ProwlarrDownloadDefaultsViewModel)` 走构造器自动装配，
不用改 DI 模块）。

**范围收紧**：这条反馈只要求加 server 选择，不要求把 savePath/category 从纯文本框换成"查这个
server 真实分类列表"的下拉（round 12 计划文档第 7 节当时明确权衡过、放弃过这个方向，理由是
"这些默认值要在任意 server 上都讲得通"）。现在每条路由/默认参数确实绑定了一个具体 server，那条
"跟 server 无关"的理由不再成立，但用户这次没有提出要连带做这个，本轮不做，只在
`ProwlarrDownloadDefaultsScreen.kt` 顶部 KDoc 里更新这句已经不成立的假设，避免误导下一轮。

## 4. 索引器标志（Freeleech/Halfleech）过滤

**未验证风险**（比照 round 13 的教训明确写在这里）：Prowlarr `/api/v1/search` 单条结果的
`indexerFlags` 字段名/取值是根据第三方文档 + 常见 Torznab/Newznab 实现推断的（预期是字符串数组，
形如 `["freeleech"]`），不是已核实的真实响应样本——跟 round 13 两次踩坑的 `categories` 字段是
同一个"没有第一方 spec"的处境。所以：
- 字段声明成 `List<String>? = null`（宁可拿不到也不能崩），`ignoreUnknownKeys` 已经是全局配置
- 这条明确记入 PROGRESS.md 待验证清单，跟"分页 limit/offset"、"Basic Auth" 那两条并列

**模型改动**：
- `ProwlarrSearchResult.indexerFlags: List<String>? = null`
- `Search.Result.indexerFlags: List<String> = emptyList()`（无 `@SerialName`，写法照抄
  `categories` 那个字段的先例——qBit 自己的搜索插件结果永远给空列表）
- `toSearchResult()` 里 `indexerFlags = indexerFlags ?: emptyList()`

**过滤器**：`ProwlarrSearchViewModel.Filter` 加 `flags: List<String> = emptyList()`。选中多个标志时
用"任一命中"（OR）语义，不是"全部命中"（AND）——Freeleech/Halfleech 这类站点优惠通常互斥（一个
资源只会占其中一种），要求同时满足没有意义，跟分类多选的"或"语义保持一致。

**UI**：`ProwlarrFilterDialog` 新增一节（结构照抄现有 Keyword/Indexer 两节：icon + 标题 + 内容），
可选标志列表**从当前 `rawResults` 实际出现过的值动态生成**（`rawResults.flatMap{it.indexerFlags}
.distinct().sorted()`），不是写死的 Torznab 标准列表——延续 round 9 处理分类分组时确认过的原则
（"真实 tracker 数据驱动 UI，而不是 spec 假设"），也避免因为字段本身就未核实、再去猜一份"标准
标志名列表"造成双重不确定。用 `TagChip` 多选（同一组件，和分类/标签选择器视觉一致）。

结果卡片（`ProwlarrSearchResultItem`）上也顺带把非空的 flags 显示成小 chip（不加会导致这个筛选
维度对用户来说毫无提示——加了什么筛选条件都不知道从哪判断）。

## 5. 长按下载 = 手动模式

**交互**：`ProwlarrSearchResultItem` 现在的下载 `IconButton(onClick = onDownloadClick)`
换成一个自建的 `Box + Modifier.combinedClickable(onClick, onLongClick)`（`IconButton` 本身不支持
`onLongClick`）——`combinedClickable` 这个长按+单击组合本身在 `SearchResultScreen.kt` 已有先例（用于
进入多选模式），不是这个仓库第一次用。单击维持原来"按默认配置直接下载"的行为完全不变；长按
触发新的手动下载弹窗。

**为什么不直接复用 `AddTorrentScreen`**：这个屏幕的 URL 模式是把链接原样交给 qBittorrent 服务端去
抓（`urls` 表单字段），这正是 round 3 特意放弃、改成"客户端自己下载种子文件字节再以文件形式
上传"的方案（理由见 `ProwlarrSearchViewModel.addTorrent` 现有 KDoc：qBit 服务端不需要能访问到
Prowlarr）。如果把 `downloadUrl` 直接塞进 `AddTorrentScreen` 的 URL 框，等于让 qBit 服务端反向去抓
Prowlarr 的直链，悄悄推翻这个已经权衡过的设计——磁力链接倒是没问题（两条路径殊途同归，都是把
magnet 原样当 link 传给 qBit），但 `downloadUrl` 不行，所以不能通过预填 `Destination.AddTorrent`
的 `torrentUrl`/`torrentFileUris` 来复用整个屏幕。

**方案**：`ui/prowlarr/search/` 下新建一个专用的手动下载弹窗（`ProwlarrManualAddDialog.kt`），走
`androidx.compose.ui.window.Dialog`（`usePlatformDefaultWidth = false`）铺满屏幕、内部套
`Scaffold`——跟 `AddTorrentScreen` 一样是"全屏"体量的表单，只是用 Dialog 承载而非导航到新
destination，改动范围收在 prowlarr 目录内，不碰 `MainScreen.kt`/`NavHostDestination.kt`。

字段来源：不是重新发明，是把 `AddTorrentScreen.kt` 里源选择（URL/文件）之外的那些设置项照抄一遍
（server 下拉、分类 chip、标签 chip、保存路径、种子名、stop condition/content layout/autoTMM 三个
下拉、上传下载限速、分享率限制、做种时间限制、暂停/跳过校验/顺序下载/优先首尾片段四个复选框），
数据来源同样是 `AddTorrentRepository.getCategories/getTags/getDefaultSavePath`（`ProwlarrSearchViewModel`
已经注入了 `AddTorrentRepository`，不用新增依赖；只需要新增 `ServerManager` 依赖来拿 server 列表，
同第 3 节，Koin 自动装配）。

**明确不做**（控制范围，理由与 round 12 的"不做保存路径自动补全"是同一类权衡）：
- 保存路径的目录自动补全建议（`AddTorrentViewModel.directorySuggestions` 那一套）——先做成纯文本框
- 种子文件多选批量下载（用户这条反馈只提到单条结果的长按，批量长按多选是"待办"里已经写好、还
  没排期的另一件事，不混进这一轮）

**预填值**：弹出时用"自动模式会用的那一套"（`resolveProwlarrDownloadRouting` 解析出的
server/savePath/category/tags + `ProwlarrDownloadDefaults` 的其余字段）作为表单初始值，用户在这个
基础上改——"手动模式"的意思是"我要看一眼、必要时改"，不是"从空白表单重新填一遍"，且这些解析
逻辑已经现成可以直接复用，不额外增加代码量。

**提交时的下载机制**：跟自动路径完全一致——磁力链直接当 link 传，非磁力先
`prowlarrSearchRepository.downloadTorrentFile()` 拿字节再以文件形式上传——只是 server/savePath/
category/tags/... 这些参数来自弹窗里用户确认后的值，不是 `resolveProwlarrDownloadRouting()` 的结果。
`ProwlarrSearchViewModel` 新增 `addTorrentManual(serverId, searchResult, options)`，和现有私有
`addTorrent(serverId, links, files, categories)` 复用同一个下载分支逻辑（那个函数不用改，两个公开
入口都调它）。

## 实施顺序（对应 commit 划分，非强制但作为默认计划）

1. 反馈 1：去掉 Enable 开关
2. 反馈 2：Download Defaults 入口挪到 Settings 主页
3. 反馈 3：模型加 serverId 字段 + 解析函数改造（纯逻辑，不涉及 UI）
4. 反馈 3：两处设置页 UI 加 server 下拉
5. 反馈 3：`ProwlarrSearchViewModel`/`ProwlarrSearchScreen` 接入新的兜底顺序，下载按钮 enabled 逻辑
   调整
6. 反馈 4：模型加 indexerFlags + 过滤器逻辑
7. 反馈 4：过滤弹窗 UI + 结果卡片 flag chip
8. 反馈 5：长按手势
9. 反馈 5：`ProwlarrManualAddDialog` 骨架（server + 分类/标签 + 保存路径）
10. 反馈 5：`ProwlarrManualAddDialog` 剩余字段（限速/分享率/做种时间/复选框/下拉）+ 接入提交逻辑

每步一个 commit，完成后立即 push。第 5 步左右和第 10 步结束后各 dispatch 一次 CI 作为检查点
（参照 round 12 的节奏：攒够几个有实际编译面的改动再 dispatch，不是每个 commit 都点一次）。
