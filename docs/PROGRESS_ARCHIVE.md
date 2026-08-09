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
