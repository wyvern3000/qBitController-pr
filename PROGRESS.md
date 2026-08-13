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

## Round 16（2026-08-13，本轮）：合并 main + 改名发布 qBitController-pr 2.2.1-v1 ⚠️ 卡在签名

用户要求把 `feature/prowlarr-connection` 合并进 `main`，App 改名为 `qBitController-pr`，发布正式版
`2.2.1-v1`，多平台优先。

**合并与改名**：
- `--no-ff` 合并 64 个 commit 进 main，无冲突；清理合并带入的调试遗留文件 `build-error.log`
- App 显示名（Android `strings.xml` 的 `app_name` / iOS `Config.xcconfig` 的 `PRODUCT_NAME` / 桌面端
  `packageName`）统一改为 `qBitController-pr`，Bundle ID / applicationId 均未动
- `Versions.AppVersion` 2.2.1 → **2.2.1-v1**，`AppVersionCode` 29 → 30。桌面端 `packageVersion` 单独
  用 `.substringBefore("-")` 去掉后缀（Windows MSI 要求严格数字 `MAJOR.MINOR.BUILD`）

**顺手修复**：`build-snapshot.yml`/`check-codestyle.yml` 一直监听不存在的 `master` 分支，改成监听
`main`，并给 `build-snapshot.yml` 加了 `workflow_dispatch`。

**改名引出的三处路径硬编码不一致（均已修复并验证）**：桌面端 flatpak 打包任务的 `app/qBitController/`
源目录、iOS `Generate IPA` 步骤的 `mv .../qBitController.app`、flatpak `manifest.yml` 的
`command: /app/bin/qBitController` 均硬编码旧包名，改名后全部失效。改用共享常量
`desktopPackageName`（build.gradle.kts）+ 逐个手动改名（iOS/flatpak manifest 是静态文件没模板化）。
排查用的是跟 `build-prowlarr-apk.yml` 一样的"失败时把日志写回仓库"套路，建临时 debug workflow
单独跑某一平台迭代，确认后即删除。

**全平台验证（未签名 dry-run）全部通过**：Android / iOS / macOS×2 / Windows×2 / Linux×2。

**正式打 tag `v2.2.1-v1` 发布，卡在 Android 签名**：

1. 第一次卡点——**secret 命名不匹配**：`build-release.yml` 原来读的是
   `QBITCONTROLLER_STORE_FILE_BASE64`/`QBITCONTROLLER_STORE_PASSWORD`/`QBITCONTROLLER_KEY_ALIAS`/
   `QBITCONTROLLER_KEY_PASSWORD`（上游项目的命名），但用户实际配置的是
   `SIGNING_KEYSTORE_BASE64`/`SIGNING_STORE_PASSWORD`/`SIGNING_KEY_ALIAS`/`SIGNING_KEY_PASSWORD`。
   引用不上的 secret 在表达式里静默取到空字符串，`Base64.decode("")` 生成一个 0 字节的"keystore"，
   报出一个具有误导性的 `KeytoolException: Tag number over 30 is not supported`（看起来像损坏的
   keystore，实际是空文件）。已修正 `build-release.yml` 里的 `secrets:` 映射改用 `SIGNING_*`
   （`56513b70`/`b553538d`）
2. 顺手按用户要求**移除了 `build-playstore`/`upload-playstore`/`upload-altstore` 三个 job**，
   `build-release.yml` 现在只剩 `extract-version` → `build` → `upload-release` 三步，不再尝试上传
   Google Play / 更新 AltStore 源
3. **改完 secret 名字后用同样的 keytoolException 复现**——用一个单独的 debug workflow（这次确保
   secret 引用方式正确，之前排查这一步犯过同样的"reusable workflow 内部 secret 引用方式在独立
   workflow 里用会读到空值"的错，已在 commit message 里记录避免以后重犯）验证，结果**报错完全一样**。
   说明 secret 命名问题是真实存在、也修对了，但**不是全部原因**——`SIGNING_KEYSTORE_BASE64` 这个
   secret 本身解出来的字节就不是一个合法的 keystore。`Base64.decode()` 本身没抛异常（说明字符串本身
   是合法 base64 字符集，没有换行/URL-safe 字符集之类的问题），但解出来的字节没法被当成有效
   JKS/PKCS12 解析。**这一步需要用户核实/重新生成这个 secret**，我这边拿不到实际内容没法再往下查
4. 已删除本轮所有临时 debug workflow 和调试日志文件，仓库目前干净

**下一步（需要用户操作）**：
- 请用户在本地用 `keytool -list -keystore 你的keystore文件` 先确认这个 keystore 文件本身是否能正常
  打开（排除文件本身已损坏的可能）
- 确认后重新生成 base64（建议 `base64 -w0 keystore.jks`，Linux；macOS 用
  `base64 -i keystore.jks | tr -d '\n'`，避免换行/软换行被带进 secret 值），完整复制、不要有多余
  引号或空格，重新写入 `SIGNING_KEYSTORE_BASE64` 这个 GitHub secret
- 顺手确认 `SIGNING_STORE_PASSWORD`/`SIGNING_KEY_ALIAS`/`SIGNING_KEY_PASSWORD` 三个值当前是否正确
  （尤其 alias，如果记错了具体是哪个 alias 也会导致类似的读取失败，虽然这次的报错更像是 keystore
  文件本身解析失败，不像是 alias/密码错误的典型报错）
- 确认好后回来说一声，我再验证一次签名、正式重新发布 `v2.2.1-v1`（当前这个 tag 因为没有任何一次
  release workflow 跑完整，还没有实际产生 GitHub Release，可以直接复用这个 tag 号重新触发，不需要
  改版本号）

## 下一轮接手时先做什么

1. **`v2.2.1-v1` 发布还卡在 Android 签名 keystore 数据问题上**（不是 secret 命名，那个已经修对了）——
   查 `git tag -l` 确认 tag 还在，如果用户已经重新生成了 `SIGNING_KEYSTORE_BASE64`，先用本轮踩过坑的
   方式（独立 debug workflow，直接引用 `secrets.SIGNING_KEYSTORE_BASE64` 等真实名字，**不要**照抄
   `build.yml` 内部 `secrets.store-file-base64` 这种 reusable workflow 专用的写法，那个在独立
   workflow 里会静默读到空值）单独验证签名能不能过，过了再重新触发这个 tag 的 release
2. **Round 14/15 的所有改动至今没有一次真机测试**——优先级次于上面签名的问题，但仍然高于继续做新
   功能。尤其是反馈 3 的下载器兜底优先级链、反馈 4 的 `indexerFlags` 过滤（字段未经第一方核实，见
   下方待确认事项）、反馈 5 的手动下载弹窗整条提交路径
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
