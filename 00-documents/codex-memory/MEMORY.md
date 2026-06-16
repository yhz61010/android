# Task Group: DCD shared-memory AGENTS and Codex mirror workflow in /home/yhz61010/StudioProjects/dcd-demo
scope: 适用于 `dcd-demo` 仓库里通过 `AGENTS.md` 启动共享记忆、同步外部 Codex 镜像、保留 StarPay Pro 路径映射、并坚持 Claude 文件只读边界的任务。
applies_to: cwd=/home/yhz61010/StudioProjects/dcd-demo; reuse_rule=DCD 外部目录、镜像路径和 AGENTS 启动顺序是 checkout-sensitive；“不要修改 Claude 文件”和“唤醒记忆”触发方式可跨相近工作流复用，但仍应先核对当前项目路径

## Task 1: 更新 dcd-demo 的 AGENTS 共享记忆入口并建立 20-DCD 外部镜像，成功

### rollout_summary_files

- rollout_summaries/2026-05-22T07-19-34-PxNF-dcd_shared_memory_agents_mirror_and_claude_readonly.md (cwd=/home/yhz61010/StudioProjects/dcd-demo, rollout_path=/home/yhz61010/.codex/sessions/2026/05/22/rollout-2026-05-22T15-19-34-019e4e8d-c463-73d0-a3d4-d724ecc523b3.jsonl, updated_at=2026-05-25T05:07:33+00:00, thread_id=019e4e8d-c463-73d0-a3d4-d724ecc523b3, AGENTS startup path, DCD mirror sync, and Claude-readonly boundary validated)

### keywords

- AGENTS.md, 共享记忆加载, current-codex-memories, 唤醒记忆, Claude read-only, 20-DCD/Codex, StarPay Pro App path, diff -qr

## User preferences

- 当用户说“StarPay Pro App 项目路径是 /home/yhz61010/NST/AndroidProjects/starpaypro。将这一点更新到 AGENTS.md 中。” -> 未来 DCD/StarPay 协作文档里把 StarPay Pro 本机路径写明，不要留成隐含背景 [Task 1]
- 当用户说“将 Codex 的主记忆，复制到 /home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex 下面…以后主记忆更新时，也要同时更新到那个目录里” -> 后续主 Codex 记忆更新时，默认同步 DCD 外部镜像 [Task 1][ad-hoc note]
- 当用户追问 fresh clone 如何“回忆起以前的记忆”，后来又直接说“唤醒记忆” -> 在这个仓库里应把 `AGENTS.md` 作为启动索引，并接受“唤醒记忆”作为读取共享记忆的简短触发词 [Task 1]
- 当用户再次强调“任何时候都不要修改 Claude 的文件” -> `CLAUDE.md`、`.claude/**`、外部 `Claude/**` 继续视为只读参考，只在 Codex-owned 路径写派生内容 [Task 1][ad-hoc note]

## Reusable knowledge

- DCD 共享记忆镜像路径是 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/`；新会话应先从仓库 `AGENTS.md` 进入，再按其中顺序读 `memory_summary.md`、`MEMORY.md`、`extensions/ad_hoc/notes/` 和任务相关 `rollout_summaries/` [Task 1][ad-hoc note]
- 外部 DCD `AGENTS.md` 副本位于 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/AGENTS.md`；仓库 `AGENTS.md` 的“共享记忆加载”段是 future clone 的恢复入口 [Task 1]
- DCD 相关的 Claude 派生本地化说明放在 `converted-claude-memory.md`；它和镜像目录一起构成外部 DCD Codex 记忆树 [Task 1]
- 这条 shared-memory 流程同时承接当前 DCD 业务上下文：`HttpCommandSender` -> `dcd-server` HTTP/polling -> WebSocket -> StarPay Pro，签名公式仍是 `SHA256(aid + orderNo + amount + timestamp + aKey)`；后续业务 review 仍优先检查 `BUSY`、`CANCEL`、`pairingCode + orderNo`、pending-result 生命周期和 `RefundFee` 语义 [Task 1]

## Failures and how to do differently

- 症状：第一次比较镜像内容时统计基准不对，看起来像源/目标数量不一致 -> 原因：比较时没有统一相对路径基准 -> 修复：后续用 `diff -qr -x .git -x .agents -x .codex` 或基于相对路径的比较来验证镜像完全一致 [Task 1]
- 症状：外部 `AGENTS.md` 同步步骤中断后，容易误以为规则已经落盘 -> 原因：复制动作被打断，缺少再次验证 -> 修复：镜像或外部文档同步后要重新核对目标文件，再做完成判断 [Task 1]
- 症状：旧记忆里仍混有远端历史路径，可能被误当成当前本机路径 -> 原因：路径更正信息没有作为默认读取规则反复强调 -> 修复：除非明确写成“映射”或“历史修正”，否则只使用当前本机路径 [Task 1]

# Task Group: StarPay shared-memory wake-up and Claude-readonly workflow in /home/yhz61010/NST/AndroidProjects/starpaypro
scope: 适用于 `starpaypro` 仓库中“唤醒记忆”、确认共享记忆加载顺序、使用外部 Codex 镜像、以及维持 Claude 文件只读边界的任务；不覆盖一般性业务开发或构建细节。
applies_to: cwd=/home/yhz61010/NST/AndroidProjects/starpaypro; reuse_rule=StarPay 外部镜像路径、路径映射和 AGENTS 加载顺序是 checkout-sensitive；“唤醒记忆”和 Claude 只读边界可跨相近工作流复用，但仍需核对当前 mirror 路径

