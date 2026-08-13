# PROGRESS 归档

> 从 `PROGRESS.md` 移出的历史记录，仅供追溯排查问题时参考，不代表当前状态。
> 当前状态与"下一轮接手时先做什么"请看 `PROGRESS.md`。

## Round 5（2026-08-09）：修复编译失败 + CI 自身的一个 bug —— 完整排查记录

用户贴了 CI 失败日志，排查后定位到**两个独立问题**，均已修复并推送：

1. **源码 bug #1（真正的编译失败原因之一）**：`ProwlarrSearchScreen.kt` 的 KDoc 里字面写了
   `ui/search/*` 这段路径。Kotlin 块注释支持嵌套，`/*` 被解析成 KDoc 内部又开了一层嵌套注释，嵌套
   注释在 KDoc 自己的 `*/` 处提前关闭，但最外层的 `/**` 从此再没闭合——解析器把这之后的全部代码吞
   成"注释"直到文件末尾。这就是编译器最初报的 `365:1 Unclosed comment`，以及 `MainScreen.kt` 里
   `Unresolved reference 'ProwlarrSearchScreen'`（符号确实"不存在"，被注释掉了）的根本原因。
   → commit `d9ff295e`，把 KDoc 改写成不含 `/*` 序列的表述。

2. **CI 脚本 bug（导致"看起来成功"的构建其实是假的）**：`.github/workflows/build-prowlarr-apk.yml`
   里 `./gradlew ... | tee build-output.log` 没开 `pipefail`，管道最终退出码是 `tee` 的（恒为 0），
   不是 `gradlew` 真实的失败退出码。核对 Actions 记录发现：commit `30d72335`（只改了 workflow、没改
   任何源码）那次运行，"Build debug APK" 步骤显示 success、耗时和上一次真实失败的构建几乎一样长
   （~5分52秒），但 `Upload artifact` 实际上传了 **0 个文件**——真实构建其实还是失败的，只是失败信号
   被吞掉了，日志兜底机制（失败时把 log 写回仓库那一步）从未真正触发过。
   → commit `3fdf1307`，加了 `set -o pipefail`，并给 `upload-artifact` 加了
   `if-no-files-found: error` 作为第二道保险。这个修复本身也已验证生效：修复后的下一次真实失败
   （见下条 #2）第一次真正把 `build-error.log` 写回了仓库，日志兜底机制现在是可信的。

3. **源码 bug #2（pipefail 修好后，CI 吐出的第一份真实日志揭示的问题）**：
   `ProwlarrSearchScreen.kt` 里两处 `Spacer(modifier = Modifier.height(...))`，文件只 import 了
   `androidx.compose.foundation.layout.size`，漏了 `.height`——`height`/`size`/`fillMaxSize` 这些
   是包级顶层扩展函数，必须显式 import；而同文件里的 `align`/`weight` 之所以没报错，是因为它们是
   `RowScope`/`ColumnScope` 接口自带的成员扩展函数，不需要 import。已顺带排查了
   `ProwlarrSettingsScreen.kt` 和 round 4 改的 `MainScreen.kt`，逐个核对每个 `Modifier.xxx` 调用对
   应的 import，没有发现其他同类问题。
   → commit `e83b126e`（rebase 到 CI 自动提交的 `build-error.log` 之上后为 `d1d1a16d`），补上
   `import androidx.compose.foundation.layout.height`。

