# PROGRESS

> 给下一轮对话（可能是另一个 Claude 实例）看的进度记录。开工前先跑：
> `git log --oneline -10 && git status`，再对照本文件确认接哪里。
> 协作规范见 Project 知识库里的《代码协作与推送策略》。

## 当前分支

`feature/prowlarr-connection`（未合并到 main，main 保持干净）

## 项目背景

给 qBitController（Kotlin Multiplatform / Compose Multiplatform，Android/iOS/Desktop）加一路
Prowlarr 搜索源。详细方案见 `docs/prowlarr-integration-plan.md`——**第 8 节「实施纪要」是权威现状**，
第 0～7 节是动工前的最初方案，两者冲突以第 8 节为准。

核心结论（详见文档第 8 节）：

1. 做成了**完全独立的页面**（`ui/prowlarr/search/`），零侵入 `ui/search/*` 原有搜索功能，可独立回退
2. 下载改成**客户端直传**：磁力链原样传给 qBit；种子直链由客户端自己下载字节后以文件形式上传给
   qBit，qBit 服务端不需要能访问 Prowlarr
3. 设置页加了 Prowlarr 连接配置；tab 显隐/顺序管理已在 Round 7 迁移到
   `SettingsManager.visibleTabs`（见下），不再是单独的 `showProwlarrTab` 开关

## Rounds 1-4（已完成，功能可用）

| Round | Commit | 内容 |
|---|---|---|
| 1 | `1372e3dc` | 全局 Prowlarr 连接设置（URL/API Key/测试连接），独立 `"prowlarr"` Settings 命名空间 |
| 2 | `bfecfd88` | Prowlarr 搜索 API 对接，结果映射到已有 `Search.Result` 模型 |
| 3 | `0255ac43` | 独立的 `ProwlarrSearchScreen`/`ProwlarrSearchViewModel`，客户端直传下载逻辑 |
| 4 | `04eead7b` | 接入底部导航栏（第 6 个可选 tab），处理了 tab 下标安全性、导航 channel 泄漏等细节 |

Round 4 推送后触发 CI，编译失败（见下）。

## Rounds 5-9（2026-08-09，已完成，完整记录见 `docs/PROGRESS_ARCHIVE.md`）

| Round | 内容 | 关键 commit |
|---|---|---|
| 5 | 修 KDoc 嵌套注释吞文件 + 漏 import 两处编译错误，以及 CI `tee` 吞掉真实退出码的脚本 bug；CI 首次真正构建成功 | 见归档 |
| 6 | P1 方案文档（排序/过滤+索引器/分类多选、tab 移位、tab 可显隐），纯文档未动代码 | — |
| 7 | P1 前三步：tab 下标硬编码改按 `NavHostDestination` 比对、`visibleTabs` 设置、tab 移到 Search 之后 | `83b907ef` `fc1b1bd5` `c45c91d7` |
| 8 | CI 改手动 dispatch；P1 第四步索引器多选，核实 `capabilities.categories` 是递归结构 | `8b3d4508` |
| 9 | P1 第五步分类多选；真机反馈分类分组渲染像错乱，按 Torznab 规范 id≥100000 拆 Standard/Site-Specific 两组修复 | `7df171a7` |

## Round 10（2026-08-10）：P1 第六步（排序/过滤）完成，再次踩中 KDoc 嵌套注释坑 ✅

