# PROGRESS

> 给下一轮对话（可能是另一个 Claude 实例）看的进度记录。开工前先跑：
> `git log --oneline -10 && git status`，再对照本文件确认接哪里。
> 协作规范见 Project 知识库里的《代码协作与推送策略》。

## 当前分支

`main`（Round 16 已把 `feature/prowlarr-connection` 合并进 main，正式改名发布，功能分支使命结束，
后续直接在 main 上开发即可，无需再开新分支）

## 项目背景

给 qBitController（Kotlin Multiplatform / Compose Multiplatform，Android/iOS/Desktop）加一路
Prowlarr 搜索源。详细方案见 `docs/prowlarr-integration-plan.md`——**第 8 节「实施纪要」是权威现状**，
第 0～7 节是动工前的最初方案，两者冲突以第 8 节为准。

核心结论（详见文档第 8 节）：

1. 做成了**完全独立的页面**（`ui/prowlarr/search/`），零侵入 `ui/search/*` 原有搜索功能，可独立回退
2. 下载改成**客户端直传**：磁力链原样传给 qBit；种子直链由客户端自己下载字节后以文件形式上传给
   qBit，qBit 服务端不需要能访问 Prowlarr
3. 设置页加了 Prowlarr 连接配置；tab 显隐/顺序管理已在 Round 7 迁移到
   `SettingsManager.visibleTabs`，不再是单独的 `showProwlarrTab` 开关

## Rounds 1-15（已完成，功能可用，完整记录见 `docs/PROGRESS_ARCHIVE.md`）

| Round | 内容 |
|---|---|
| 1-4 | 全局 Prowlarr 连接设置、搜索 API 对接、独立搜索页、接入底部导航栏 |
| 5-9 | 修 KDoc 嵌套注释吞文件等编译 bug、CI pipefail 修复、tab 移位/可显隐、索引器多选、分类多选（含 Torznab id≥100000 分组修复） |
| 10-11 | 排序/过滤、错误详情/关键字过滤/点击跳浏览器 |
| 12 | 下载默认参数 + 分类专属路由（`docs/prowlarr-download-defaults-plan.md`） |
| 13 | 真机搜索首次实测，连续两次 `categories` 字段解析崩溃，改用独立的 `ProwlarrResultCategory` 类型修复 |
| 14 | P2 首轮真机反馈 5 条全部实施（去掉 isEnabled 开关、下载默认参数入口挪位、下载器选择、索引器标志过滤、长按=手动下载弹窗） |
| 15 | Round 14 收尾 CI 失败排查，三处独立编译错误（`rememberSaveable` 包路径、漏 import、可见性不匹配）一次修完 |

## Round 16（2026-08-13，本轮）：合并 main + 改名发布 qBitController-pr 2.2.1-v1 ✅

用户要求把 `feature/prowlarr-connection` 合并进 `main`，App 改名为 `qBitController-pr`，发布正式版
`2.2.1-v1`，多平台优先。

**合并与改名**：
- `--no-ff` 合并 64 个 commit 进 main，无冲突；清理合并带入的调试遗留文件 `build-error.log`
- App 显示名（Android `strings.xml` 的 `app_name` / iOS `Config.xcconfig` 的 `PRODUCT_NAME` / 桌面端
  `packageName`）统一改为 `qBitController-pr`，Bundle ID / applicationId 均未动
- `Versions.AppVersion` 2.2.1 → **2.2.1-v1**，`AppVersionCode` 29 → 30。桌面端 `packageVersion` 单独
  用 `.substringBefore("-")` 去掉后缀（Windows MSI 要求严格数字 `MAJOR.MINOR.BUILD`，验证过带后缀会
  直接构建失败），app 内 `BuildConfig.Version` 仍显示完整的 `2.2.1-v1`

**顺手修复**：`build-snapshot.yml`/`check-codestyle.yml` 一直监听不存在的 `master` 分支（仓库默认
分支其实是 `main`），从未真正触发过，已改成监听 `main`，并给 `build-snapshot.yml` 加了
`workflow_dispatch` 方便手动跑全平台验证构建。

**改名引出的三处路径硬编码不一致（均为本轮改名直接导致，非既有 bug）**：

1. 桌面端 flatpak 打包任务 `from("$buildDir/.../app/qBitController/")` 硬编码旧包名 →
   AppImage 输出目录已随 `packageName` 改名，导致 `bin/` 复制失败、`flatpak-builder` 报
   `Unable to get source file 'bin/'`。改用共享常量 `desktopPackageName`（新增在文件顶部），
   `compose.desktop.nativeDistributions.packageName` 和这里都引用它，避免以后再次漂移
2. iOS `build.yml` 的 `Generate IPA` 步骤 `mv .../qBitController.app` 硬编码旧产品名 → 实际产出是
   `qBitController-pr.app`（`xcodebuild` 本身成功，只是这一步 `mv` 找不到文件）
3. flatpak `manifest.yml` 的 `command: /app/bin/qBitController` 硬编码旧可执行文件名 → `bin/`
   目录本身已经复制对了（上面第 1 点修完后），但 flatpak 最终 finish 阶段报
   `Command '/app/bin/qBitController' not found`，因为 AppImage 里实际的启动脚本也随包名改叫
   `qBitController-pr`

