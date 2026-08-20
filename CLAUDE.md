# Development Workflow

**CRITICAL RULE: MUST read and follow `.katedra/skills/00-core/caveman-method.md` BEFORE taking any action.**

Expert developer workflow for the Katedra server project:
Before writing any code or proposing architecture, you MUST review the relevant skill files in `.katedra/skills/`.

## Workflow
1. Analyze the user's request.
2. Identify the domain (e.g., architecture, database, security, workflows).
3. Read the relevant `.md` files in `.katedra/skills/` to understand the project's standards.
4. Apply the rules strictly in your implementation.
5. You may use subagents for complex tasks (refer to `.katedra/skills/workflows/subagent-driven-development.md`), but you **MUST explicitly ask the user for authorization before invoking any subagents**.
6. Provide concise, direct code solutions. Avoid verbose explanations unless explicitly requested.
7. Communicate in the language requested by the user, but write all code, comments, and commit messages in English.
