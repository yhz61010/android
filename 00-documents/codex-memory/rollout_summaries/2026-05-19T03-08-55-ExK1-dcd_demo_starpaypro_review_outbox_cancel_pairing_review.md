thread_id: 019e3e35-389b-75a1-be8d-ccafcdc9b7f9
updated_at: 2026-05-19T07:41:18+00:00
rollout_path: /home/yhz61010/.codex/sessions/2026/05/19/rollout-2026-05-19T11-08-55-019e3e35-389b-75a1-be8d-ccafcdc9b7f9.jsonl
cwd: /home/yhz61010/StudioProjects/dcd-demo

# 对 DCD Demo、dcd-server 与 StarPay Pro DCD 代码做了多轮代码审查，并围绕 outbox、CANCEL、配对隔离、DCD 设置入口与退款校验形成了可执行修复方案。

Rollout context: 用户要求审查 `/home/yhz61010/StudioProjects/dcd-demo`、`/home/yhz61010/StudioProjects/dcd-demo/dcd-server`，以及 StarPay Pro 仓库中 `app/src/main/java/jp/co/netstars/starpay/dcd/`、相关文件和 `HelpActivity.kt`，目标是“确保要满足业务需求”。后续用户明确把 NodeJS 端视为 Demo，审查重点应放在 StarPay Pro 生产代码。

## Task 1: 首轮范围确认与业务基准建立

Outcome: partial

Preference signals:
- when用户要求“代码 Review。包括 ... 以及 HelpActivity.kt。要确保要满足业务需求。” -> future reviews should be organized around business requirements and include cross-repo impact, not just single file diffs
- when用户补充“NodeJS 相关的代码只是 Demo，没有大问题就行。Review 时该项目不是重点。” -> future reviews should prioritize StarPay Pro production code; dcd-server should only be checked for obvious demo-level protocol issues

Key steps:
- 先读两个仓库的 `AGENTS.md`、DCD Demo `CLAUDE.md` 和 DCD WebSocket 设计/使用/并发文档，确认当前业务语义：HTTP 轮询 DCD Server，再由 WebSocket 到 StarPay Pro；签名公式仍是 `SHA256(aid + orderNo + amount + timestamp + aKey)`。
- 发现 `dcd-demo` 根目录有 `.git`，但之前有一次读 `AGENTS.md` 时出现“文件不存在”的瞬时不一致；后续通过目录确认恢复正常。
- 审查时把 NodeJS 端降级为 Demo 级别，只检查是否会明显误导联调或破坏协议语义。

Failures and how to do differently:
- 第一次审查没有按用户后来强调的权重收敛，导致把 dcd-server 的多租户/原子性问题提得过重；后续改成仅把它当 Demo 风险。
- `node -e "require('./src/db')"` 因本机 Node v24 与 `better-sqlite3` ABI 不匹配失败，错误是 `NODE_MODULE_VERSION 115` vs `137`；这不是代码语法错误，后续若要做运行时验证，需先重建 native 模块或换匹配 Node 版本。

Reusable knowledge:
- DCD Demo 当前业务链路：`HttpCommandSender` → DCD Server HTTP → DCD Server WebSocket → StarPay Pro。
- StarPay Pro 侧 DCD 相关代码核心文件是 `DcdMmkvCache.kt`、`DcdCommandListener.kt`、`DcdCallbackActivity.kt`、`DcdWebSocketClient.kt`、`DcdListenerService.kt`、`DcdSettingsActivity.kt`，以及 `HelpActivity.kt`。

References:
- [1] `/home/yhz61010/StudioProjects/dcd-demo/AGENTS.md`（中文仓库指南，说明当前 demo 链路与文档约定）
- [2] `/home/yhz61010/StudioProjects/dcd-demo/docs/2026-05-19-dcd-websocket-demo-usage-guide.md`（HTTP + WebSocket Demo 使用流程）
- [3] `/home/yhz61010/StudioProjects/dcd-demo/docs/superpowers/specs/2026-05-18-dcd-websocket-server-design.md`（WebSocket 目标架构与签名约束）
- [4] `node -e "require('./src/db')"` 失败：`NODE_MODULE_VERSION 115` vs `137`（native 模块 ABI 不匹配）

## Task 2: StarPay Pro DCD 代码首轮 Review 与用户反馈后的方案收敛

Outcome: partial

