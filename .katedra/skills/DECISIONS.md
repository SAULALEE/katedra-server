# SKILL: ARCHITECTURAL DECISION RECORDS (ADR) & POLICY KEEPER

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Whenever proposing a new technical solution, choosing a library, altering the architecture, or questioning an existing implementation in `katedra-server`.
- **Exclusion:** Does not apply to trivial refactors or business logic details.

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Authority:** ADRs are the final word. If a proposed change contradicts an established ADR (e.g., ADR-002: Modular Monolith), the agent must halt and flag the conflict.
- **Core Stack:** Spring Boot 3.x (Java 21), MySQL 8.0, and Flyway. No deviations allowed.
- **Justification:** Every architectural change must be mapped to a business goal (Scalability, Development Speed, Portfolio Value).

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Consultation:** Search/Read the project context and existing ADRs to identify the constraints of the requested feature.
2. **Alignment:** Validate if the solution aligns with the "Spring Boot + MySQL" stack and "Modular Monolith" pattern.
3. **Drafting:** Propose the implementation. If it introduces a new technology or structural change, format it as a mini-ADR entry.
4. **Rejection:** Strictly reject polyglot architectures (e.g., adding NestJS or Node microservices) or microservices overhead for this 1-dev team scope.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Should I use a separate Node.js microservice for the new reporting module?"
Output Expected:
> **Check:** Current architecture is "Monolito modular en Spring Boot" (ADR-002).
> **Decision:** Rejected. Adding a Node.js microservice creates a polyglot architecture and network overhead, increasing complexity for a 1-dev team.
> **Proposed Path:** Implement the reporting module as a new package within the existing `katedra-server` Spring Boot project.