## Task 1: 通过“唤醒记忆”恢复 StarPay Pro 共享记忆并确认加载顺序，成功

### rollout_summary_files

- rollout_summaries/2026-05-22T07-18-21-JHKF-starpaypro_memory_wakeup_and_shared_memory_loading.md (cwd=/home/yhz61010/NST/AndroidProjects/starpaypro, rollout_path=/home/yhz61010/.codex/sessions/2026/05/22/rollout-2026-05-22T15-18-21-019e4e8c-a91b-77b2-bf6d-7be78f5430b1.jsonl, updated_at=2026-05-25T05:07:04+00:00, thread_id=019e4e8c-a91b-77b2-bf6d-7be78f5430b1, shared-memory entrypoint and Claude-readonly boundary revalidated without file edits)

### keywords

- 唤醒记忆, AGENTS.md, shared memory loading, current-codex-memory, memory_summary.md, MEMORY.md, Claude readonly, DCD path

## User preferences

- 当用户直接说“唤醒记忆” -> 先重载 durable context，再决定是否需要深入 `rollout_summaries/`，不要一上来就进入实现 [Task 1]
- 当用户先前要求“将 Codex 的主记忆，复制到 .../10-StarPay/Codex 下面…以后主记忆更新时，也要同时更新到那个目录里” -> 未来 StarPay 相关记忆恢复不能只依赖 `~/.codex/memories`，还要把外部镜像当作一等记忆源 [Task 1][ad-hoc note]
- 当用户持续坚持“任何时候都不要修改 Claude 的文件” -> `CLAUDE.md`、`.claude/**`、外部 `Claude/**` 默认只读；转换或纠偏内容只写到 Codex-owned 目录 [Task 1][ad-hoc note]

## Reusable knowledge

