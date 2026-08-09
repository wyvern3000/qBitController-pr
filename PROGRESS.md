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

## 下一轮接手时先做什么

功能已经能编译出 APK 了（round 5 的 CI 已验证）。建议：

1. 找用户确认：APK 实际装到手机上跑一遍 P0 验收标准（见方案文档第 6 节）——配置 Prowlarr 连接、搜
   索、点击下载（分别验证磁力链结果和种子直链结果两种情况）
2. 功能验证通过后，`build-prowlarr-apk.yml` 上标注了"临时工作流，分支合并/废弃后可删"，可以考虑清
   掉这个文件，改用仓库里已有的正式 build workflow；也可以问用户要不要继续做原方案 P1（indexer 多
   选、结果合并展示、分类映射）
3. Round 4 的 `MainScreen.kt` 改动里，`navigateToStartChannels[5]` 硬编码了 Prowlarr tab 排在第 6
   位（下标 5）——目前看和 `tabs` 列表的追加顺序一致，没发现问题，但如果以后又加别的可选 tab，这类
   写死的下标要留意
4. `build-prowlarr-apk.yml` 现在的 `set -o pipefail` 修复是这轮才加上的，之前所有轮次报告的"CI 成
   功"（round 4 之前）都没有这层保险，理论上也可能有被掩盖的失败，但已经用真实构建结果（round 5）
   覆盖验证过一遍最新代码，不需要往回查

## 待确认事项（继承自原方案第 7 节，尚未处理）

- `categories`（Torznab 分类号）参数目前没接，Prowlarr 端默认查全部分类
- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
