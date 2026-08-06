# Status and Roadmap

> English version. Versión en español: [3_STATUS_AND_ROADMAP.md](./3_STATUS_AND_ROADMAP.md).

Katedra is feature-complete for its first release. This document states plainly what works today
and what is deliberately left for later. It replaces the old `3_IMPLEMENTATION_PHASES.md`, which
had drifted badly out of date.

## Shipped

### Infrastructure
- Spring Boot 4.0.6 on Java 21, layered modular monolith.
- MySQL 8.0 with Flyway migrations. `ddl-auto=validate`, so the app will not start against a
  schema that disagrees with the entities.
- Doppler for development secrets; plain environment variables in production.
- Multi-stage Dockerfile running as a non-root user.
- Production profile with no fallback secrets, so a missing variable fails startup loudly.

### Authentication
- Stateless JWT with a custom `JwtAuthenticationFilter`.
- Register, login, logout.
- Google and Microsoft OAuth2 sign-in, both optional — the app starts cleanly when their
  credentials are absent.
- Role and plan modelled independently: an admin on the Free plan is a valid combination.

### Syllabus management
- Full CRUD for syllabi (`temarios`) and subjects (`asignaturas`).
- Three creation paths: manual entry, document upload (`pdf` / `doc` / `docx` / `md`), and URL
  import. The latter two are Pro-only.
- Favourites, per-user statistics, and an activity history.
- Ownership enforced in the service layer — users only ever reach their own data.

### AI generation
- Real OpenAI integration through Spring AI's `ChatClient`. No mock data anywhere.
- Four material pieces: outline, theory, exam, slides.
- Two branded tiers — Tutor (`gpt-4.1-mini`) and Catedrático (`o4-mini`, reasoning) — exposed to
  clients as tier keys, never as raw model ids.
- Five academic-level rubrics driving prompt register, from primary school to postgraduate.
- Fully async on a dedicated thread pool, with the MVC async timeout raised to 180s to cover the
  reasoning tier.
- Prompts stored as `.st` resources, never inlined in Java.
- Structured Outputs for exams and slides; markdown prose for theory.
- Exams grounded in the generated theory rather than the raw syllabus.

### Export
- Five exporters: PDF, DOCX, PPTX, Markdown, and a Google Apps Script that rebuilds an exam as a
  self-grading Google Form.
- Format/piece compatibility centralised in `FormatoExportacion.soporta(...)`.

### Billing
- Stripe Subscriptions, monthly and annual.
- Stripe is the source of truth. The webhook and the browser's explicit confirmation both call one
  idempotent sync, so the two can race safely and neither double-grants.
- Free and Pro tiers with capability locks and daily quotas.
- Three enforcement gates: capability (403), quota reservation (429), and quota refund on failed
  generation.
- Signature verification on the webhook against the raw request body.
- Self-service cancellation that keeps Pro active until the period ends.

### Quality
- 36 server-side test files: JUnit 5, Mockito, AssertJ, and `@WebMvcTest` slices covering
  services, controllers, exporters, security config, and the billing gates.

### Deployment
- Live backend on Render, behind a CORS allow-list.

## Next

Honest and deliberately short — these are wants, not commitments.

- **Stripe live mode.** The billing system is complete but runs in Stripe test mode. Going live is
  a configuration and account-verification task, not a code task.
- **Rate limiting on the public demo.** Every generation is a paid OpenAI call, so a public demo
  needs a per-account ceiling tighter than the Pro quota.
- **Integration tests against a real database.** The current suite mocks repositories. Testcontainers
  would cover the Flyway migrations and JPA mappings that unit tests cannot reach.
- **Observability.** Structured logging and metrics around generation latency, token spend, and
  failure rate per tier.
- **Slide generation depth.** Slides are the least developed piece; they summarise the syllabus
  rather than the generated theory, unlike exams.
- **Editing generated material.** Today material is regenerated rather than edited in place.

## Not planned

- Microservices. The modular monolith is a deliberate fit for a one-developer project; splitting it
  would add operational cost with no benefit at this scale.
- A second AI provider at runtime. Spring AI makes switching a config change, so carrying two
  providers simultaneously buys nothing.
