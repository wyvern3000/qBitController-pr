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

`build-prowlarr-apk.yml` 从 push 自动触发改成 `workflow_dispatch` 手动触发（push 仍每 commit 都推，
只是构建验证不再跟着每个小 commit 触发）。索引器多选：`ProwlarrService.getIndexers()` 转发 + 三态选择器
（复用 `RadioButtonWithLabel`），`capabilities.categories` 核对真实响应后发现是递归结构（`ProwlarrCategory.
subCategories`），为第五步分类多选打好基础。踩坑：漏了 `EventEffect` 里 `IndexersError` 分支的 exhaustive
check，`8b3d4508` 修复后 CI success。完整记录已归档到 `docs/PROGRESS_ARCHIVE.md`。

## Round 9（2026-08-09）：P1 第五步（分类多选）完成，真机反馈后修了分类分组的显示 bug ✅

`categories: List<Int>?` 参数打通 `search()` 全链路。偏离方案文档：顶层分组改成从真实索引器数据动态
构建（`buildCategoryGroups`），而非方案里假设的固定 8 个 Torznab 标准大类——核对样本后发现 OpenCD
这类站点的自定义分类全在标准大类之外。真机测试发现同名 "Movies" chip（标准分类 2000 与站点自定义分类
100401）挨在一起显示像是渲染错乱，按 Torznab 规范 `id ≥ 100000` 为站点自定义范围拆成 "Standard"/
"Site-Specific" 两组解决（`7df171a7`，CI success）。完整记录已归档到 `docs/PROGRESS_ARCHIVE.md`。

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

## 下一轮接手时先做什么

1. **P1 六步 + Round 11/12 所有追加改动（错误详情/关键字过滤/点击跳浏览器/下载默认参数/分类路由）
   现在才第一次全部一起编译验证通过**（`0dab06f1`，CI success）。但到目前为止所有验证都停在"编译
   过/UI 渲染出来了"这一层，没有一条做过"功能确实生效"的实机验收——需要用户实测：索引器多选/分类
   多选是否真的把结果限定在选中范围、排序是否真的按 Seeders/Size 等排对了、seeds min 过滤是否真的
   滤掉了做种数不够的结果、关键字过滤是否真的按关键字筛、点击结果跳的浏览器链接是否真的对、错误
   详情文案是否真的把 Prowlarr 返回的 message 显示出来了、**下载默认参数是否真的套用到了实际下载
   请求上、分类路由是否真的按分类命中并覆盖了保存路径/分类/标签、路由优先级（列表顺序）是否符合
   预期**——**优先级高于继续往下做新功能**
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
