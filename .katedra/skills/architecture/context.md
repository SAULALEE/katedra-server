---
name: context-server
description: System context and backend technology stack
---

# SKILL: SYSTEM CONTEXT & TECHNOLOGY STACK

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Whenever a foundational understanding of Katedra's core functionality (AI content generation) or technology stack is required to scope, design, or build new features in the backend.
- **Exclusion:** Does not handle specific architectural data flows (use `architecture.md`) or database schema details (use `database-jpa-architect.md`).

## 2. STRICT BUSINESS & TECH RULES (T_σ)
- **Product Scope:** Katedra connects teacher preparation with a student learning journey. Teacher authoring/generation and student account registration/login are implemented. Class sharing and student learning APIs remain planned.
- **Teacher Experience:** The existing AI-driven workflow turns a structured syllabus into theory, evaluations, and presentation slides using Spring AI. Class publishing/sharing is future work.
- **Student Experience:** The intended class journey is class → topic/material → activity → response → teacher feedback → next step. AI may clarify prompts or feedback and suggest practice; it must not submit work, change grades, or bypass teacher-defined attempt rules.
- **Target Users:** Teachers preparing learning materials and, when class sharing is implemented, students studying and responding within shared classes.
- **Backend Stack:** Spring Boot (Java 21) with Spring AI, modular monolith organized by domain capabilities.
- **Database Stack:** PostgreSQL, Flyway migrations, UUIDs (`VARCHAR(36)`), soft deletes (`deleted_at`).
- **AI Integration Architecture:** 
  - **Spring AI Integration:** ChatClient abstracts LLM provider communication
  - **Primary AI Provider:** OpenAI (GPT-4, GPT-4-turbo, GPT-4o) with structured JSON output
  - **Provider Flexibility:** Spring AI's abstraction allows switching to Anthropic, Azure OpenAI, or other models with minimal config changes
  - **Async Processing:** All LLM calls use reactive patterns (Mono, CompletableFuture, @Async) to prevent thread blocking and manage resources efficiently
  - Spring Service calls ChatClient.prompt() → async LLM call → structured JSON response → Spring persists results to PostgreSQL

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Goal Verification:** Ensure features support either the teacher preparation/publishing workflow or the student learning/response journey. Do not reject student-facing work merely because it does not generate content with AI.
2. **Stack Alignment:** Validate that solutions strictly use the approved technology stack. Automatically reject unauthorized technologies (e.g., MongoDB, Node.js).
3. **Language Awareness:** Maintain code, variables, and database tables in Spanish (`snake_case` for DB, `camelCase` for Java) to match the established domain language, while keeping system prompts/skills in English.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "What is the core feature of this app and how does it work?"
Output Expected:
> **Core Product:** Katedra connects teacher preparation with a planned student class journey. The teacher generator is implemented; class sharing and student-facing backend workflows remain planned.
> **Architecture:** 
> - **Spring Boot** handles users, authentication, teacher syllabus/material workflows, AI orchestration, and persistence. Student workflows belong to the same modular monolith and require class-scoped authorization.
> - **Spring AI ChatClient** abstracts OpenAI API calls with async/reactive patterns (no thread blocking).
> - **Data Storage:** PostgreSQL (managed by Spring via Flyway). All entities use UUIDs and soft deletes.
> - **Data Flow:** Service → ChatClient.prompt() → OpenAI (async) → structured JSON → persisted to PostgreSQL.
> - **Performance:** All LLM calls are non-blocking (Mono/CompletableFuture) to prevent thread exhaustion.
