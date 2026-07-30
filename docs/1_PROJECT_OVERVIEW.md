# Katedra — Project Overview

> Technical docs are written in English. The public-facing READMEs are available in both
> English and Spanish.

## 1. What it is

Katedra is an AI-driven academic content generator built for teachers. A teacher describes a
syllabus once — by hand, by uploading a document, or from a URL — and Katedra generates the
teaching material for it: the syllabus outline, the theory, a multiple-choice exam, and
presentation slides. Everything generated stays organised under the syllabus it came from, and
can be exported to the formats teachers actually hand in.

The target user is a working teacher, and the goal is to remove the bulk of manual preparation
time rather than to replace the teacher's judgement.

## 2. What it generates

Four pieces of material, defined by `PiezaMaterial`:

| Piece | Value | Output |
|---|---|---|
| Outline | `estructura` | Units / topics / subtopics as markdown. Generated automatically when a syllabus is created. |
| Theory | `teoria` | Full lesson prose in markdown, on demand. |
| Exam | `evaluacion` | Structured multiple-choice questions, each with the correct option and an explanation. |
| Slides | `diapositivas` | Structured slide deck. Pro plan only. |

Exams are generated **from the theory text**, not from the raw syllabus, so the exam tests what
the student actually read. Requesting an exam with no theory available is rejected with a 400.

## 3. Two generation tiers

The API exposes a branded tier key, never a raw model id, so the AI provider can change by
configuration without breaking clients. Defined in `ModeloIA`:

| Tier | Key | Display | Model | Behaviour |
|---|---|---|---|---|
| Fast | `flash` | Tutor | `gpt-4.1-mini` | 5–15 theory paragraphs, 1–10 exam questions, 5–10 slides. `temperature 0.5`, 2500 max tokens. |
| Deep | `pro` | Catedrático | `o4-mini` (reasoning) | 20–40 theory paragraphs capped at 5000 words, 15–30 exam questions, 10–20 slides. `reasoningEffort=medium`, 12000 max completion tokens, and **no** temperature — reasoning models reject it. |

Within a tier the exact paragraph / question / slide count is user-selectable per request and
validated against the tier's range; out-of-range values are rejected with a 400.

## 4. Academic level

Every syllabus targets one of five bands (`NivelAcademico`): `primaria`, `secundaria`,
`bachillerato`, `universitario`, `posgrado`. Each band carries a rubric injected into every
generation prompt, so the same topic reads differently for a 10-year-old than for a postgraduate.
The level is never silently defaulted, and when a syllabus lists several levels the most advanced
one wins — material should never undershoot its hardest audience.

## 5. Export

Export runs through `FormatoExportacion`. Not every format applies to every piece;
`FormatoExportacion.soporta(...)` is the single source of truth:

| Format | Extension | Applies to | Plan |
|---|---|---|---|
| PDF | `.pdf` | theory, exam, slides | Free |
| Word | `.docx` | theory, exam | Free |
| PowerPoint | `.pptx` | slides | Pro (slides are Pro) |
| Markdown | `.md` | theory, exam | Pro |
| Google Forms script | `.gs` | exam | Pro |

The Google Forms export emits an Apps Script that recreates the exam as a self-grading Google
Form.

## 6. Plans

Two billing tiers (`PlanUsuario`), enforced server-side against the database — never against the
JWT claim, which is display-only:

| | Free | Pro |
|---|---|---|
| Generations / day | 10 | 100 |
| Exports / day | 5 | 100 |
| Catedrático tier | — | yes |
| Slides | — | yes |
| File upload | — | yes |
| URL import | — | yes |
| Markdown + Google Forms export | — | yes |

Caps are per-day rather than lifetime on purpose: a permanently useful free tier drives return
visits, while a lifetime cap causes churn the moment it is hit. Upgrade pressure is meant to come
from the capability locks, not from starving the quota. The Pro figure of 100/day is an abuse
ceiling, not a paywall — every generation is a real OpenAI call.

Billing runs on Stripe Subscriptions (monthly and annual). The server treats Stripe as the source
of truth: the webhook and the browser's explicit confirmation both call the same idempotent sync,
so whichever arrives first wins and the second is a no-op.

## 7. Documentation map

- [2_ARCHITECTURE_AND_TECH_STACK.md](./2_ARCHITECTURE_AND_TECH_STACK.md) — layers, data flow, AI
  integration, security.
- [3_STATUS_AND_ROADMAP.md](./3_STATUS_AND_ROADMAP.md) — what ships today, what is next.
- REST contract: generated live from the controllers via springdoc — run the app and open
  `/api/v1/swagger-ui/index.html`, or fetch the raw spec from `/api/v1/v3/api-docs`.
- [`api/bruno/`](../api/bruno/) — a runnable Bruno collection covering every endpoint.
- Frontend repo: [katedra-client](https://github.com/SAULALEE/katedra-client).