**第六步：排序/过滤**（commit `fb4fde69`）——`ProwlarrSearchViewModel` 加排序（Seeders/Leechers/Size/
PublishDate，升降序）和过滤（seeds min、keyword 占位）状态，`ProwlarrFilterDialog` 新增筛选面板。首次
dispatch 构建失败：`ProwlarrFilterDialog.kt` 的 KDoc 里又写了一次 `ui/search/*` 这个字面路径——跟 Round 5
踩的完全是同一类坑（Kotlin 块注释嵌套，字面 `/*` 提前闭合导致外层注释吞掉后面所有代码），只是换了个
文件、同一措辞又写了一遍。`d7c816a2` 改写措辞修复，重新 dispatch，[run
31350744421](https://github.com/wyvern3000/qBitController-pr/actions/runs/31350744421) `success`。
P1 六步至此全部完成并至少编译验证过一次。

## Round 11（2026-08-10）：错误详情透出 + 关键字过滤 + 点击跳浏览器，CI 曾失败，已定位修复并验证 ✅

三个独立改动，分三个 commit 分别验证隔离：

- **Commit `484cdebe`**：`getErrorMessage()` 之前对所有 API 错误只显示裸状态码（"API returned an
  error: 500"），看不出具体原因。改成 `ProwlarrService.execute()` 尽力从响应体里提取
  `message`/`error`/`errorMessage`/`description` 字段（对象或数组形态都处理，非 JSON 或像 HTML
  错误页则丢弃，提取失败绝不影响原有状态码判断），新增 `error_api_detail` 字符串在有detail 时展示。
- **Commit `a1cf4646`**：关键字过滤真正接入 `ProwlarrSearchScreen`/`ProwlarrSearchViewModel`（Round 10
  加的只是占位状态）。
- **Commit `bd6e72d2`**：点击搜索结果直接跳转到 tracker 页面（浏览器打开），P1 方案外的追加需求。

**CI 失败排查**（`beeb97b6` 记录的日志，dispatch 对象 `bd6e72d2`）：`compileFreeDebugKotlinAndroid`
报 `StringsHelper.kt:211:30 Unresolved reference 'error_api_detail'`。定位：Compose Resources 把每个
`Res.string.X` 生成成**扩展属性**而非成员，Kotlin 解析扩展属性必须显式 import 才能生效——文件里其余
~50 个 `error_*`/`date_*`/`eta_*` 引用都各自有一行 import，唯独 `484cdebe` 加的 `error_api_detail`
用法忘了加对应 import。`strings.xml` 里键本身一直是对的，`generateComposeResClass` 也正常跑了，纯粹是
少了一行 import，不是资源生成器的问题。`2b92059f` 补上，重新 dispatch，[run
31357762028](https://github.com/wyvern3000/qBitController-pr/actions/runs/31357762028) `success`——
三个功能改动至此才第一次全部一起真正编译通过。

## Round 12（2026-08-10）：下载默认参数 + 分类专属路由——方案先审批后实施，CI 验证通过 ✅

用户追加需求：Prowlarr 下载目前全部走服务端默认（保存路径/顺序下载/首尾优先/做种策略等全部不传），
不想每次弹窗配置，至少要有设置页默认参数，理想情况按分类分流（movie 去一处，music 去另一处）。

**设计先行**：写了 `docs/prowlarr-download-defaults-plan.md`，用户审批确定两点范围（分类路由只覆盖
保存路径/分类/标签，不覆盖限速/做种策略等全部字段；设置页用纯文本框，不做按服务器自动补全）。这也
正式废弃了 P1 方案文档 2.4 节"点下载跳转 AddTorrentScreen"的方向（跟"不想弹窗"直接冲突，从未实施）。

**实施（4 个 commit）**：
- 重构（`fc2ef04a`，纯移动零行为变化）：把搜索页的分类多选 UI（`CategoryGroup`/`buildCategoryGroups`/
  `CategorySelectionSection` 等）提取到 `ui/prowlarr/ProwlarrCategoryPicker.kt`，供设置页复用
- 数据层（`0a57797a`）：新模型 `ProwlarrDownloadDefaults`（全局默认，字段照抄
  `AddTorrentRepository.addTorrent()` 参数列表）+ `ProwlarrCategoryRoute`（名字+分类 id 列表+可选
  覆盖保存路径/分类/标签）、`SettingsManager` 两个新 `jsonPreference`、`Search.Result`/
  `ProwlarrSearchResult` 补上 `categories` 字段（真实 API 一直有这个字段，之前没接进模型）、
  `ProwlarrSearchViewModel.addTorrent()` 改成用 `resolveProwlarrDownloadRouting()` 解析出的参数，
  替换掉原来全部硬编码 null/false
- 设置页 UI（`402d7b0a`）：新增 `ProwlarrDownloadDefaultsScreen`，"默认参数"表单（全部字段）+"分类
  专属路由"列表（增/删/改/上下移动排序，分类多选复用上面提取的组件），从 Prowlarr 设置页加了个入口
  按钮
- 修复（`0dab06f1`）：CI 报 `Unresolved reference 'ExposedDropdownMenu'`——这次反过来，是我自己加了
  一行不存在的 import。`ExposedDropdownMenu` 在这个 Material3 版本里不是顶层 composable，是
  `ExposedDropdownMenuBoxScope` 的成员函数，在 `ExposedDropdownMenuBox { ... }` 的 trailing lambda
  里直接调用靠隐式 receiver 解析，完全不需要 import（跟 `.menuAnchor(...)` 是同一类特例，
  AddTorrentScreen 调用四次 `.menuAnchor` 全都没有对应 import）。删掉这行多余的 import 即可，
  `EnumDropdown` 的调用结构本身跟 AddTorrentScreen 已有的四处用法完全一致。重新 dispatch，[run
  31464305653](https://github.com/wyvern3000/qBitController-pr/actions/runs/31464305653) `success`。

## Round 13（2026-08-11）：真机搜索首次实测，连续两次 categories 字段解析崩溃，已修复 ✅

Round 12 编译通过后用户第一次真机实测搜索功能（此前所有 CI 验证都只到"编译过/UI 渲染出来了"，从没
真正跑过一次搜索），连续暴露两层同一个字段的解析错误：

- **第一次**（`1a753cf9`，非本轮对话内完成，上一轮已修）：`search()` 返回体的 `categories` 是对象数组
  `{"id": 3000, ...}`，不是方案文档假设的 `List<Int>`，`JsonConvertException: Expected numeric
  literal`。改成复用 `ProwlarrCategory`（跟 `capabilities.categories` 同构）。CI
  [31467904289](https://github.com/wyvern3000/qBitController-pr/actions/runs/31467904289) `success`，
  但用户马上真机复测又炸了——第二次。
- **第二次**（本轮，`1086baed`）：复用 `ProwlarrCategory` 假设每个分类对象都有 `name`（`capabilities.
  categories` 里 Round 7 确认过确实一直有），但搜索结果里的分类对象不是——`JsonConvertException: Field
  'name' is required ... missing at path: $[0].categories[1]`（用户截图）。说明这两个端点返回的
  "categories" 字段虽然字段名一样，形状并不完全一致，不能假设结果端的分类对象一定携带 `name`。改成
  新增一个只声明 `id` 的最小类型 `ProwlarrResultCategory`，不再复用 `ProwlarrCategory`——`toSearchResult()`
  本来就只取 `.id` 用于分类路由，不需要 `name`/`subCategories`，`ignoreUnknownKeys` 会丢掉其余字段。
  CI [31469128530](https://github.com/wyvern3000/qBitController-pr/actions/runs/31469128530)
  `success`，Artifacts API 确认产出了真实 30MB APK。

**教训**：`capabilities.categories`（`/api/v1/indexer`）和搜索结果里的 `categories`
（`/api/v1/search`）虽然字段名相同、单个分类对象的基本形状也相似，但**不能假设两个不同端点返回的
"同名字段"结构完全一致**——这次连续两轮真机测试才把搜索结果这边的真实形状摸清楚（先是发现是对象不是
纯 id，再发现对象里 `name` 不保证存在）。以后遇到"名字相同、大概率结构类似"的字段，优先直接找一份该
**具体端点**的真实响应样本核对，而不是复用另一个端点已核实过的模型。

## 下一轮接手时先做什么

1. **Round 13 修的是"搜索直接崩溃"（categories 字段解析），不是功能性验收**——现在搜索至少能真的
   跑出结果列表了（此前从未成功跑过一次），但索引器多选/分类多选/排序/seeds min 过滤/关键字过滤/
   点击跳浏览器/错误详情文案/下载默认参数/分类路由这些具体功能是否真的按预期生效，仍然一条都没
   实机验证过——**优先级高于继续往下做新功能**。另外 Round 13 改了 `ProwlarrSearchResult.categories`
   的解析方式（现在只取 `id`），下载分类路由（`resolveProwlarrDownloadRouting()`）吃的就是这批 id，
   值得重点复核一遍分类路由是否还能正确命中
2. 分类选择器目前对着 CJK 短标签测试过，但"Standard"/"Site-Specific" 这两个分组标题以及 8 个标准
   Torznab 大类名（Movies/TV/Audio/...）都还是硬编码英文，没有走 strings.xml 之外的本地化路径——
   目前判断这是合理的（协议层面的分类名，不是面向用户的文案，参照 indexer.name 本身也不本地化），
   但如果用户觉得别扭需要反馈
3. P0 验收（APK 实机测试）如果还没做完，优先级高于继续往下做 P1/追加需求
4. 所有功能性验收都做完后，按方案第 8 节建议，把结论合并进 `docs/prowlarr-integration-plan.md`
   的"实施纪要"一节，避免文档长期并存（现在是三份：P0 主方案、P1 方案、下载默认参数方案）
5. **写 KDoc/注释时如果要提到形如 `xxx/*` 这样以 `/*` 结尾的路径或通配符，务必改写措辞避开字面的
   `/*` 序列**——Kotlin 块注释支持嵌套，字面 `/*` 会被解析成新的嵌套层，导致注释自己的 `*/` 只关闭了
   这个意外嵌套层，外层注释从此不再闭合、把后面所有代码吞成注释直到文件末尾。同一措辞（`ui/search/*`）
   已经在 Round 5 和 Round 10 各踩了一次，写完新注释后可以用脚本统计整份文件 `/*`/`*/` 出现次数是否
   配平（深度归零）来自查，别只靠肉眼扫。
6. **新增 `Res.string.X`/`Res.plurals.X` 用法时记得同时加一行对应 import**——这个项目里 `Res.string.X`
   是扩展属性不是成员，不 import 就是编译期 `Unresolved reference`（Round 11 踩了一次，`error_api_detail`
   加了用法忘加 import）。改完 `StringsHelper.kt` 这类文件后，可以简单 grep 一下 `Res.string.`/
   `Res.plurals.` 用到的每个名字是否都在文件顶部有对应 import 行，别只肉眼扫一长串 import 列表。
7. **反过来的坑：`ExposedDropdownMenuBoxScope`（`ExposedDropdownMenu`、`.menuAnchor(...)` 等）是
   receiver 成员函数，不是顶层 composable，不需要也不能 import**——只在直接嵌套于
   `ExposedDropdownMenuBox { ... }` lambda 内调用时才通过隐式 receiver 解析，Round 12 因为多加了一行
   不存在的 `import androidx.compose.material3.ExposedDropdownMenu` 而编译失败。跟第 6 条正好相反：
   不是"用了忘 import"，而是"不该 import 的东西手动加了 import"。写新的
   `ExposedDropdownMenuBox`/`ExposedDropdownMenu` 用法前，直接照抄 `AddTorrentScreen.kt` 里现成的四处
   写法，不要凭直觉补 import。

## 待确认事项（继承自原方案第 7 节，尚未处理）

- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
