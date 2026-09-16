# Performance Optimization

## Model Selection Strategy

**Subagents use the same model as the current session.**

- When invoking any subagent (ECC plugin agents such as `kotlin-reviewer`,
  `cpp-reviewer`, `code-reviewer`, `security-reviewer`, `planner`, `tdd-guide`,
  and built-in agents), always pass the Agent tool's `model` parameter set to
  the current session model, overriding the `model:` declared in the agent
  definition (`sonnet` / `opus` / `haiku`).
- Do not hard-code a specific model name here. Determine the current session
  model at call time (the session environment states it, e.g. "You are powered
  by the model named ...") and map it to the `model` parameter value
  (`fable` / `opus` / `sonnet` / `haiku`).
- `fork` subagents inherit the session model automatically; no override needed.
- Do not downgrade subagents to smaller models for cost savings in this
  project; consistent quality across the main session and its agents takes
  priority.

## Context Window Management

Avoid last 20% of context window for:
- Large-scale refactoring
- Feature implementation spanning multiple files
- Debugging complex interactions

Lower context sensitivity tasks:
- Single-file edits
- Independent utility creation
- Documentation updates
- Simple bug fixes

## Extended Thinking + Plan Mode

Extended thinking is enabled by default, reserving up to 31,999 tokens for internal reasoning.

Control extended thinking via:
- **Toggle**: Option+T (macOS) / Alt+T (Windows/Linux)
- **Config**: Set `alwaysThinkingEnabled` in `~/.claude/settings.json`
- **Budget cap**: `export MAX_THINKING_TOKENS=10000` (bash) or `$env:MAX_THINKING_TOKENS = "10000"` (PowerShell)
- **Verbose mode**: Ctrl+O to see thinking output

For complex tasks requiring deep reasoning:
1. Ensure extended thinking is enabled (on by default)
2. Enable **Plan Mode** for structured approach
3. Use multiple critique rounds for thorough analysis
4. Use split role sub-agents for diverse perspectives

## Build Troubleshooting

If build fails:
1. Use **build-error-resolver** agent
2. Analyze error messages
3. Fix incrementally
4. Verify after each fix
