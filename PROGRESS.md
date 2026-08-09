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
3. 设置页加了 Prowlarr 连接配置 + "在底部导航栏中显示"开关（`SettingsManager.showProwlarrTab`）

## Rounds 1-4（已完成，功能可用）

| Round | Commit | 内容 |
|---|---|---|
| 1 | `1372e3dc` | 全局 Prowlarr 连接设置（URL/API Key/测试连接），独立 `"prowlarr"` Settings 命名空间 |
| 2 | `bfecfd88` | Prowlarr 搜索 API 对接，结果映射到已有 `Search.Result` 模型 |
| 3 | `0255ac43` | 独立的 `ProwlarrSearchScreen`/`ProwlarrSearchViewModel`，客户端直传下载逻辑 |
| 4 | `04eead7b` | 接入底部导航栏（第 6 个可选 tab），处理了 tab 下标安全性、导航 channel 泄漏等细节 |

Round 4 推送后触发 CI，编译失败（见下）。

## Round 5（本轮，2026-08-09）：修复编译失败 + CI 自身的一个 bug —— 已构建成功 ✅

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
方向性偏离（见上面"核心结论"）。

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

## 下一轮接手时先做什么

1. **先找用户确认方案**（`docs/prowlarr-p1-search-ui-and-tabs-plan.md` 第 7 节六条待确认事项，尤其
   第 1 条字段结构、第 2/3 条要不要做子分类/记忆勾选状态），确认后再按方案第 6 节的六步顺序开工，
   每步单独 commit + push（第一步"tab 下标去字面量化"和第二步"visibleTabs + 外观设置勾选组"是后续
   步骤的地基，务必按顺序做，不要跳步）
2. P0 验收（APK 实机测试，见 `docs/prowlarr-integration-plan.md` 第 6 节）如果这轮还没做完，优先级
   高于本轮新方案的编码——先确认 P0 稳定，再叠加 P1 改动，避免两层未验证的改动叠在一起排查困难
3. 功能验证通过后，`build-prowlarr-apk.yml` 上标注了"临时工作流，分支合并/废弃后可删"，可以考虑清
   掉这个文件，改用仓库里已有的正式 build workflow
4. `build-prowlarr-apk.yml` 现在的 `set -o pipefail` 修复是 round 5 才加上的，之前所有轮次报告的
   "CI 成功"（round 4 之前）都没有这层保险，理论上也可能有被掩盖的失败，但已经用真实构建结果
   （round 5）覆盖验证过一遍最新代码，不需要往回查
5. 完成 P1 方案后，按 `docs/prowlarr-p1-search-ui-and-tabs-plan.md` 第 8 节的建议，把本文档结论合并
   进 `docs/prowlarr-integration-plan.md` 的"实施纪要"一节，避免两份方案文档长期并存

## 待确认事项（继承自原方案第 7 节，尚未处理）

- `categories`（Torznab 分类号）参数目前没接，Prowlarr 端默认查全部分类
- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