Preference signals:
- when用户说“我的修改方案你看一下”并给出分项修复 -> future responses should critique the proposed fix against actual code, not just restate design intent
- when用户说“我一会让 Claude Code 重新改一下，到时候你再Review下。” -> future workflow should expect iterative review cycles and keep feedback precise so it can be checked later

Key steps:
- 发现并确认了多个高风险问题：
  - `DcdCallbackActivity` 保存 outbox 后又调用 `clearAllState()`，会清掉 pending result；并且 outbox JSON 里原先缺少 `type: "RESULT"`，重发时服务端识别不了。
  - `HelpActivity` 中普通设置走密码，但 DCD 设置入口仍直接 `startActivity`，未受密码保护。
  - `DcdMmkvCache.isProcessing` 的 3 分钟超时自动释放会破坏 CANCEL/物理支付的业务语义。
  - `dcd-server` 仍以 `orderNo` 为全局主键，跨 pairingCode 仍可能覆盖/读取/取消错单。
  - `DcdCommandListener` 对缺字段的 COMMAND 静默丢弃，会让 Demo 只能等超时。
- 用户提出具体修复方案后，逐项校验并修正建议：
  - `pendingResult` 生命周期独立，不能在 `savePendingCommand()` / `markCanceled()` / `clearAllState()` 中被无条件清除。
  - 需要统一构建 `RESULT` 消息 JSON，outbox 和实时发送使用同一格式。
  - 完整隔离应改成 `(pairingCode, orderNo)` 复合主键，而不仅是 query 参数校验。
  - malformed COMMAND 的 `REFUND` 不能按 `amount > 0` 拒绝，因为 Demo 的退款金额是 `refundFee`。
- 在用户要求下，再次明确：NodeJS 端只是 Demo，低成本隔离增强可以接受，但不作为生产阻断项。

Failures and how to do differently:
- 最初把 `REFUND` 与 `PAY` 共用的 `amount` 校验视为通用规则，后来通过 `IntentFactory` / `IntentRequest.isValidRefund()` 验证发现：退款语义实际上用 `RefundFee`，`amount` 不能作为退款拒绝条件。
- `DcdSettingsActivity` 在保存配置时仍调用 `clearAllState()`，这会误删 pending result；这个点在后续审查里仍然是未修复阻断项。
- `HelpActivity` 的 DCD 设置入口仍未加密码保护，说明修改方案与实际代码存在偏差，后续复查要逐文件确认，不只看用户描述。

Reusable knowledge:
- `DcdCommandListener` 现有校验顺序是：先取 `orderNo`，再校验必填字段、金额、签名，再看 `isProcessing || hasPendingResult`，最后启动 Intent V2。
- `DcdCallbackActivity` 现在会用 `DcdWebSocketClient.buildResultJson(resultMap)` 统一生成结果 JSON，并在发送失败时保存到 outbox。
- `DcdMmkvCache` 已把 `hasPendingResult` 纳入状态机，`clearProcessingState()` 与 `clearPendingResult()` 分离，但 `isProcessing` 仍带 3 分钟自动清理副作用。
- `dcd-server` 已改成 `(pairingCode, orderNo)` 复合主键，`routes.js` 的 result/cancel 也要求 `pairingCode` 查询参数；`wsManager.js` 更新状态时已传 `pairingCode`。

References:
- [1] `app/src/main/java/jp/co/netstars/starpay/dcd/DcdMmkvCache.kt`：`hasPendingResult`、`clearProcessingState()`、`clearAllState()`、`isProcessing` 3 分钟超时
- [2] `app/src/main/java/jp/co/netstars/starpay/dcd/DcdCallbackActivity.kt`：`buildResultJson()` / outbox 保存
- [3] `app/src/main/java/jp/co/netstars/starpay/dcd/DcdWebSocketClient.kt`：`buildResultJson(resultMap)`、`sendResult(resultMap)`、`resendPendingResult()`
- [4] `app/src/main/java/jp/co/netstars/starpay/dcd/DcdCommandListener.kt`：`MISSING_REQUIRED_FIELDS`、`INVALID_AMOUNT`、`SIGNATURE_MISMATCH`、`hasPendingResult` BUSY 判断
- [5] `app/src/main/java/jp/co/netstars/starpay/ui/activity/HelpActivity.kt`：`btn_dcd_settings` 仍直接打开 `DcdSettingsActivity`
- [6] `dcd-server/src/db.js` / `routes.js` / `wsManager.js`：复合主键与 pairingCode 校验

