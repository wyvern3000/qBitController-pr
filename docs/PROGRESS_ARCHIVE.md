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
