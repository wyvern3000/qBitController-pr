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

## Round 6（2026-08-09）：产出 P1 详细方案（纯文档，未动代码）

用户确认 P0 验收通过后提出三个新需求（结果排序/过滤+索引器/分类多选、Prowlarr tab 移到 Search
之后、新增可显隐 tab 设置），写入 `docs/prowlarr-p1-search-ui-and-tabs-plan.md`（本轮只设计不编码）。
完整讨论内容（`selectedTabIndex` 下标错位隐患、`ProwlarrIndexer` 字段待真实核实等）已归档到
`docs/PROGRESS_ARCHIVE.md`。

## Round 7（2026-08-09）：P1 前三步（tab 重构/可显隐/移位）完成，CI 全部验证通过 ✅

按方案第 6 节顺序做了前三步——`indexOfDestination()` 替换硬编码 tab 下标（`83b907ef`）、新增
`SettingsManager.visibleTabs`（`fc1b1bd5`）、Prowlarr tab 移到 Search 之后（`c45c91d7`），顺带在
第二步一并修了 `selectedTabIndex` 因 tab 显隐组合变化可能错指到另一个 tab 的 bug（改成按
`NavHostDestination` 比对而非下标）。三次 CI 均 `success`。完整讨论内容已归档到
`docs/PROGRESS_ARCHIVE.md`。

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

1. **P1 六步已全部完成**（第六步排序/过滤见 Round 10）。索引器多选（第四步）、分类多选（第五步）、
   排序/过滤（第六步）都还没有真正意义上的"功能确实生效"的实机验证——目前为止的截图/CI 只验证了
   UI 渲染和编译通过，没有验证过"只勾 OurBits + 只选 Movies 分类后，搜出来的结果确实只来自 OurBits
   的电影分类"“按 Seeders 排序后顺序真的对”"填了 seeds min=10 后确实过滤掉了做种数<10 的结果"这类
   功能性验收标准，需要用户实测一遍——**优先级高于继续往下做新步骤**
2. 分类选择器目前对着 CJK 短标签测试过，但"Standard"/"Site-Specific" 这两个分组标题以及 8 个标准
   Torznab 大类名（Movies/TV/Audio/...）都还是硬编码英文，没有走 strings.xml 之外的本地化路径——
   目前判断这是合理的（协议层面的分类名，不是面向用户的文案，参照 indexer.name 本身也不本地化），
   但如果用户觉得别扭需要反馈
3. 方案第 2.4 节"下载目的地重新设计"仍然**没有**列进第 6 节六步，是否要补第七步，需要用户确认
4. P0 验收（APK 实机测试）如果还没做完，优先级高于继续往下做 P1 新步骤
5. P1 全部步骤（含第六步）都完成后，按方案第 8 节建议，把结论合并进 `docs/prowlarr-integration-plan.md`
   的"实施纪要"一节，避免两份方案文档长期并存
6. **写 KDoc/注释时如果要提到形如 `xxx/*` 这样以 `/*` 结尾的路径或通配符，务必改写措辞避开字面的
   `/*` 序列**——Kotlin 块注释支持嵌套，写在注释里的字面 `/*` 会被解析成新的嵌套层，导致注释自己的
   `*/` 只关闭了这个意外嵌套层，外层注释从此不再闭合、把后面所有代码吞成注释直到文件末尾。这个坑
   Round 5（`ui/search/*`）和 Round 10（`ui/search/*`，同一措辞，不同注释里又写了一次）各踩了一次，
   本轮已改写措辞规避，但下一轮新写注释时如果又不小心引用类似路径，还是可能再踩——写完之后可以用
   一个简单脚本统计整份文件 `/*`/`*/` 出现次数是否配平（深度归零）来自查，别只靠肉眼扫。

## 待确认事项（继承自原方案第 7 节，尚未处理）

- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
