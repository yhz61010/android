# DCD Codex memory and AGENTS sync rule

- For `/home/yhz61010/StudioProjects/dcd-demo`, keep `AGENTS.md` body in Chinese.
- The remote Claude path `~/yhz61010/Documents/DCD/` maps locally to `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/`.
- Do not modify Claude-related files such as repository `CLAUDE.md` or `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Claude/**`.
- Store DCD-specific Codex-readable memory copies under `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/Codex/`.
- When updating the repository `AGENTS.md`, also copy it to `/home/yhz61010/NST/02 Claude For StarPay/01-StarPay/20-DCD/AGENTS.md`.
- Current DCD Demo integration should be checked against code: `HttpCommandSender` uses HTTP polling against `dcd-server`, while `dcd-server` forwards to StarPay Pro over WebSocket.