**最终结果**：CI 运行 [31291122804](https://github.com/wyvern3000/qBitController-pr/actions/runs/31291122804)（commit `d1d1a16d`）**真正构建成功**，产出了 `qbitcontroller-prowlarr-debug-apk`
artifact（~28.5MB debug APK，free flavor），可以下载装到手机上测试功能了。删除了几次失败构建时 CI
自动写回仓库的 `build-error.log`（已不需要，问题已解决）。

另外顺便更新了 `docs/prowlarr-integration-plan.md`，加了第 8 节「实施纪要」，记录了跟最初方案的三点
方向性偏离（做成独立页面零侵入原搜索功能、下载改客户端直传、设置页加连接配置+显示开关）。

## Round 6（2026-08-09）：产出 P1 详细方案（纯文档，未动代码）—— 完整记录

用户确认 P0 验收通过后提出三个新需求（结果排序/过滤+索引器/分类多选、Prowlarr tab 移到 Search
之后、新增可显隐 tab 设置），写入 `docs/prowlarr-p1-search-ui-and-tabs-plan.md`（本轮只设计不编码）。
讨论中记录的两处待办：`selectedTabIndex` 目前是硬编码下标，tab 显隐/移位后可能错指到另一个 tab（Round
7 第二步已修复）；`ProwlarrIndexer` 的 `capabilities.categories` 字段形状当时仅凭第三方文档猜测，
未核实过真实响应（Round 7/8 期间陆续核实，见下）。

## Round 7（2026-08-09）：P1 前三步（tab 重构/可显隐/移位）完成，CI 全部验证通过 —— 完整记录

按方案第 6 节顺序做了前三步——`indexOfDestination()` 替换硬编码 tab 下标（`83b907ef`）、新增
`SettingsManager.visibleTabs`（`fc1b1bd5`）、Prowlarr tab 移到 Search 之后（`c45c91d7`），顺带在
第二步一并修了 `selectedTabIndex` 因 tab 显隐组合变化可能错指到另一个 tab 的 bug（改成按
`NavHostDestination` 比对而非下标，即 Round 6 记录的待办）。三次 CI 均 `success`。

## Round 8（2026-08-09）：CI 改为按需构建 + P1 第四步（索引器多选）完成 —— 完整记录

**CI 触发方式改动**：用户要求"编译先改为按需编译，不要每次改动一点都编译"——`build-prowlarr-apk.yml`
原来 `push` 到本分支就自动触发（配合协作规范"每个 commit 后立即 push"，导致每个已经验证过的小 commit
都触发一次 ~6 分钟构建）。改成只保留 `workflow_dispatch`，push 频率不变（继续遵守"及时同步防丢失"），
但构建验证只在有意义的检查点手动触发（`gh workflow run build-prowlarr-apk.yml --ref
feature/prowlarr-connection`，或用仓库 Contents API 之外的 Actions API 直接 POST
`.../actions/workflows/build-prowlarr-apk.yml/dispatches`）。已确认改动生效：改动本身的 push 没有
触发自动构建，随后手动 dispatch 两次都正常跑起来了。

**第四步：索引器多选**（`83b907ef`→`c536ddc1` 之后，commit `735b7441` + 修复 `8b3d4508`）——

用户提供了一份真实（但未脱敏）的 `GET /api/v1/indexer` 响应样本，核对后发现：`id`/`name`/`enable`
字段名跟方案文档 2.1 节的猜测一致，但 `capabilities.categories` 是**递归**结构（子分类嵌套在父分类的
`subCategories` 里，不是平铺列表），这是原方案没预料到的。样本里还带着真实站点的 session cookie/JWT，
已在对话里提醒用户轮换，代码/文档里没有落地任何一个真实凭证。

改动：
- 新增 `model/ProwlarrIndexer.kt`：`ProwlarrIndexer` / `ProwlarrIndexerCapabilities` /
  `ProwlarrCategory`（`subCategories: List<ProwlarrCategory>` 递归建模，为第五步分类多选直接复用）
- `ProwlarrService.getIndexers()` + `ProwlarrRepository.getIndexers()` 转发（失败不阻塞搜索，退化为
  不传 `indexerIds`）
- `ProwlarrSearchViewModel`：`indexers`/`isLoadingIndexers` 状态，`loadIndexers()`，`search()` 加
  `indexerIds: List<Int>?` 参数
- `ProwlarrSearchScreen`：可折叠的三态选择器（已启用/全部/自选），复用
  `SearchStartScreen.kt` 的 `RadioButtonWithLabel` 三态模式。**偏离了方案文档一处**：方案写的是
  "FilterChip"，但核对代码发现这个 Material3 组件在本项目里从来没用过，实际的 chip 组件是
  `TagChip`/`CategoryChip`（`TorrentOverviewTab.kt` 的标签选择器在用）——改用后者，跟"核对真实 API
  而不是照抄文档猜测"是同一个原则的延伸

**踩的一个坑**：第一次推送后手动 dispatch 构建，`compileFreeDebugKotlinAndroid` 报错——新增的
`Event.IndexersError` 没有加进 `ProwlarrSearchScreen.kt` 里 `EventEffect` 那个穷尽 `when` 分支，
漏了编译器要求的 exhaustive check。单独一个 commit（`8b3d4508`）修掉，重新 dispatch 构建，
[run 31316463719](https://github.com/wyvern3000/qBitController-pr/actions/runs/31316463719)
`success`。`build-error.log` 已清理。

**用户手动修复的一处 bug**（`cc9fe525` + `29d6e0f4`，非本轮 Claude 改的，特此记录）：
`NavHostDestination.kt` 的 `sealed class NavHostDestination` 本身缺了 `@Serializable` 注解
（每个 `data object` 子类单独标了，但密封类自己没标），会影响 `NavHost` 类型安全导航的多态序列化。
用户本地修正、重新编译、实机验证功能正常后推送。下一轮如果要碰导航相关代码，注意这个类现在的
正确写法是密封类和每个子类都要有 `@Serializable`。

## Round 9（2026-08-09）：P1 第五步（分类多选）完成，真机反馈后修了分类分组的显示 bug —— 完整记录

**第五步：分类多选**（commit `3d229c87`，CI
[31321894534](https://github.com/wyvern3000/qBitController-pr/actions/runs/31321894534) success）——
`ProwlarrService.search()` / `ProwlarrSearchRepository.search()` / `ProwlarrSearchViewModel.search()`
加了 `categories: List<Int>?` 参数，用法跟 `indexerIds` 一样（重复 query 参数，不传 = 查全部分类，
解决了下面"待确认事项"里"categories 参数没接"那条）。`ProwlarrSearchScreen` 新增第二个可折叠区块，
两级展开：大类本身直接可勾选（`CategoryChip`），有子分类时旁边才出现展开箭头。

**偏离方案文档一处**（跟 Round 8 索引器多选那次同一个原则——核对真实数据，不照抄文档）：方案 2.2 节
写的是固定 8 个 Torznab 标准大类（1000 Console ... 8000 Other）作为唯一顶层分组。核对 Round 7 那份
真实索引器样本后发现这样会让 OpenCD（中文音乐站）的分类选择器基本没用——它的音乐流派
（华语流行/古典音乐/...）全挂在 100000+ 的自定义分类号下，不属于任何标准大类。改成从"当前生效的
索引器集合实际上报了什么"动态构建顶层分组（`buildCategoryGroups`），按 id 升序排列。

**真机测试发现的 bug**（用户截图反馈"分类出现错乱了"）：按 id 排序的扁平列表，把 OurBits 的标准
"Movies"（2000，真的有 "Movies/3D" 子分类）跟它自己重复注册的 site-specific "Movies"（100401，
没有子分类）挨在一起显示，样式完全一样——用户看到两个同名 "Movies" chip 中间隔着 "Audio"/"TV"，
以为是渲染错乱了。查了 Torznab/Newznab 规范确认：**id ≥ 100000 就是规范预留给站点自定义分类的范围**
（跟标准 1000-8999 分开就是为了不冲突，不是瞎猜的）。据此把分类列表拆成 "Standard"/"Site-Specific"
两个带标签的分组（`7df171a7`，CI
[31341337943](https://github.com/wyvern3000/qBitController-pr/actions/runs/31341337943) success）——
只是展示层面的分组，实际传给 `search()` 的分类号不变。`CategoryGroupRow` 抽出来复用，两个分组渲染
逻辑不重复。

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