- StarPay Pro 的共享记忆入口是 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay/Codex/current-codex-memory/`；最实用的加载顺序是 `AGENTS.md` -> `current-codex-memory/MEMORY.md` -> `current-codex-memory/memory_summary.md` -> 需要时再按关键词下钻 `rollout_summaries/` [Task 1][ad-hoc note]
- StarPay 相关远端 Claude 路径 `~/yhz61010/Documents/StarPay/` 在本机映射到 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay/`；涉及外部同步或转换引用时先用这个本地路径 [Task 1][ad-hoc note]
- `starpaypro` 相关 DCD 本机路径固定为 `/home/yhz61010/StudioProjects/dcd-demo`；写指南、读历史记忆或做路径转换时不要保留旧 `/home/coding04/...` 引用 [Task 1]
- `AGENTS.md` 已有“共享记忆加载”段，明确告诉 future clone 先读共享记忆入口并保持 Claude 派生文件只读；更新主记忆时还要同步外部镜像 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/10-StarPay/Codex/current-codex-memory/` [Task 1][ad-hoc note]

## Failures and how to do differently

- 本轮没有功能性失败；但“唤醒记忆”类任务不应先扫整份 `raw_memories.md`，应先用 `MEMORY.md` 做索引，再按关键词定向读取 summary [Task 1]
- 症状：外部镜像与主记忆若长期不同步，会让记忆恢复看到旧规则 -> 原因：只更新主目录，没有同步 StarPay mirror -> 修复：主记忆变更后同步 `current-codex-memory/`，避免 stale mirror 干扰检索 [Task 1][ad-hoc note]

# Task Group: DCD business-state review across /home/yhz61010/StudioProjects/dcd-demo and /home/yhz61010/NST/AndroidProjects/starpaypro
scope: 适用于 DCD Demo、dcd-server 与 StarPay Pro DCD 集成的业务语义 review；重点是 outbox、CANCEL、pairingCode 隔离、设置入口权限、退款字段语义，以及“Demo 代码不是重点”的审查权重控制。
applies_to: cwd=/home/yhz61010/StudioProjects/dcd-demo and /home/yhz61010/NST/AndroidProjects/starpaypro; reuse_rule=仅在同一套 DCD Demo + StarPay Pro 联调语义、相近目录结构或相同 DCD 文件族下直接复用；具体业务字段和状态机仍要先核对当前代码

## Task 1: review dcd-demo、dcd-server 与 StarPay Pro DCD/HelpActivity 业务正确性，形成后续复审清单

### rollout_summary_files

- rollout_summaries/2026-05-19T03-08-55-ExK1-dcd_demo_starpaypro_review_outbox_cancel_pairing_review.md (cwd=/home/yhz61010/StudioProjects/dcd-demo, rollout_path=/home/yhz61010/.codex/sessions/2026/05/19/rollout-2026-05-19T11-08-55-019e3e35-389b-75a1-be8d-ccafcdc9b7f9.jsonl, updated_at=2026-05-19T07:41:18+00:00, thread_id=019e3e35-389b-75a1-be8d-ccafcdc9b7f9, cross-repo business review; prepare for another Claude Code review cycle)

### keywords

- code review, DCD, StarPay Pro, dcd-demo, dcd-server, outbox, CANCEL, BUSY, pairingCode, orderNo, RefundFee, HelpActivity, WebSocket, better-sqlite3, ABI mismatch

## User preferences

- 当用户要求“代码 Review。包括 ... 以及 HelpActivity.kt。要确保要满足业务需求。” -> review 默认按业务需求和跨仓库影响组织，不要只看单文件 diff [Task 1]
- 当用户补充“NodeJS 相关的代码只是 Demo，没有大问题就行。Review 时该项目不是重点。” -> 默认把 StarPay Pro 生产代码放在最高优先级，NodeJS 端只检查会不会明显误导联调或破坏协议语义 [Task 1]
- 当用户说“我的修改方案你看一下” -> 要按实际代码逐项验证用户方案，指出哪一项仍与现状不符，不要把设计意图当成已落地事实 [Task 1]
- 当用户说“我一会让 Claude Code 重新改一下，到时候你再Review下。” -> 默认这是迭代 review 工作流，反馈要可复核、可对照，且不要在未看新代码前提前结案 [Task 1]

## Reusable knowledge

- DCD Demo 当前联调链路是 `HttpCommandSender` HTTP 轮询 `dcd-server`，再由 WebSocket 转到 StarPay Pro；签名约束仍围绕 `aid + orderNo + amount + timestamp + aKey` [Task 1]
- 这轮 review 的高风险点集中在 `DcdMmkvCache.kt`、`DcdCommandListener.kt`、`DcdCallbackActivity.kt`、`DcdWebSocketClient.kt`、`DcdListenerService.kt`、`DcdSettingsActivity.kt` 和 `HelpActivity.kt`；跨仓库问题还涉及 `dcd-server/src/db.js`、`routes.js`、`wsManager.js` [Task 1]
- `DcdCallbackActivity` 需要统一构建 `type: "RESULT"` 的结果 JSON，并确保 outbox 与实时发送走同一格式；否则重发结果时服务端无法按协议识别 [Task 1]
- `DcdCommandListener` 的 BUSY 判断应把 `hasPendingResult` 与 `isProcessing` 一起纳入；否则已有 pending result 时仍可能接收新单 [Task 1]
- `dcd-server` 若要真正隔离不同终端，主键和查找条件都应围绕 `(pairingCode, orderNo)`，不能只靠 `orderNo` 或只加 query 参数校验 [Task 1]
- 退款语义不能直接复用 `amount > 0`。在现有协议里，PAY 看 `OrderAmount`，REFUND 看 `RefundFee` 和 `transactionId`；需要回到 `IntentFactory.kt` 与 `IntentRequest.java` 核对字段映射和有效性规则 [Task 1]
- 本轮可复用的轻量验证是：`./gradlew test`（dcd-demo）、`node --check src/db.js src/routes.js src/wsManager.js`、StarPay Pro `./gradlew :app:processStarPay_Test_CommonAndroidDebugManifest`、`./gradlew :app:compileStarPay_Test_CommonAndroidDebugKotlin`；`node -e "require('./src/db')"` 因 `better-sqlite3` ABI 不匹配不能当作代码正确性的直接信号 [Task 1]

## Failures and how to do differently

- 症状：把 NodeJS Demo 端提成主要阻断项 -> 原因：没有按用户强调的“Demo 不是重点”收敛权重 -> 修复：优先找 StarPay Pro 生产状态机和权限问题，Demo 端只保留明显协议风险 [Task 1]
- 症状：用户描述“问题 2 修复方案”看起来合理，但实际仍会误伤 REFUND -> 原因：把 `amount` 当成所有 tradeAction 的统一校验值 -> 修复：按 `PAY` / `REFUND` / `QUERY` 分别核对协议底层约束，不要套一条金额规则 [Task 1]
- 症状：review 只看口头方案会漏掉 `HelpActivity` 仍直接打开 `DcdSettingsActivity`、`clearAllState()` 仍清掉 pending result、`isProcessing` 仍有 3 分钟自动释放副作用 -> 原因：没有逐文件复查实际代码 -> 修复：后续复审必须逐文件确认状态机与入口权限是否真的落地 [Task 1]
- 症状：`node -e "require('./src/db')"` 报 `NODE_MODULE_VERSION 115` vs `137`，看起来像 dcd-server 运行失败 -> 原因：本机 Node 与 `better-sqlite3` native 模块 ABI 不匹配，不是 JS 语法错误 -> 修复：运行时验证前先重建 native 依赖或切回匹配 Node 版本 [Task 1]

# Task Group: cross-workflow collaboration language preference
scope: 记录跨任务可复用的交流默认值；用于任何未另行指定语言的后续协作，不包含仓库专属实现细节。
applies_to: cwd=workflow-wide; reuse_rule=这是用户级稳定偏好，可跨 checkout 复用，除非用户在当前会话另行要求其他语言

## Task 1: 用户要求后续默认用中文对话，已记录为全局协作偏好

### rollout_summary_files

- rollout_summaries/2026-05-15T06-54-02-bUQm-user_prefers_chinese.md (cwd=/home/yhz61010/NST/AndroidProjects/starpaypro, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T14-54-02-019e2a69-e246-7e91-ad26-a03dc7375456.jsonl, updated_at=2026-05-15T06:54:51+00:00, thread_id=019e2a69-e246-7e91-ad26-a03dc7375456, direct preference capture)

### keywords

- 中文, 用中文与我对话, language preference, conversation style, AGENTS.md

## User preferences

- 当会话没有额外语言约束时，用户直接要求：“用中文与我对话。” -> 后续默认使用中文回复，不必重复确认语言选择 [Task 1]
- 当任务是生成或更新 `AGENTS.md` 时，用户又补充“记住，以后用中文与 AGENTS.md” -> 默认保持 `AGENTS.md` 正文中文、文件名仍为 `AGENTS.md`，除非用户明确要求其他语言或文件名 [Task 1]

## Reusable knowledge

- 这是稳定且高复用的交流偏好信号；未来先按中文输出，通常能减少一次来回确认 [Task 1]

## Failures and how to do differently

- 本任务没有失败；下次不要把语言偏好埋在局部任务里，应优先作为跨工作流默认值应用 [Task 1]

# Task Group: Android multi-module review and incremental-build debugging in /home/yhz61010/StudioProjects/android
scope: 适用于 `/home/yhz61010/StudioProjects/android` 中的模块 review、回归测试补齐、生命周期/并发修复，以及看似源码错误但实为增量编译产物失配的构建排障。
applies_to: cwd=/home/yhz61010/StudioProjects/android; reuse_rule=仅在同仓库或结构/模块名高度相似的 Android 多模块 checkout 中直接复用，涉及具体模块路径和 Gradle 任务时先核对当前分支状态

## Task 1: review basenetty 并在新分支修复 EventBus 与 BaseNettyClient 缺陷，成功

### rollout_summary_files

- rollout_summaries/2026-05-18T06-53-15-Huu3-basenetty_review_fixes_and_demo_androidbase_recompile.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/18/rollout-2026-05-18T14-53-15-019e39dc-3bc2-7f91-8e9f-dc06275ecc27.jsonl, updated_at=2026-05-18T07:26:27+00:00, thread_id=019e39dc-3bc2-7f91-8e9f-dc06275ecc27, verified with tests, assemble, ktlint, detekt, and git diff --check)

### keywords

- basenetty, EventBus, BaseNettyClient, BaseClientChannelInboundHandler, CopyOnWriteArrayList, ByteArrayInputStream, disconnectManually, handlerRemoved, testDebugUnitTest

## Task 2: demo assembleDevDebug 失败定位为 androidbase Kotlin 产物陈旧，成功解除阻塞

### rollout_summary_files

- rollout_summaries/2026-05-18T06-53-15-Huu3-basenetty_review_fixes_and_demo_androidbase_recompile.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/18/rollout-2026-05-18T14-53-15-019e39dc-3bc2-7f91-8e9f-dc06275ecc27.jsonl, updated_at=2026-05-18T07:26:27+00:00, thread_id=019e39dc-3bc2-7f91-8e9f-dc06275ecc27, root cause verified by rerun-tasks rebuild)

### keywords

- demo, assembleDevDebug, androidbase, compileDebugKotlin, --rerun-tasks, ActivityExtKt, SnackbarExtKt, ThreadExtKt, StringExtKt, stale-outputs

## User preferences

- 当用户要求：“Review 一下这个模块的代码。若有问题，新建一个分支，并在新分支上进行修改。” -> 默认先 review，再在确认有问题后建分支修复，不要一开始就混入大范围改动 [Task 1]
- 当用户没有要求重构时，本仓库里的类似 review/修复任务应优先走“小改动 + 回归测试”的证据链，而不是顺手清理无关代码 [Task 1]
- 当用户在排障结束后连续问“说一下主要修改了什么内容？” -> 收尾时要主动给出精炼的实际改动摘要，不要只报命令结果 [Task 2]

## Reusable knowledge

- `basenetty` 原来没有 `src/test`；补 JVM 回归测试时需要先在 `basenetty/build.gradle.kts` 增加 `testImplementation(libs.bundles.test)`，再落测试文件 [Task 1]
- `EventBus.processHandlers()` 在 handler 迭代中再次注册 handler 时会触发 `ConcurrentModificationException`；这里改成 `CopyOnWriteArrayList` 是已验证修复 [Task 1]
- `BaseNettyClient.getCertificateInputStream()` 不能重复返回同一个一次性流；缓存证书字节并每次返回新的 `ByteArrayInputStream` 才能覆盖 self-signed WSS 重连场景 [Task 1]
- `disconnectManually()` 需要显式处理 `channel` 尚未初始化的路径，否则协程可能挂起不返回；`BaseClientChannelInboundHandler.handlerRemoved()` 也要在客户端已是 `DISCONNECTED` 时跳过失败/重试逻辑 [Task 1]
- 这个仓库里，`demo` 的 unresolved reference 不一定是源码真的缺失；先确认 `androidbase/src/main/kotlin/com/leovp/androidbase/exts/*` 源文件是否存在，再检查 `androidbase/build/intermediates/...` 里是否已经生成对应 `*Kt.class` [Task 2]
- `./gradlew :androidbase:compileDebugKotlin --rerun-tasks` 已验证能刷新 `androidbase` 的陈旧 Kotlin 产物，并解除 `./gradlew :demo:assembleDevDebug` 的阻塞 [Task 2]
- 这一类任务在本仓库的高置信完成标准是：模块测试、`ktlintCheck`、`detekt`、必要的 assemble/build 命令，以及 `git diff --check` 全绿 [Task 1][Task 2]

## Failures and how to do differently

- 症状：测试里想用 Kotlin SAM 简写构造 `EventBusHandler` 却编不过 -> 原因：`EventBusHandler` 不是 `fun interface` -> 修复：测试里改用匿名对象 [Task 1]
- 症状：清理逻辑先报 `TimeoutCancellationException`，把真正断言淹没 -> 原因：`release()` 清理超时时间设得太短 -> 修复：先把 timeout 拉长，再看真实失败信号 [Task 1]
- 症状：`git switch -c basenetty-review-fixes` 一开始因 `.git` 锁文件只读失败 -> 原因：当前执行环境对 `.git` 写入有限制 -> 下次预期分支创建可能需要可写 Git 环境 [Task 1]
- 症状：`demo` 报 `startActivity`、`sleep`、`truncate`、`snack`、`action` 等 unresolved reference，看起来像 import 错误 -> 原因：更接近 `androidbase` 增量编译/产物状态失配，而不是立即修改源码 import -> 修复：先核对 provider 模块源文件与编译产物，再强制重编 provider 模块 [Task 2]

# Task Group: Android JitPack, Gradle publishing, and style-policy debugging in /home/yhz61010/StudioProjects/android
scope: 适用于 `/home/yhz61010/StudioProjects/android` 中与 JitPack 构建、仓库源顺序、Git LFS、Gradle publication 坐标冲突、detekt/ktlint 行长治理有关的诊断和修复。
applies_to: cwd=/home/yhz61010/StudioProjects/android; reuse_rule=仅在同仓库或也使用 JitPack + Git LFS + Android maven-publish 的相近 checkout 中直接复用；涉及版本号、模块名、Gradle 版本时先核对当前文件

## Task 1: JitPack DeviceCompat 解析失败，调整 settings.gradle.kts 仓库顺序后局部验证通过

### rollout_summary_files

- rollout_summaries/2026-05-15T05-29-03-UN96-jitpack_devicecompat_repo_order_and_local_execution.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T13-29-03-019e2a1c-131d-7272-b80a-5a35699d57a8.jsonl, updated_at=2026-05-15T06:03:08+00:00, thread_id=019e2a1c-131d-7272-b80a-5a35699d57a8, `:camerax:checkReleaseAarMetadata` verified locally)

### keywords

- JitPack, DeviceCompat, XXPermissions, checkReleaseAarMetadata, dependencyInsight, settings.gradle.kts, maven repository order, local-command-execution

## Task 2: 分析 jpeg 模块在 JitPack 上因 Git LFS pointer `.so` 失败，根因定位到 LFS 对象未实化但未当场修复

### rollout_summary_files

- rollout_summaries/2026-05-18T01-41-46-NYlI-jitpack_lfs_pointer_linker_failure_jpeg_module.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/18/rollout-2026-05-18T09-41-46-019e38bf-1136-76c1-9ed4-76ba966f764a.jsonl, updated_at=2026-05-18T01:43:25+00:00, thread_id=019e38bf-1136-76c1-9ed4-76ba966f764a, diagnosis only; investigate JitPack LFS materialization path)

### keywords

- jitpack, git-lfs, ld.lld, unknown directive version, version https://git-lfs.github.com/spec/v1, jpeg, libjpeg.so, libturbojpeg.so, git ls-tree -l, 132-byte blobs

## Task 3: 后续通过 jitpack.yml `before_install` 拉取 LFS 对象，修复同类 Git LFS pointer `.so` 构建失败

### rollout_summary_files

- rollout_summaries/2026-05-15T08-25-40-OBgC-jitpack_lfs_fix_and_detekt_max_line_length_100.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T16-25-40-019e2abd-c659-7701-a85f-9fc0ee84a4ea.jsonl, updated_at=2026-05-15T09:08:26+00:00, thread_id=019e2abd-c659-7701-a85f-9fc0ee84a4ea, verified with `git lfs fsck` and `:jpeg:assembleRelease --offline`)

### keywords

- jitpack, git-lfs, before_install, ld.lld unknown directive version, version https://git-lfs.github.com/spec/v1, .gitattributes, jitpack.yml, :jpeg:assembleRelease --offline

## Task 4: JitPack publication 因 duplicate coordinates 在 POM 生成阶段失败，根因定位未修复

### rollout_summary_files

- rollout_summaries/2026-05-18T02-06-40-jfve-jitpack_duplicate_publication_coordinates.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/18/rollout-2026-05-18T10-06-40-019e38d5-dd9a-7ac0-91b8-45baa0915c94.jsonl, updated_at=2026-05-18T02:08:41+00:00, thread_id=019e38d5-dd9a-7ac0-91b8-45baa0915c94, diagnosis only; no patch applied)

### keywords

- maven-publish, duplicate coordinates, generatePomFileForAndroidbasePublication, multiple publications, libs.versions.leo.version, -Pversion, Gradle 9.4, publishToMavenLocal not found

## Task 5: 将全局 max line length 改为 100，并在新分支修复 detekt/ktlint 连带问题，成功

### rollout_summary_files

- rollout_summaries/2026-05-15T08-25-40-OBgC-jitpack_lfs_fix_and_detekt_max_line_length_100.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T16-25-40-019e2abd-c659-7701-a85f-9fc0ee84a4ea.jsonl, updated_at=2026-05-15T09:08:26+00:00, thread_id=019e2abd-c659-7701-a85f-9fc0ee84a4ea, verified with detekt, ktlintCheck, line scan, and git diff --check)

### keywords

- max_line_length 100, detekt, ktlint, Indentation, .editorconfig, 10-configs/detekt.yml, ktlint_standard_indent, detekt-max-line-length-100, CameraAvcEncoder, ByteBufferExt

## User preferences

- 当用户贴出 JitPack/Gradle 失败日志并问原因时，不要先补丁式乱改；先从日志、依赖链、publishing DSL、仓库源顺序这些证据入手定位最小根因 [Task 1][Task 2][Task 4]
- 当用户说“添加到你自己的记忆里，可以在本机直接执行命令，不需要在沙箱里执行。” -> 这类需要正常文件系统/网络访问的 Gradle、依赖、构建验证，应默认优先用本机直执行思路，而不是把失败归因于业务代码 [Task 1]
- 当用户说自己已经更新了 `settings.gradle.kts` 并要求“检查更新后的文件” -> 先重新评估当前仓库状态，不要沿用更新前的结论 [Task 1]
- 当用户要求“并在新分支修改发现的问题”时，应先创建分支再做风格治理；当用户强调“尤其是行最大长度字符问题”时，要把物理行长是否全部达标作为收尾验证的一部分 [Task 5]

## Reusable knowledge

- 这个仓库里 `camerax` 依赖 `api(libs.xx.permissions)`，而 `gradle/libs.versions.toml` 把它映射到 `com.github.getActivity:XXPermissions:28.0`；`DeviceCompat:2.3` 是该依赖的传递依赖，所以 `:camerax:checkReleaseAarMetadata` 报 `DeviceCompat-2.3.jar` 缺失时，先追这条链 [Task 1]
- `dependencyResolutionManagement.repositories` 把 `maven("https://jitpack.io")` 放在阿里/腾讯镜像前面对 `com.github.*` 解析很关键；本次改完后 `./gradlew :camerax:checkReleaseAarMetadata --refresh-dependencies` 已通过 [Task 1]
- `.gitattributes` 将 `*.so` 交给 Git LFS；如果构建日志出现 `ld.lld ... unknown directive: version` 且 `.so` 首行是 `version https://git-lfs.github.com/spec/v1`，说明拿到的是 pointer，不是真正的 ELF [Task 2][Task 3]
- `jpeg/CMakeLists.txt` 通过 `IMPORTED_LOCATION` 直接引用 `jpeg/libs/${ANDROID_ABI}/libjpeg.so` 和 `libturbojpeg.so`；这种预编译 `.so` 一旦没被 LFS 正确实化，就会在 native 链接阶段直接炸 [Task 2]
- 诊断这类 LFS 问题时，`git ls-tree -l HEAD jpeg/libs/...` 显示 `132` 字节 blob、而本地 `file` 却显示真实 ELF，说明本地工作树正常但 git tree/JitPack checkout 看到的仍是 pointer 文件 [Task 2]
- 处理这类 LFS 问题时，`jitpack.yml` 的 `before_install` 是有效挂点：安装/初始化 `git-lfs`、执行 `git lfs pull`，再跑 Gradle；本仓库用 `git lfs fsck` 和 `./gradlew :jpeg:assembleRelease --offline` 完成复验 [Task 3]
- 本仓库多个模块都用了同一套 publication 模式：`maven-publish` + `singleVariant("release")` + `afterEvaluate { create<MavenPublication>("release") { version = libs.versions.leo.version.get(); from(components["release"]) } }`；这让 JitPack 注入 `-Pversion=...` 时容易与固定 `release` publication 坐标冲突 [Task 4]
- `Execution failed for task ':androidbase:generatePomFileForAndroidbasePublication'` 加上 `Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.` 是这类 duplicate coordinates 问题的明确信号；先查各模块 publication 坐标，不要误判成普通编译错误 [Task 4]
- 行长治理在这个仓库里分散在 `.editorconfig` 与 `10-configs/detekt.yml`；稳定改到 100 时，两处都要同步，且 `ktlint_standard_indent` 需要开启，否则 detekt 会爆大量 `Indentation` 噪音 [Task 5]
- 风格迁移任务在本仓库的高置信收尾是四重校验：`./gradlew detekt`、`./gradlew ktlintCheck`、Kotlin/KTS 物理行长扫描 `TOTAL 0`、`git diff --check` 干净 [Task 5]

## Failures and how to do differently

- 症状：Gradle/JitPack 验证卡在 `~/.gradle` 锁文件只读、wrapper 下载被拦、`Operation not permitted` 等环境错误 -> 原因：执行环境无法提供正常 Gradle 缓存/网络 -> 下次先把环境限制与业务根因分开，必要时切到本机直执行或预热缓存 [Task 1][Task 4]
- 症状：JitPack 日志里一开始出现 `sdkmanager --install "cmake;3.22.1"` 相关 `javax/xml/bind/annotation/XmlSchema` 噪音 -> 原因：`jitpack.yml` bootstrap 步骤本身报错，掩盖了真正的依赖解析问题 -> 修复：先看 `jitpack.yml` 的 `before_install`，再区分 bootstrap 错误和主构建错误 [Task 1]
- 症状：`.so` 文件路径存在，但链接仍报 `unknown directive: version` -> 原因：文件内容其实是 Git LFS pointer，或 JitPack checkout 没把 pointer 实化成真实二进制 -> 修复：不要只看路径存在，直接检查文件首行/内容、`git ls-tree` blob 大小，以及 JitPack 是否在 native 编译前执行了 LFS 拉取 [Task 2][Task 3]
- 症状：`publishToMavenLocal not found` 或 POM 生成阶段报多个 publication 坐标冲突 -> 原因：JitPack publication/task-discovery 与模块里显式定义的 `release` publication 互相叠加 -> 修复方向：优先排查 publication DSL 和版本来源，不要先动业务代码；当前结论仍需后续 patch 验证 [Task 4]
- 症状：`git switch -c fix/...` 失败并提示 `cannot lock ref 'refs/heads/fix/...'` -> 原因：仓库已有顶层 `fix` 引用冲突 -> 下次直接用简单分支名，例如 `detekt-max-line-length-100` [Task 5]
- 症状：Kotlin 源文件在 Git 中突然显示成 binary diff -> 原因：文件里混入 NUL 字节，本次出现在 `lib-bytes/.../ByteBufferExt.kt` 注释示例中 -> 修复：把实际 NUL 替换为文本 `\\0`，再继续格式化/审查 [Task 5]

# Task Group: Android repository memory and contributor-guideline conventions in /home/yhz61010/StudioProjects/android
scope: 适用于读取/重载 `.claude` 仓库记忆、判断 AGENTS 与 `.claude` 的职责边界、以及把仓库级协作规则落到正确文件的任务。
applies_to: cwd=/home/yhz61010/StudioProjects/android; reuse_rule=这组记忆依赖该仓库的 `.claude` 与 AGENTS 布局；跨仓库只能复用方法，不能直接套用具体规则

## Task 1: 用户更新 `.claude` 后要求重新唤醒记忆，成功确认仓库约束并发现部分记忆链接失效

### rollout_summary_files

- rollout_summaries/2026-05-15T03-05-38-ioP6-reload_claude_memory_from_updated_dot_claude.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T11-05-38-019e2998-c477-7b90-9a24-639baea5c690.jsonl, updated_at=2026-05-15T05:27:44+00:00, thread_id=019e2998-c477-7b90-9a24-639baea5c690, read-only memory reload)

### keywords

- .claude, CLAUDE.md, MEMORY.md, 唤醒记忆, user_language.md, project_cmake_ndk_versions.md, 00-documents, bilingual docs, no Co-Authored-By, stale memory links

## Task 2: 将 bilingual documentation rule 同步写入 AGENTS.md 与 AGENTS.zh-CN.md，成功

### rollout_summary_files

- rollout_summaries/2026-05-15T05-29-03-UN96-jitpack_devicecompat_repo_order_and_local_execution.md (cwd=/home/yhz61010/StudioProjects/android, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T13-29-03-019e2a1c-131d-7272-b80a-5a35699d57a8.jsonl, updated_at=2026-05-15T06:03:08+00:00, thread_id=019e2a1c-131d-7272-b80a-5a35699d57a8, AGENTS sync edit completed)

### keywords

- AGENTS.md, AGENTS.zh-CN.md, bilingual documentation rule, 中文版也更新下, English primary Markdown, CodeX-enforced rules

## User preferences

- 当用户说“唤醒记忆。”以及“我更新的 .claude 内容。现在重新唤醒记忆。” -> 在这个仓库里应把更新后的 `.claude` 内容视为需要重新读取的动态上下文，而不是只依赖旧记忆 [Task 1]
- 在这类记忆重载任务里，默认保持只读；本次“只加载上下文，不修改 Claude 相关文件”没有被用户纠正 -> 除非用户明确要求，否则不要改 `CLAUDE.md` / `.claude/**` [Task 1]
- 当用户说“按你说的追加吧”“对应的中文版也更新下。” -> 若仓库规则需要落到 AGENTS，就同步维护英文和中文 AGENTS 文件，而不是只更新一边 [Task 2]

## Reusable knowledge

- `.claude/rules/personal-style.md` 明确了 commit message 使用英文；`.claude/memory/MEMORY.md` 汇总了文档目录、双语文档、英文代码注释/提交、语言偏好、禁止 `Co-Authored-By`、CMake/NDK 版本等记忆入口 [Task 1]
- 当前已确认的仓库约束包括：文档应放在 `./00-documents`，不是 `./docs`；生成文档时要有英文和中文版本；代码注释与 git commit message 用英文；不要添加 `Co-Authored-By: Claude ...` [Task 1]
- 这个仓库把 `.claude/**` 视为补充指导，但需要直接执行的 CodeX 规则应落在 `AGENTS.md` / `AGENTS.zh-CN.md`；本次 bilingual-doc rule 已双向同步，后续先读 AGENTS 可更快命中该政策 [Task 2]

## Failures and how to do differently

- 症状：直接读取 `.claude/memory/user_language.md` 或引用的 project memory 文件时报 `No such file or directory` -> 原因：`MEMORY.md` 中的链接可能已陈旧 -> 修复：先用定向 `find`/文件名过滤验证文件还在，再决定是否引用 [Task 1]
- 症状：`rg --files /home/yhz61010/.claude` 输出过大、不利于快速确认 -> 原因：范围过宽 -> 修复：查单个记忆文件是否存在时优先用 filename 定向搜索 [Task 1]

# Task Group: Android contributor-guide authoring for /home/yhz61010/NST/AndroidProjects/starpaypro
scope: 适用于 `starpaypro` 多模块 Android/Gradle 仓库中的贡献指南、仓库概览和文档产出约束；用于类似 `AGENTS.md`、协作文档或仓库导览任务，不用于通用 Android 仓库推断。
applies_to: cwd=/home/yhz61010/NST/AndroidProjects/starpaypro; reuse_rule=仓库结构、模块名、依赖布局、提交风格均是 checkout-sensitive，只有在同仓库或紧密相关分支下才可直接复用

## Task 1: 为 starpaypro 生成并修订中文 AGENTS.md 贡献者指南，成功

### rollout_summary_files

- rollout_summaries/2026-05-15T06-54-59-JfmT-starpaypro_agents_md_contributor_guide.md (cwd=/home/yhz61010/NST/AndroidProjects/starpaypro, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T14-54-59-019e2a6a-bf01-7640-bbd0-e537973fbf0f.jsonl, updated_at=2026-05-15T06:58:45+00:00, thread_id=019e2a6a-bf01-7640-bbd0-e537973fbf0f, validated with UTF-8 check and repo inspection)

### keywords

- AGENTS.md, starpaypro, Android Gradle, multi-module, UTF-8, 文档用中文写, 文件名要用英文, settings.gradle, shared-libs, Kover, MockK, conventional commits

## User preferences

- 在类似文档任务里，用户先要求“用中文与我对话”，随后又要求“文档用中文写，编码 utf-8。文件名要用英文。” -> 默认使用中文回复；文档正文直接写中文；文件内容保持 UTF-8；文件名保持英文，不要把文件名本地化 [Task 1]

## Reusable knowledge

- `starpaypro` 是多模块 Android Gradle 仓库；根模块至少包括 `app`、`library-common`、`library-common-compose`、`library-http`、`library-print`、`library-scan`、`module-credit_pay`、`module-felica`、`module-point`、`module-point-compose`，写贡献指南前可以先从 `settings.gradle` 抓模块真相 [Task 1]
- `app/build.gradle` 明确使用 Java 17 / Kotlin JVM 17，并启用了 `viewBinding`、`dataBinding`、`compose`；这决定了文档里关于构建环境和 UI 技术栈的表述 [Task 1]
- `module-point-compose/build.gradle.kts` 启用了 `org.jetbrains.kotlinx.kover`，测试依赖含 `io.mockk:mockk:1.13.13`；涉及测试与覆盖率时可直接引用这些事实 [Task 1]
- `shared-libs/` 下有本地 AAR/JAR，例如 `PayLib-release-2.0.33.aar`、`SUNMI_CUSTOMER_API_v1.0.48_release.aar`；这类依赖兼容性敏感，适合在贡献指南里提醒不要随意替换 [Task 1]
- 最近提交历史里出现 `fix(dcd): ...`、`feat(dcd): ...` 这类 Conventional Commit 风格；在 PR/提交建议里可优先推荐 `type(scope): summary`，但不要假设全仓库只允许一种格式 [Task 1]

## Failures and how to do differently

- 症状：初版 `AGENTS.md` 用英文正文，随后被用户纠正 -> 原因：文档语言默认值没有跟随用户“用中文与我对话”“文档用中文写”的明确要求 -> 下次直接产出中文正文，避免返工 [Task 1]
- 症状：文档中的构建命令示例可能写成假定 flavor 名 -> 原因：多模块 Android 仓库的变体矩阵复杂，不能从常见命名习惯直接猜 -> 下次引用 `assemble...` 之类命令前，先根据实际 flavors/source sets 再核对一次 [Task 1]

# Task Group: Repository guideline authoring requests for /home/yhz61010/StudioProjects/dcd-demo
scope: 记录 `dcd-demo` 中 AGENTS.md 贡献指南请求暴露出来的稳定写作约束；由于该轮中断，没有仓库事实或完成文件可复用。
applies_to: cwd=/home/yhz61010/StudioProjects/dcd-demo; reuse_rule=只有写同仓库或同类 AGENTS 指南时可复用其写作偏好；不要把未验证的 repo 事实当成已知

## Task 1: 用户要求生成简洁的 AGENTS.md contributor guide，但轮次中断未完成

### rollout_summary_files

- rollout_summaries/2026-05-15T06-17-38-x4zg-generate_agents_md_contributor_guide.md (cwd=/home/yhz61010/StudioProjects/dcd-demo, rollout_path=/home/yhz61010/.codex/sessions/2026/05/15/rollout-2026-05-15T14-17-38-019e2a48-8df5-7f10-89eb-ff0b3fe20b12.jsonl, updated_at=2026-05-15T06:20:10+00:00, thread_id=019e2a48-8df5-7f10-89eb-ff0b3fe20b12, turn aborted; preference-only evidence)

### keywords

- AGENTS.md, contributor guide, Repository Guidelines, concise, 200-400 words, repo-specific, descriptive headings, aborted turn

## User preferences

- 当用户要求生成仓库贡献指南并明确说“200-400 words is optimal”“keep explanations short, direct, and specific to this repository” -> 默认写短、直接、仓库特定的版本，不要扩成通用长文 [Task 1]
- 当用户说“adapt as needed — add sections if relevant, and omit those that do not apply” -> 应按仓库实际情况裁剪结构，而不是机械照抄模板 [Task 1]

## Reusable knowledge

- 该类请求的目标文件名是 `AGENTS.md`，标题是 `Repository Guidelines`；用户偏好有描述性标题、可执行解释、必要时少量示例 [Task 1]

## Failures and how to do differently

- 症状：本轮没有产出可验证文件 -> 原因：turn 被中断，未完成仓库检查与草拟 -> 下次先快速读 repo 结构/历史，再在 200-400 词约束内成稿 [Task 1]
