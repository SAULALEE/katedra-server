---
name: content-generation-prompts
description: Instructor-grade prompt strategy for AI content generation (teoria, ejercicios, evaluacion, diapositivas) — level rubric, model tiers, and roadmap
---

# SKILL: AI Content Generation Strategy (Katedra Core)

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Writing or editing prompts in `PromptTemplates`, adding a new generated
  material type, or touching `ModeloIA` / `NivelAcademico`.
- **Exclusion:** Does not handle the async/ChatClient plumbing (use `ai-integration.md`)
  or REST routing (use `architecture.md`).

## 2. WHERE PROMPTS LIVE
Prompts are **content, not code** — they are never Java string literals. Every system
prompt is a file under `src/main/resources/prompts/*.st`, loaded via Spring AI's
`org.springframework.ai.chat.prompt.PromptTemplate` (backed by the StringTemplate/ANTLR
engine already on the classpath via `spring-ai-template-st`, transitively pulled in by
`spring-ai-openai`). Static prompts (no variables) are read once as plain text; prompts
with variables use `{placeholder}` substitution via `PromptTemplate.render(Map<String,Object>)`.
`PromptTemplates.java` (`config/`) is the only class that touches these files — it stays a
static utility that returns fully-rendered strings, so callers never deal with resources.

When adding a new prompt: create `src/main/resources/prompts/<name>.st`, load it once
(`readResource` for static text, `loadTemplate` + a `build...()` method for dynamic text),
never inline prompt text back into `.java` files.

## 3. THE INSTRUCTOR METHOD
Every generation prompt must read as if written by an experienced instructor planning a
lesson, not a generic assistant. Concretely:
- Open every system prompt with the shared `prompts/context.st` preamble so the model
  never loses the persona (Katedra's content engine, catedrático + diseñador instruccional).
- Prefer a **flexible structure with a required spine** over rigid fixed headings: let
  the model choose section titles that fit the specific topic, but always require
  (1) a hook/context opener, (2) progressive development, (3) at least one worked
  example tied to the asignatura, (4) a closing synthesis.
- Forbid filler, repetition, and generic closing phrases ("en conclusión...").
- Never ask for JSON/code fences around prose content (teoria, ejercicios); reserve
  Structured Outputs (`.entity()`) for content that is inherently structured
  (evaluacion, diapositivas).

## 4. `NivelAcademico` — always saved, always read
`Temario.gradoAcademico` is a **required** 5-band enum (never free text):

| Enum | Register |
|---|---|
| `PRIMARIA` | frases muy cortas, lenguaje cotidiano, ejemplos visuales |
| `SECUNDARIA` | términos técnicos con definición, ejemplos guiados |
| `BACHILLERATO` | mayor rigor, conexión entre conceptos, notación básica |
| `UNIVERSITARIO` | rigor conceptual, notación formal, razonamiento explícito |
| `POSGRADO` | profundidad teórica, matices, límites, estado del arte |

Every generation service method that talks to the model must accept a `NivelAcademico`
and inject `nivel.getRubrica()` into the system prompt, and `nivel.getEtiqueta()` into
the user prompt — the level must never be silently defaulted or dropped. The same topic
must read differently across levels; if two levels produce near-identical prose, the
prompt is under-specified.

## 5. `ModeloIA` — two branded tiers, not raw model ids
The API and persisted data (`ContenidoTemario.modelo`) expose only the **tier key**
(`flash`/`pro`), never a raw OpenAI model id — keeps the contract provider-agnostic
(Spring AI can swap providers with a config change) and avoids leaking implementation
details to clients.

| Tier | Display name | Model | Teoría párrafos | Preguntas examen | Diapositivas | Options |
|---|---|---|---|---|---|---|
| `FLASH` | Tutor | `gpt-4.1-mini` | 5–15 (default 8) | 1–10 (default 5) | 5–10 (default 8) | temperature 0.5, maxTokens 2500 |
| `PRO` | Catedrático | `o4-mini` (reasoning) | 20–40 (default 20), capped at 5000 words | 15–30 (default 20) | 10–20 (default 15) | reasoningEffort=medium, **no temperature**, maxCompletionTokens 12000 |

The exact paragraph/question/slide count within each tier's range is user-selectable per
request (`GenerarMaterialRequestDTO.numeroParrafos`/`numeroPreguntas`/`numeroDiapositivas`);
a null value falls back to the tier's default. `ContenidoTemarioService` validates the
requested value against the selected tier's `[min, max]` before dispatching, rejecting
out-of-range values with 400.

**Avoid wasting tokens:** reasoning models bill hidden "thinking" tokens in addition to
the visible answer (a 500-token answer can consume 2000+ total tokens). Only `PRO` uses
a reasoning model, with a bounded `reasoningEffort` and `maxCompletionTokens` cap — never
let the cheap tier accidentally call a reasoning model, and never set `temperature` on a
reasoning-tier call (the API rejects it).

## 6. Response format strategy
- **Prose content** (teoria): plain markdown via `.call().content()`. No
  `response_format`/JSON — forcing prose into JSON wastes tokens and hurts readability.
- **Structured content** (ejercicios, evaluacion, diapositivas): OpenAI Structured Outputs
  via Spring AI's `.call().entity(new ParameterizedTypeReference<...>() {})` — never
  hand-roll JSON parsing (this is what `ai-integration.md` already mandates
  project-wide). Ejercicios moved from prose to `List<EjercicioDTO>` (titulo, tipo,
  dificultad, tiempoEstimado, enunciado, conceptosClave, pistas, solucion) so the
  frontend renders each exercise as its own interactive card instead of parsing markdown
  headings — this is the one exception to "prose for non-inherently-structured content":
  interactive material needs a field per UI element (badges for tipo/dificultad/tiempo,
  concept chips linking back to the theory, a progressive reveal-next-hint control over
  `pistas`) that markdown can't express cleanly. `tipo` has six values (conceptual,
  aplicado, analisis, practico, caso, reflexion); each tier's `estiloEjercicios` fixes
  its own hint count (Tutor 1, Maestro 2, Catedrático 2–3), time range, and preferred
  types, so the card texture differs visibly across tiers.

