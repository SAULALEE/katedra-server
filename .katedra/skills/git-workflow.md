# SKILL: Git Branching Strategy & Workflow

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Starting a new coding task, creating branches, making commits, pushing code, or proposing merges/PRs in the server repository.
- **Exclusion:** Does not govern coding architecture (use `ARCHITECTURE.md`).

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Branch Protection & Structure:**
  - **`main`**: Production-ready code. Strictly protected. Only merges from `staging`. Never commit directly.
  - **`staging`**: Pre-production testing and integration. Protected. Merges from approved feature branches. Never commit directly.
- **Feature Branches (Temporary):** Must branch off `staging`. Naming must strictly follow the module-based convention:
  - `feature/auth`: Authentication (login, register, session management, JWT integration).
  - `feature/temarios`: CRUD operations and syllabus management.
  - `feature/material`: Materials management (generation, download, storage logic).
  - `feature/generation`: AI prompt and generation execution logic.
  - `feature/pricing`: Plans, subscriptions, and payment flows.
- **Integration Policy:**
  - Feature branches target `staging` for testing and integration.
  - Only merge `staging` into `main` when QA/testing is complete.
- **Descriptive Commits:**
  - **Commit Format:**
    ```
    <type>: <short summary>

    <detailed description (optional)>

    <footer (optional)>
    ```
  - **Commit Types:**
    | Type | Description | Example |
    | --- | --- | --- |
    | `feat` | New feature | `feat: add JWT authentication` |
    | `fix` | Bug fix | `fix: prevent token expiration error` |
    | `chore` | Setup, dependencies, config | `chore: add MySQL driver dependency` |
    | `refactor` | Code change without changing functionality | `refactor: simplify auth service logic` |
    | `test` | Add/modify tests | `test: add auth controller tests` |
    | `docs` | Documentation-only changes | `docs: update API endpoints reference` |
    | `style` | Formatting, indentation, structure | `style: fix import organization` |
- **Explicit Push Authorization:**
  - The AI agent must **never** run `git push` or upload any code/commits to the remote repository unless the human user has explicitly requested it in the chat interface.

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Alignment:** Identify which feature branch matches the requested task (e.g., login changes belong in `feature/auth`).
2. **Branch Creation / Checkout:** 
   - Pull latest changes from `staging`.
   - Checkout or create the corresponding feature branch (e.g., `git checkout feature/auth`).
3. **Commit Quality:** Group changes into small, descriptive commits.
4. **Push & Pull Request:** Push the feature branch to origin and propose a merge targeting `staging` (only when authorized).

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Add login API endpoint"
Output Expected:
> **Branch Identification:** `feature/auth`
> **Git Commands:** 
> ```bash
> git checkout staging
> git pull
> git checkout feature/auth || git checkout -b feature/auth
> ```
> **Implementation:** Create AuthController and JWT logic.
> **Commit:**
> ```bash
> git commit -am "feat: implement login API endpoint"
> ```
> **Wait:** Await user authorization to push to remote.
