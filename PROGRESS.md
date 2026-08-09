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

## Round 5（2026-08-09）：修复编译失败 + CI 自身的一个 bug —— 已构建成功 ✅

排查出两处源码 bug（KDoc 嵌套注释意外把整个文件注释掉；`Spacer(Modifier.height(...))` 漏了
`.height` 的 import）和一处 CI 脚本 bug（`tee` 吞掉了 `gradlew` 的真实失败退出码，导致构建失败时
Actions 却显示 success）。三处都已修复，CI 运行
[31291122804](https://github.com/wyvern3000/qBitController-pr/actions/runs/31291122804) 真正构建
成功，产出 debug APK。**完整排查记录（每个 bug 的根因分析、涉及的 commit）已归档到
`docs/PROGRESS_ARCHIVE.md`**，这里不再重复。

## Round 6（本轮，2026-08-09）：P0 验收通过，产出 P1 详细方案（纯文档，未动代码）

用户确认 P0 已验收成功，提出三个新需求，均已写入
`docs/prowlarr-p1-search-ui-and-tabs-plan.md`（该文档待第 4 节引用的字段核实后即可按第 6 节的六步
顺序开工，本轮**只设计、不编码**）：

1. Prowlarr 搜索页参照 Prowlarr 自身界面重做：索引器多选、分类多选（Torznab 大类）、结果排序/过滤
2. Prowlarr 底部导航 tab 从"追加在最后"改为"排在搜索 tab 之后"——这要求先把 `MainScreen.kt` 里
   round 4 故意写死的 7 处 tab 下标字面量重构成按 `NavHostDestination` 运行时查找（原来"追加在末
   尾"就是为了避免这层重构，见方案第 3.1 节）
3. 外观设置新增"页签显示"勾选组（默认全选，可隐藏 搜索/RSS/日志，种子/设置强制常显）——需要新增
   `SettingsManager.visibleTabs`，并把现有 `showProwlarrTab` 迁移并入（方案第 4.3 节），避免两处
   分别维护 Prowlarr tab 显隐状态

方案文档里第 3.3 节额外发现一个现有代码没处理过的边界情况：`selectedTabIndex` 靠
`rememberSaveable` 跨重启保存的是**下标**而不是**具体 tab**，一旦 tabs 顺序/显隐组合发生变化，可能
出现"下标没越界、但指向了另一个 tab"的隐蔽错位，现有 `LaunchedEffect(tabs)` 的越界检查覆盖不到这
种情况，第 3.3 节给出了修复方向（改成按 destination 比对）。

方案第 2.1 节的 `ProwlarrIndexer`/`ProwlarrIndexerCapabilities` 数据模型是根据 Prowlarr 官方文档
推测的字段结构，**没有**真实 Prowlarr 实例可以核对（沙盒连不上外网 Prowlarr 服务），第 7 节"待确认
事项"第 1 条已标注：开工第一步（对应方案第 6 节"第四步"）必须先用真实 `GET /api/v1/indexer` 响应
核对字段名，不能直接假设文档写的就是对的。

## Round 7（2026-08-09）：P1 前三步已完成，CI 全部验证通过 ✅

用户直接说"按这个文档开始实施"（没有先走 Round 6 计划的"找用户确认第 7 节六条待确认事项"流程）。
核对后发现第 7 节六条里第 2、3 条其实方案正文已有结论（子分类"需要支持"、记忆勾选状态"本轮先不做"），
真正阻塞的只有第 1 条（真实索引器字段结构），而且只影响第四步，跟前三步（纯 tab/设置重构，不碰
Prowlarr API）无关——于是直接按方案第 6 节顺序开工做了前三步，每步单独 commit + push，CI
（`build-prowlarr-apk.yml`）全部验证通过：

| 步骤 | Commit | 内容 | CI |
|---|---|---|---|
| 一 | `83b907ef` | `indexOfDestination()` 替换 `MainScreen.kt` 里 7 处硬编码 tab 下标，行为不变 | ✅ success |
| 二 | `fc1b1bd5` | `SettingsManager.visibleTabs`（新增 `OptionalTab` 枚举，迁移旧 `showProwlarrTab`）+ 外观设置页新增"Visible tabs"勾选组 + Prowlarr 设置页旧开关换成跳转按钮 | ✅ success |
| 三 | `c45c91d7` | `buildList{}` 里 Prowlarr 插入位置从末尾移到 Search 之后，默认顺序变为 Torrents/Search/Prowlarr/Rss/Logs/Settings | ✅ success |

**顺带修了方案 3.3 节的 `selectedTabIndex` 错位 bug**，但没有按方案原计划放在第三步——这个 bug 其实
在第二步（任意可选 tab 可独立隐藏，不只是 Prowlarr 一个）就已经会触发（例如隐藏 RSS 时若当前选中
Logs，原先只做越界检查的 `LaunchedEffect(tabs)` 会让下标错误指向 Settings），不是非要等到第三步
Prowlarr 移位才暴露，所以提前在第二步一并修掉：把持久化状态从 `selectedTabIndex: Int` 换成
`selectedDestination: NavHostDestination`（`rememberSaveable` + `jsonSaver()`），`selectedTabIndex`
变成 `tabs.indexOfDestination(selectedDestination)` 派生的只读值，tab 被隐藏后自动退回 Torrents，
不会再指错。第三步因此不需要额外处理这个问题，diff 纯粹是一处 if 块搬家。

沙盒依然没有 Gradle 本地编译能力（无 Maven/Google 仓库访问），三步都是手动核对 API 签名（`Preference`
的 getter/setter、`PaddingValues`、`Settings.hasKey()` 等）后推送，靠 CI 远程验证——三次运行都是
`success`，没有出现过编译错误。

## Round 8（2026-08-09）：CI 改为按需构建 + P1 第四步（索引器多选）完成 ✅

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

## Round 9（2026-08-09）：P1 第五步（分类多选）完成，真机反馈后修了分类分组的显示 bug ✅

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

## 下一轮接手时先做什么

1. 按方案第 6 节继续：**第六步（排序/过滤）**，完成后手动 dispatch 一次 `build-prowlarr-apk.yml`
   验证（不要每个中间 commit 都触发构建，见 Round 8）
2. 索引器多选（第四步）和分类多选（第五步）都还没有真正意义上的"搜索结果确实被过滤对了"的实机验证——
   Round 9 的截图只验证了 UI 渲染本身（勾选、展开、显示分组），没有验证"只勾 OurBits + 只选 Movies
   分类后，搜出来的结果确实只来自 OurBits 的电影分类"这条功能性验收标准，需要用户实测一遍
3. 分类选择器目前对着 CJK 短标签测试过，但"Standard"/"Site-Specific" 这两个分组标题以及 8 个标准
   Torznab 大类名（Movies/TV/Audio/...）都还是硬编码英文，没有走 strings.xml 之外的本地化路径——
   目前判断这是合理的（协议层面的分类名，不是面向用户的文案，参照 indexer.name 本身也不本地化），
   但如果用户觉得别扭需要反馈
4. 方案第 2.4 节"下载目的地重新设计"仍然**没有**列进第 6 节六步，是否要补第七步，需要用户确认
5. P0 验收（APK 实机测试）如果还没做完，优先级高于继续往下做 P1 新步骤
6. 完成 P1 全部步骤后，按方案第 8 节建议，把结论合并进 `docs/prowlarr-integration-plan.md` 的
   "实施纪要"一节，避免两份方案文档长期并存

## 待确认事项（继承自原方案第 7 节，尚未处理）

- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