## Task 3: 针对“问题 2”的修复建议复核

Outcome: uncertain

Preference signals:
- when用户给出“问题 2 修复方案”并指出 `tradeAction != "QUERY"` 会误伤 `REFUND` -> future review should check the exact protocol semantics of each action, not assume one validation rule fits all
- when用户说“NodeJS 相关的代码只是 Demo，没有大问题就行” -> keep the review weight centered on StarPay Pro; Demo-side issues should be weighed only if they affect business semantics

Key steps:
- 结合 DCD Demo 的 `PaymentViewModel` 和 `library-common` 的 Intent 校验逻辑核对：
  - PAY 使用 `OrderAmount` / `amount > 0`。
  - REFUND 使用 `RefundFee`，`OrderAmount` 不是退款有效性依据。
  - `IntentRequest.isValidRefund()` 对 QR 的退款要求 `refundFee > 0`，Felica 退款要求 `transactionId` 非空。
- 结论：用户提出的把 `tradeAction != "QUERY"` 改成 `tradeAction == "PAY"` 的方向是正确的，能直接修掉合法退款被拒的问题。
- 但“完全不需要额外校验 refundFee”仍然存在争议：DCD 层如果完全不做最小协议校验，`PROCESSING` 之后可能在远端业务校验失败，Demo 端会陷入等待/超时；更稳妥的是最少做 `PAY` 与 `REFUND` 的分别校验。

Failures and how to do differently:
- 不要把 `amount` 当成所有交易动作的统一必填值；这在现有 Intent 协议里是不成立的。
- Review 时若发现协议级别的语义差异，应回到 `IntentFactory` / `IntentRequest` 这类底层约束文件核对，而不是只看上层 ViewModel。

Reusable knowledge:
- `library-common/src/main/java/com/library/android/netstars/common/intent/IntentFactory.kt` 里，PAY 读取 `KEY_ORDER_AMOUNT`，REFUND 读取 `KEY_REFUND_FEE`。
- `library-common/src/main/java/com/library/android/netstars/common/intent/IntentRequest.java` 里，`isValidPay()` 要求 `orderAmount > 0`，`isValidRefund()` 主要看 `refundFee > 0` 与 `transactionId`。

References:
- [1] `app/src/main/java/jp/co/netstars/starpay/dcd/DcdCommandListener.kt:46-57`（当前金额校验与签名校验位置）
- [2] `library-common/src/main/java/com/library/android/netstars/common/intent/IntentFactory.kt:37-55`（PAY / REFUND 的字段映射）
- [3] `library-common/src/main/java/com/library/android/netstars/common/intent/IntentRequest.java:152-182`（PAY / REFUND 有效性校验）
- [4] `app/src/main/java/com/starpay/dcd/ui/payment/PaymentViewModel.kt:81-126`（DCD Demo 退款使用 `refundFee`）

## Task 4: 接收“后续重新 Review”的工作方式

Outcome: success

Preference signals:
- when用户说“我一会让 Claude Code 重新改一下，到时候你再Review下。” -> future workflow should wait for a fresh review cycle after changes, not prematurely close the issue
- when用户说“注意 NodeJS 相关的代码只是 Demo，没有大问题就行。Review 时该项目不是重点。” -> future reviews should deprioritize demo-only Node correctness and focus on business-critical StarPay Pro code

Reusable knowledge:
- 用户对 review 的默认期待是：先明确业务语义，再用编译/语法/实际文件内容验证，不接受只凭口头方案就下结论。
- 用户允许把 NodeJS 侧的细节降级处理，只要不明显误导联调或破坏协议语义即可。

References:
- 用户原话：“NodeJS 相关的代码只是 Demo，没有大问题就行。Review 时该项目不是重点。”
- 用户原话：“我一会让 Claude Code 重新改一下，到时候你再Review下。”
- 验证结果：`./gradlew test`（dcd-demo）成功；`node --check src/db.js src/routes.js src/wsManager.js` 成功；StarPay Pro `./gradlew :app:processStarPay_Test_CommonAndroidDebugManifest` 与 `./gradlew :app:compileStarPay_Test_CommonAndroidDebugKotlin` 成功。
