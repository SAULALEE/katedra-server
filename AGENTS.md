# Antigravity Rules

**CRITICAL RULE: You MUST read and follow `.katedra/skills/00-core/caveman-method.md` BEFORE taking any action.**

This file configures Antigravity for the Katedra project.

## Skills and Rules
All project-specific rules are located in `.katedra/skills/`. You must search and read these files before implementing features. Treat the files in that directory as your "Skills".

## Workflows & Capabilities
- You are highly encouraged to use your `invoke_subagent` tool for complex, multi-step tasks. Refer to `.katedra/skills/workflows/subagent-driven-development.md` for the standard operating procedure.
- Always verify your work and the work of your subagents using TDD principles (`.katedra/skills/workflows/test-driven-development.md`).
- Do not run destructive database commands (DROP, DELETE) without explicit user approval.
- Use `grep_search` and `list_dir` aggressively to find context before asking the user.
