---
name: context-server
description: System context and backend technology stack
---

# SKILL: SYSTEM CONTEXT & TECHNOLOGY STACK

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Whenever a foundational understanding of Katedra's core functionality (AI content generation) or technology stack is required to scope, design, or build new features in the backend.
- **Exclusion:** Does not handle specific architectural data flows (use `architecture.md`) or database schema details (use `database-jpa-architect.md`).

## 2. STRICT BUSINESS & TECH RULES (T_σ)
- **Core App Functionality:** Katedra is an **AI-driven academic content generator**. Its primary engine takes a structured syllabus and automatically creates complete educational materials (theory, practical exercises, evaluations, and presentation slides) using LLMs. 
- **Target Audience:** Teachers (designed specifically to save 30-40% of manual preparation time).
- **Backend Stack:** Spring Boot 3.x (Java 21) with Spring AI, Modular Monolith architecture.
- **Database Stack:** MySQL 8.0, Flyway migrations, UUIDs (`CHAR(36)`), soft deletes (`deleted_at`).
- **AI Integration Architecture:** 
  - **Spring AI Integration:** ChatClient abstracts LLM provider communication
  - **Primary AI Provider:** OpenAI (GPT-4, GPT-4-turbo, GPT-4o) with structured JSON output
  - **Provider Flexibility:** Spring AI's abstraction allows switching to Anthropic, Azure OpenAI, or other models with minimal config changes
  - **Async Processing:** All LLM calls use reactive patterns (Mono, CompletableFuture, @Async) to prevent thread blocking and manage resources efficiently
  - Spring Service calls ChatClient.prompt() → async LLM call → structured JSON response → Spring persists results to MySQL

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Goal Verification:** Ensure any new proposed feature directly serves the primary core goal: **Generating academic content via AI**. Reject features that deviate from this purpose.
2. **Stack Alignment:** Validate that solutions strictly use the approved technology stack. Automatically reject unauthorized technologies (e.g., MongoDB, Node.js).
3. **Language Awareness:** Maintain code, variables, and database tables in Spanish (`snake_case` for DB, `camelCase` for Java) to match the established domain language, while keeping system prompts/skills in English.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "What is the core feature of this app and how does it work?"
Output Expected:
> **Core Feature:** Katedra is an AI content generator for teachers. It automatically creates theory, exercises, and evaluations from syllabuses using OpenAI's GPT-4/4o models.
> **Architecture:** 
> - **Spring Boot 3.x** handles users, authentication, syllabi CRUD, AI orchestration, and persistence.
> - **Spring AI ChatClient** abstracts OpenAI API calls with async/reactive patterns (no thread blocking).
> - **Data Storage:** MySQL 8.0 (managed by Spring via Flyway). All entities use UUIDs and soft deletes.
> - **Data Flow:** Service → ChatClient.prompt() → OpenAI (async) → structured JSON → persisted to MySQL.
> - **Performance:** All LLM calls are non-blocking (Mono/CompletableFuture) to prevent thread exhaustion.