排查方式：沙盒访问不了 GitHub Actions 日志的 Azure Blob 存储，用跟 `build-prowlarr-apk.yml` 一样的
"失败时把日志用 Contents API 写回仓库"套路，临时建了 `debug-linux-build.yml`（后来还有一个
`debug-winarm64-build.yml`）单独跑某一个平台的 job 迭代排错，确认修复后即删除，不留在仓库里。

**全平台验证结果**：Android / iOS / macOS(arm64+x86_64) / Windows(x86_64) / Linux(x86_64+arm64) 全部
构建成功。Windows arm64 在一次全平台联跑中失败过一次，单独隔离重跑后成功——判断是 `windows-11-arm`
这类较新 GitHub 托管 runner 的偶发性抖动，不是代码问题，未做进一步改动。

**下一步**：确认全平台验证绿了之后打 tag `v2.2.1-v1` 触发 `build-release.yml` 正式发布（GitHub
Release + 全平台产物）。该 workflow 还会尝试上传 Google Play（`build-playstore`/`upload-playstore`）
和更新 AltStore 源（`upload-altstore`）——这两个 job 不阻塞主发布（`upload-release` 只依赖
`extract-version` + `build`），但如果仓库配置了对应 secret 会真的对外发布到商店，已跟用户确认过这个
副作用。

## 下一轮接手时先做什么

1. **确认本轮的 `v2.2.1-v1` release 是否成功发布**——如果对话中止在打 tag 之前或 release workflow
   跑到一半，先查 `git tag -l` 和 Actions 页面确认状态，避免重复打 tag
2. **Round 14/15 的所有改动至今没有一次真机测试**——优先级高于继续做新功能。尤其是反馈 3 的下载器
   兜底优先级链、反馈 4 的 `indexerFlags` 过滤（字段未经第一方核实，见下方待确认事项）、反馈 5 的
   手动下载弹窗整条提交路径
3. 分类选择器的 "Standard"/"Site-Specific" 分组标题和 8 个标准 Torznab 大类名还是硬编码英文，未走
   本地化——目前判断合理（协议层分类名，非用户文案），如用户反馈别扭再处理
4. 所有功能性验收做完后，按方案第 8 节建议，把结论合并进 `docs/prowlarr-integration-plan.md` 的
   "实施纪要"一节，避免文档长期并存（现在是四份）
5. **`Versions.kt`/`Config.xcconfig`/`strings.xml` 里的 app 名称和 desktop `packageName` 现在是本轮
   新加的 `desktopPackageName` 常量统一管理，但 flatpak `manifest.yml` 的 `command:` 字段是静态
   YAML、没有模板化**——以后再改名务必记得同步这一处，不然会重演本轮排查的坑
6. **写 KDoc/注释时如果要提到形如 `xxx/*` 这样以 `/*` 结尾的路径或通配符，务必改写措辞避开字面的
   `/*` 序列**——字面 `/*` 会被 Kotlin 嵌套块注释解析成新的一层，外层注释从此不再闭合、吞掉后面所有
   代码。已踩两次（Round 5、Round 10）
7. **新增 `Res.string.X`/`Res.plurals.X` 用法时记得同时加一行对应 import**——不 import 就是编译期
   `Unresolved reference`。已踩两次（Round 11、Round 15）
8. **`ExposedDropdownMenuBoxScope` 的成员函数（`ExposedDropdownMenu`、`.menuAnchor(...)` 等）不需要
   也不能 import**，只在 `ExposedDropdownMenuBox { ... }` lambda 内靠隐式 receiver 解析。已踩三次
   （Round 12、Round 14）
9. **`rememberSaveable` 的正确包路径是 `androidx.compose.runtime.saveable`**，不是
   `androidx.compose.runtime`。Round 15 踩过一次，级联出近 40 条看似无关的报错
10. **排查编译错误日志时，先完整 grep 一遍所有 `error:` 行按文件/行号聚类，再逐类定位根因**——错误
    条数不代表根因数量，级联错误会让日志显得比实际情况严重得多（Round 15 近 40 条报错只对应 3 个
    独立根因）

## 待确认事项（继承自原方案第 7 节，尚未处理）

- 分页 `limit`/`offset` 在当前 Prowlarr 版本上是否生效未实测确认
- 未支持 Prowlarr 侧 Basic Auth / 反向代理鉴权场景
- `ProwlarrSearchResult.indexerFlags`（Round 14 反馈 4）字段名/取值是根据第三方文档推断的，不是
  已核实的真实响应样本——跟 Round 13 两次踩坑的 `categories` 字段是同一类未核实风险，实机验证时
  重点关注这个过滤器是否真的能筛出东西
- `composeApp/build.gradle.kts` 里 `SourceCodeUrl`/`LatestReleaseUrl`（桌面端更新检查用）仍指向
  上游 `Bartuzen/qBitController` 仓库，不是这个 fork——本轮改名发布时注意到但未处理（不在本次任务
  范围内），如果启用了 desktop 的更新检查功能，用户会被导向错误的仓库
