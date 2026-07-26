---
name: subagent-driven-development
description: Use subagents to execute plans
---

# Subagent-Driven Development

Execute plan by dispatching a fresh implementer subagent per task, a task review (spec compliance + code quality) after each, and a broad whole-branch review at the end.

**Why subagents:** You delegate tasks to specialized agents with isolated context. By precisely crafting their instructions and context, you ensure they stay focused and succeed at their task. They should never inherit your session's context or history — you construct exactly what they need. This also preserves your own context for coordination work.

**Core principle:** Fresh subagent per task + task review (spec + quality) + broad final review = high quality, fast iteration

**Narration:** between tool calls, narrate at most one short line — the ledger and the tool results carry the record.

**Continuous execution:** Do not pause to check in with your human partner between tasks. Execute all tasks from the plan without stopping. The only reasons to stop are: BLOCKED status you cannot resolve, ambiguity that genuinely prevents progress, or all tasks complete.

## When to Use
Use Subagent-Driven Development when:
- You have an implementation plan ready.
- Tasks are relatively independent.
- You want to maintain a clean main session context.

## Model Selection

Use the least powerful model that can handle each role to conserve cost and increase speed.
- **Mechanical implementation tasks** (isolated functions, clear specs, 1-2 files): use a fast, cheap model.
- **Integration and judgment tasks** (multi-file coordination, pattern matching, debugging): use a standard model.
- **Architecture and design tasks**: use the most capable available model.

## Verification & TDD
Always verify your work and the work of your subagents using TDD principles (see `test-driven-development.md`). Run the backend tests using:
```bash
./mvnw test -Dtest=TestClassName
```
Ensure all tests pass before proceeding to the next task.
