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

> Rounds 10-11（排序/过滤、错误详情/关键字过滤/点击跳浏览器）完整记录已归档到
> `docs/PROGRESS_ARCHIVE.md`。

> Round 12（下载默认参数 + 分类专属路由，方案见 `docs/prowlarr-download-defaults-plan.md`）完整记录
> 已归档到 `docs/PROGRESS_ARCHIVE.md`。

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

## Round 14（2026-08-12）：P2 首轮真机反馈，5 条意见全部实施完 ✅

用户装 Round 13 的 APK 实际用了一轮，给了 5 条反馈，方案见
`docs/prowlarr-p2-feedback-round1-plan.md`（审批通过后按文档"实施顺序"节分 8 个功能 commit 做完）：

1. 去掉语义不清的 "Enable Prowlarr Search" 开关（`a342e2b1`）——`isEnabled` 字段全仓库只有一处读写，
   没有任何门控逻辑依赖它，直接删
2. "Configure download defaults" 入口从 Prowlarr 设置页挪到 Settings 主页独立一项（`a342e2b1` 同一
   commit）
3. 下载默认参数/分类路由加"下载器"（server）选择（`3e4a04f0` 逻辑 + `78eff9e7` UI）——
   `resolveProwlarrDownloadRouting()` 返回类型从 `Triple` 换成具名 data class
   `ProwlarrResolvedDownloadRouting(serverId, savePath, category, tags)`；下载按钮 `enabled` 语义从
   `serverId != null` 收窄成 `!isAdding`（因为落点 server 现在可能因分类路由而异，没法在渲染时统一
   判定）
4. 搜索结果过滤器加"索引器标志"（Freeleech/Halfleech 等）过滤（`045263d9` 模型 + `b2644927` 过滤器/
   UI/chip）——**`indexerFlags` 字段跟 Round 13 的 `categories` 一样是未经第一方 spec 核实的推断
   （`List<String>?`），已记入下方"待确认事项"
5. 下载按钮长按=手动模式（`c28c1a1d` 手势/ViewModel 接线 + `5a961d66` 弹窗 UI）——新建
   `ProwlarrManualAddDialog.kt`，字段照抄 `AddTorrentScreen` 除源选择外的全部设置项，预填值复用
   `resolveProwlarrDownloadRouting()` 解析结果；新增 `addTorrentManual()` 跟现有私有 `addTorrent()`
   共用下载分支逻辑

**过程中一次编译修复**（`4fe99172`）：反馈 4 的过滤弹窗又手动加了一行不存在的
`import androidx.compose.material3.ExposedDropdownMenu`——跟 Round 12 踩的完全是同一个坑（第 7 条
教训），第三次在这个项目里出现。删掉即可，无需改动调用结构。

反馈 5 的两个 UI commit（`c28c1a1d`、`5a961d66`）推送后 dispatch CI 失败，日志见下一轮（Round 15）。

## Round 15（本轮，2026-08-13）：Round 14 收尾 CI 失败排查——三处独立编译错误，已修复验证 ✅

**背景**：上一轮对话（Round 14 后半段）已经诊断出问题但会话中止前未来得及 commit/push，本轮重新从
`git log`/`git status` 确认工作区干净、上一轮的修改确实丢失（沙盒重置），照《代码协作与推送策略》
重新排查而非直接相信上一轮的对话记录。

拉取 `5a961d66` 的 CI 失败日志（`build-error.log`），完整 grep 一遍 `error:` 而非只看前几条，定位到
**三处独立根因**（不是同一个坑的三种表现）：

1. `ProwlarrManualAddDialog.kt` 的 `rememberSaveable` 从 `androidx.compose.runtime` 导入，正确包路径
   是 `androidx.compose.runtime.saveable`（跟已有正确用法的四个文件核对确认）。这一个错误的 import
   级联出日志里另外近 40 条错误（`text`/`it`/`not` 未解析、`@Composable` 上下文错误等）——编译器在
   第一个 `rememberSaveable` 调用解析失败后，对同一段代码后续的类型推断全部失去了锚点
2. `Res.string.torrent_add_upload_speed_limit` 用了但没 import（照 Round 11 教训 #6 的方法，把文件里
   全部 `Res.string.X` 用法跟 import 块整体 diff 一遍，只有这一个缺失）
3. `ProwlarrSearchViewModel.resolveDownloadRouting()`（反馈 5 本轮新增，供 `ProwlarrManualAddDialog`
   预填表单用）是 `public` 成员，返回类型 `ProwlarrResolvedDownloadRouting` 却是 `internal`——
   Kotlin 可见性检查报错。因为唯一调用方就在同一个 module 内，改成员函数为 `internal`（而不是放宽
   data class 可见性）是改动面最小、且更收紧封装的修法

