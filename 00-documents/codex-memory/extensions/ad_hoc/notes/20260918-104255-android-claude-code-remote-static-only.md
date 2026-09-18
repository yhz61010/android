# Claude Code 的远端静态审查边界

- 用户会同时使用 Codex 与 Claude Code 开发当前项目。
- Claude Code 运行在远端服务器，不在用户本机。
- Claude Code 无法编译代码，也无法执行任何 Gradle 命令。
- Claude Code 的开发和代码审查结论只能来自源码静态分析，不能表述为已完成编译、Gradle、单测、Lint、构建或真机验证。
- Codex 的本机构建与验证权限不受上述限制；交付时必须区分 Claude Code 静态结论、Codex 本机验证证据和未验证项。
