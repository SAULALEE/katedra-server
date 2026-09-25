---
name: decisions-server
description: Architectural decision records and policy keeper
---

# SKILL: ARCHITECTURAL DECISION RECORDS (ADR) & POLICY KEEPER

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Whenever proposing a new technical solution, choosing a library, altering the architecture, or questioning an existing implementation in `katedra-server`.
- **Exclusion:** Does not apply to trivial refactors or business logic details.

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Authority:** ADRs guide decisions but can evolve. Proposed changes must be justified against business goals (Scalability, Development Speed, Portfolio Value).
- **Core Stack:** 
  - **Spring Boot (Java 21) with Spring AI** - Backend API, teacher AI orchestration, data persistence
  - **PostgreSQL, Flyway** - Data persistence
- **Product domains:** Katedra supports teacher preparation/publishing and a student learning journey. Keep both within the existing modular monolith, with `teacher` and `student` domain boundaries; do not infer student backend functionality from the client landing or requirements draft.
- **Student authorization:** Student data access must be based on verified class membership and scoped relationships. Define invitation/enrollment, submission, feedback, attempt, and result rules before designing the student persistence/API contract.
- **Monolithic Architecture with Integrated AI:** Spring Boot handles shared identity, teacher workflows, and planned student workflows; Spring AI remains the LLM abstraction layer. Student-facing AI help must be contextual and must not perform or grade student work unless a separately approved policy defines it.
- **Justification:** Spring AI provides async-native LLM integration without service overhead. Eliminates thread-blocking issues and reduces operational complexity (1 service instead of 2).

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Consultation:** Search/Read the project context and existing ADRs to identify the constraints of the requested feature.
2. **Alignment:** Validate if the solution aligns with the "Spring Boot 3.x + Spring AI + PostgreSQL" stack.
3. **Implementation Decision:** 
   - Teacher preparation and publishing → `teacher` domain in Spring Boot.
   - Student classes, activities, submissions, and feedback → `student` domain in the same Spring Boot monolith, after defining class-level access rules.
   - Shared identity and infrastructure → common packages; avoid leaking role-specific business logic into shared services.
4. **Integration:** Use Spring AI's ChatClient for LLM calls. Always use **async/reactive** patterns (Mono, CompletableFuture) to avoid thread-blocking. Never use synchronous LLM calls.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Should I add a new feature for generating exam questions from syllabi?"
Output Expected:
> **Check:** Current architecture is Spring Boot 3.x with Spring AI integrated.
> **Decision:** Implement in Spring using ChatClient (Spring AI). Use async patterns (Mono or CompletableFuture).
> **Implementation:** Service calls `chatClient.prompt()` → async LLM call to OpenAI → structured JSON response → save to DB.
> **Critical:** Never use blocking calls. Always use `@Async`, Mono, or CompletableFuture to avoid thread exhaustion.