`b712b608` 一次性修完三处，重新 dispatch，[run
31653027833](https://github.com/wyvern3000/qBitController-pr/actions/runs/31653027833) `success`，
Artifacts API 确认产出 30111040 字节（~30MB）的真实 APK。

**教训**：一份编译失败日志里的错误数量不代表根因数量——40 条报错可能只对应 1-3 个真实问题，级联
错误（尤其是一个基础符号解析失败导致的连锁反应）会让日志显得比实际情况严重得多。排查顺序应该是
**先完整 grep 一遍所有 `error:` 行，按文件/行号聚类，再逐类定位**，而不是从第一条报错开始逐条深挖、
容易在级联错误上耗费大量精力（上一轮对话正是卡在这里：反复怀疑 `TextFieldValue.Saver`、文件行号
是否对得上，绕了几圈才找到真正的包路径问题）。

## 下一轮接手时先做什么

1. **Round 14 的 5 条反馈 + Round 15 的编译修复都只验证到"CI 编译通过"，一条都没有实机测试过**——
   优先级高于继续往下做新功能。尤其注意：反馈 3 的 server 兜底优先级链（路由 → 全局默认 → 当前
   app 选中的 server）、反馈 4 的 `indexerFlags` 过滤（字段本身未经第一方核实，见下方待确认事项）、
   反馈 5 的手动下载弹窗整条提交路径（预填值是否等于自动模式真实会用的值、磁力链/种子文件两种
   下载机制在手动路径下是否都正常）都还没有一次真机验证
2. 分类选择器目前对着 CJK 短标签测试过，但"Standard"/"Site-Specific" 这两个分组标题以及 8 个标准
   Torznab 大类名（Movies/TV/Audio/...）都还是硬编码英文，没有走 strings.xml 之外的本地化路径——
   目前判断这是合理的（协议层面的分类名，不是面向用户的文案，参照 indexer.name 本身也不本地化），
   但如果用户觉得别扭需要反馈
3. 所有功能性验收都做完后，按方案第 8 节建议，把结论合并进 `docs/prowlarr-integration-plan.md`
   的"实施纪要"一节，避免文档长期并存（现在是四份：P0 主方案、P1 方案、下载默认参数方案、
   P2 首轮反馈方案）
4. **写 KDoc/注释时如果要提到形如 `xxx/*` 这样以 `/*` 结尾的路径或通配符，务必改写措辞避开字面的
   `/*` 序列**——字面 `/*` 会被 Kotlin 嵌套块注释解析成新的一层，外层注释从此不再闭合、吞掉后面所有
   代码。已踩两次（Round 5、Round 10），写完新注释后用脚本统计整份文件 `/*`/`*/` 出现次数是否配平
   来自查，别只靠肉眼扫。
5. **新增 `Res.string.X`/`Res.plurals.X` 用法时记得同时加一行对应 import**——这个项目里 `Res.string.X`
   是扩展属性不是成员，不 import 就是编译期 `Unresolved reference`。已踩两次（Round 11、Round 15）。
   改完文件后 grep 一下用到的每个名字是否都在文件顶部有对应 import 行，别只肉眼扫一长串 import 列表。
6. **反过来的坑：`ExposedDropdownMenuBoxScope`（`ExposedDropdownMenu`、`.menuAnchor(...)` 等）是
   receiver 成员函数，不是顶层 composable，不需要也不能 import**——只在直接嵌套于
   `ExposedDropdownMenuBox { ... }` lambda 内调用时才通过隐式 receiver 解析。已踩三次（Round 12、
   Round 14 反馈 4 过滤弹窗）。写新的 `ExposedDropdownMenuBox`/`ExposedDropdownMenu` 用法前，直接
   照抄 `AddTorrentScreen.kt` 里现成的写法，不要凭直觉补 import。
7. **`rememberSaveable` 的正确包路径是 `androidx.compose.runtime.saveable`，不是
   `androidx.compose.runtime`**——Round 15 踩过一次，一个错误的 import 级联出近 40 条看似无关的
   报错（`text`/`it` 未解析、`@Composable` 上下文错误等）。写新文件时直接照抄已有正确用法（比如
   `ProwlarrSearchScreen.kt`）的 import 行，不要凭 IDE 自动补全或记忆手打。
8. **排查编译错误日志时，先完整 grep 一遍所有 `error:` 行按文件/行号聚类，再逐类定位根因，不要从
   第一条报错开始逐条深挖**——一份日志里的错误条数不代表根因数量，级联错误会让日志显得比实际情况
   严重得多（Round 15 的近 40 条报错实际只对应 3 个独立根因）。

## 待确认事项（继承自原方案第 7 节，尚未处理）

- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
- `ProwlarrSearchResult.indexerFlags`（Round 14 反馈 4）字段名/取值是根据第三方文档推断的，不是
  已核实的真实响应样本——跟 Round 13 两次踩坑的 `categories` 字段是同一类未核实风险，实机验证时
  重点关注这个过滤器是否真的能筛出东西