## 7. Theory grounding for ejercicios/evaluacion
Both pieces are generated **from the theory text**, never from the raw syllabus source
(`temario.descripcion`) — the exercises/exam must test what the student actually read.
`ContenidoTemarioService.generarMaterial`:
- Rejects the request with 400 if `EJERCICIOS`/`EVALUACION` is requested without
  `TEORIA` in the same request AND no theory already persisted for the topic
  ("Genera la teoría primero...").
- When `TEORIA` **is** requested alongside them, sequences it first via
  `CompletableFuture#thenCompose` and reuses its output as the grounding text — never
  calls `generarTeoria` twice.
- Otherwise reuses the persisted `ContenidoTemario.teoria`.

## 8. Roadmap
Four-phase rollout, each phase reusing this same method + rubric + tier table:
1. **Teoría** (done) — dynamic `buildTeoriaSystemPrompt(modelo, nivel)`.
2. **Ejercicios interactivos** (done) — `buildEjerciciosSystemPrompt(modelo, nivel)`,
   per-tier count via `estiloEjercicios` + `minEjercicios`/`maxEjercicios`, structured
   output (`List<EjercicioDTO>`), grounded in theory (§7).
3. **Exámenes** (done) — `buildEvaluacionSystemPrompt(modelo, nivel)`, per-tier item
   count via `minPreguntas`/`maxPreguntas`, cognitive-level distribution cycles across
   the larger tiers, Structured Outputs, grounded in theory (§7).
4. **Diapositivas** — extend `buildDiapositivasSystemPrompt` with per-tier
   narrative-arc depth, keep Structured Outputs. Still sourced from
   `temario.descripcion`, not theory-grounded (slides summarize the syllabus, not the
   generated theory).

When implementing a later phase, mirror `buildTeoriaSystemPrompt`'s pattern: a new
`.st` resource file plus a `build...SystemPrompt(ModeloIA modelo, NivelAcademico nivel, ...)`
method that renders it with the shared context + level rubric + tier-driven depth +
the piece's own spine/format rules — never inline the text into the `.java` file.
