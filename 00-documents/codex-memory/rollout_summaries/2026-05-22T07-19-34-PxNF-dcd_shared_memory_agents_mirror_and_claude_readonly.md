thread_id: 019e4e8d-c463-73d0-a3d4-d724ecc523b3
updated_at: 2026-05-25T05:07:33+00:00
rollout_path: /home/yhz61010/.codex/sessions/2026/05/22/rollout-2026-05-22T15-19-34-019e4e8d-c463-73d0-a3d4-d724ecc523b3.jsonl
cwd: /home/yhz61010/StudioProjects/dcd-demo
git_branch: dcd-svr-ws

# The user established a durable DCD memory workflow: keep Claude files read-only, use `AGENTS.md` as the entrypoint, and mirror Codex memory into the local `20-DCD/Codex/` tree for future sessions.

Rollout context: The work happened in `/home/yhz61010/StudioProjects/dcd-demo`. The user repeatedly refined where memory should live and how future Codex sessions should recover prior context. The most durable outcome was a shared-memory pattern: `AGENTS.md` should tell new sessions to load `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/`, and Claude-related files should never be edited.

## Task 1: Update DCD AGENTS.md and establish shared-memory loading

Outcome: success

Preference signals:
- The user said: “StarPay Pro App 项目路径是 `/home/yhz61010/NST/AndroidProjects/starpaypro`。将这一点更新到 AGENTS.md 中。” -> future DCD/StarPay work should keep the StarPay Pro app path explicit in `AGENTS.md`.
- The user said: “记住，任何时候都不要修改 Claude 的文件。” -> treat `CLAUDE.md`, `.claude/**`, and external `Claude/**` as read-only forever; only write conversions or notes into Codex-owned locations.
- The user said: “将 Codex 的主记忆，复制到 `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex` 下面…以后主记忆更新时，也要同时更新到那个目录里。” -> future main-memory updates should also sync the DCD mirror.
- The user asked how a fresh clone should “告诉你回忆起以前的记忆” and later approved the implementation approach -> the repository should provide a clear startup path that points Codex to the shared memory mirror.
- The user later said: “唤醒记忆” -> a short trigger phrase is acceptable if `AGENTS.md` already points to the shared-memory loading steps.

Key steps:
- Read the current repo `AGENTS.md`, the local `/home/yhz61010/.codex/memories/MEMORY.md`, and the DCD mirror files to infer the durable rules.
- Added a `共享记忆加载` section to `AGENTS.md` that tells new sessions to read `memory_summary.md`, `MEMORY.md`, `extensions/ad_hoc/notes/`, and task-matched `rollout_summaries/` from `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/`.
- Added the StarPay Pro app path explicitly in `AGENTS.md`: `/home/yhz61010/NST/AndroidProjects/starpaypro`.
- Synchronized the updated `AGENTS.md` to `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/AGENTS.md`.
- Mirrored the main Codex memory into `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/` and recorded a note that future main-memory updates must also sync there.

Reusable knowledge:
- New clone sessions should start from repository `AGENTS.md`; that file now explicitly tells Codex where the shared memory mirror lives and what to read first.
- The shared-memory path is `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/current-codex-memories/`; the DCD conversion note is `converted-claude-memory.md`.
- Current DCD integration facts that should be preserved in future prompts/docs: `HttpCommandSender` -> `dcd-server` HTTP/polling -> WebSocket -> StarPay Pro; signature formula remains `SHA256(aid + orderNo + amount + timestamp + aKey)`.
- The main DCD review focus stays on `BUSY`, `CANCEL`, `pairingCode + orderNo` isolation, pending-result lifecycle, and `RefundFee` semantics.

Failures and how to do differently:
- A first attempt at comparing mirror contents was misleading because path counting was done against the wrong base; later checks used relative-path counts and `diff -qr` to confirm the mirror matched the source.
- The user interrupted one copy step, so the external `AGENTS.md` sync had to be rerun before verification.
- Some older memory entries still mention historical or remote paths, but they are only valid when framed as mappings or historical corrections; future agents should not treat them as active local paths.

References:
- [1] `AGENTS.md` now contains `## 共享记忆加载` with the exact mirror path and loading order.
- [2] `AGENTS.md` also contains `StarPay Pro App 项目路径是 /home/yhz61010/NST/AndroidProjects/starpaypro`.
- [3] `20-DCD/Codex/README.md` and `20-DCD/Codex/converted-claude-memory.md` describe the local DCD memory mirror and the path mapping from remote Claude docs.
- [4] Mirror sync verification succeeded with `diff -qr -x .git -x .agents -x .codex` showing no differences between `/home/yhz61010/.codex/memories` and `.../20-DCD/Codex/current-codex-memories`.
- [5] The user’s one-word trigger “唤醒记忆” is enough once `AGENTS.md` points to the shared-memory loading steps.
