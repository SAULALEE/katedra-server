# Stripe Billing System Architecture

## Overview

Katedra's monetization uses **Stripe Subscriptions** in test mode (cost $0, test card `4242 4242 4242 4242`). Two plans: **FREE** (10 generations/day, 5 exports/day, tutor-only model) and **PRO** (100 generations/day, 100 exports/day, all AI models and upload sources).

The system enforces plan limits at three choke points (generation, upload, export) and serves the current plan to the frontend via a read-only endpoint. **The server never trusts the client.** Both webhook and browser-side confirmation call the same idempotent sync to Stripe — whichever fires first wins, second is a no-op.

---

## Data Model

### `Usuario.plan`
String enum: `FREE` (default) | `PRO`. Read from the database on every plan check. The JWT claim is display-only (1h TTL); the actual gate always queries the DB.

### `Suscripcion` table
Subscription lifecycle history. One row per subscription attempt. **Not soft-deleted.**

| field | type | notes |
|---|---|---|
| `id` | UUID | Primary key |
| `usuario_id` | CHAR(36) | Foreign key, plain column (not @ManyToOne) to survive soft-deleted users |
| `plan` | VARCHAR(50) enum | `PRO` only; FREE users have no row |
| `ciclo` | VARCHAR(50) enum | `MENSUAL` \| `ANUAL` |
| `estado` | VARCHAR(50) enum | `INCOMPLETA`, `ACTIVA`, `IMPAGA`, `CANCELADA` |
| `stripe_customer_id` | VARCHAR | Reused across subscriptions to consolidate payment history |
| `stripe_subscription_id` | VARCHAR | UNIQUE; Stripe's source of truth |
| `stripe_price_id` | VARCHAR | Which price this subscription is for |
| `periodo_inicio` | TIMESTAMP | Billing period start (from Stripe) |
| `periodo_fin` | TIMESTAMP | Billing period end (from Stripe) |
| `cancela_al_final` | BOOLEAN | If true, PRO expires at `periodo_fin`; if false, auto-renews |
| `cancelada_en` | TIMESTAMP | When the user requested cancellation |
| `created_at`, `updated_at` | TIMESTAMP | Audit |

### `UsoDiario` table
Daily quota tracking. **One row per (usuario_id, fecha) pair.** Implicitly rolls over at midnight (new date = new row).

| field | type | notes |
|---|---|---|
| `id` | UUID | Primary key |
| `usuario_id` | CHAR(36) | Foreign key |
| `fecha` | DATE | Which day (local time) |
| `generaciones` | INT | Counter: 0–10 (FREE) or 0–100 (PRO) |
| `exportaciones` | INT | Counter: 0–5 (FREE) or 0–100 (PRO) |
| `created_at`, `updated_at` | TIMESTAMP | Audit |
| UNIQUE(usuario_id, fecha) | — | Prevents duplicate rows for the same user+date |

---

## Request Flow

### 1. Frontend opens checkout modal
**Browser → GET `/suscripciones/me/uso`**
- Returns current plan, today's usage, capability flags (can use Pro model? can upload files?)
- Authority on the plan; localStorage copy is display-only
- Used by: sidebar popover, plan comparison modal

### 2. User enters billing details and clicks "Pay"
**Browser → POST `/suscripciones` (step 1: capture billing)**

```json
{
  "ciclo": "mensual",
  "facturacion": {
    "nombreCompleto": "María García",
    "email": "maria@example.com",
    "pais": "México",
    "ciudad": "CDMX",
    "direccion": "Calle 1 #123",
    "codigoPostal": "06600"
  }
}
```

Response:
```json
{
  "suscripcionId": "550e8400-...",
  "clientSecret": "seti_1234_secret_567...",
  "publishableKey": "pk_test_...",
  "monto": 1900,
  "moneda": "usd",
  "ciclo": "mensual"
}
```

**Server-side logic:**
1. Lookup or create Stripe customer (reuse existing to preserve payment history)
2. Check for existing `INCOMPLETA` subscription in the same billing cycle
   - If found: update customer's billing details, re-fetch subscription, return its `clientSecret` (safe back-button)
   - If not found: create new subscription via Stripe
3. Store subscription locally with status `INCOMPLETA`
4. Extract `clientSecret` from latest invoice

**Key invariant:** No new subscription in Stripe on back-button; `clientSecret` is reused.

### 3. User enters card in modal, Stripe confirms payment
**Browser → `stripe.confirmPayment()` with `clientSecret`**

Stripe either:
- Accepts the card → fires `invoice.paid` webhook
- Declines or requires 3DS → returns error to the form

### 4a. Webhook arrives at `/webhooks/stripe`
**POST /webhooks/stripe (signature verified)**

```
Stripe → (Signature: t=..., v1=...) → Server
```

1. Verify signature (raw body + header, not deserialized)
2. Extract subscription ID from event payload (varies by event type)
3. Call `syncSubscriptionFromStripe(stripeSubscriptionId)`
4. Return 200 (even on error, to prevent Stripe retry storms)

### 4b. Browser confirms payment (explicit, not webhook-dependent)
**Browser → POST `/suscripciones/{id}/confirmar`**

```json
{ "suscripcionId": "550e8400-..." }
```

Server calls `syncSubscriptionFromStripe()` (same idempotent function as webhook).

**Why both?** Webhook might arrive late or not at all (network issues). Client confirmation is the primary flow; webhook is the safety net. If both fire, the sync is idempotent so no double-grant.

### 5. Sync subscription from Stripe
`syncSubscriptionFromStripe(stripeSubscriptionId)` is the **single source of truth sync:**

1. Call `stripe.subscriptions().retrieve(id)` — live state
2. Map Stripe status → internal `EstadoSuscripcion`
   - `incomplete` → `INCOMPLETA`
   - `active` / `trialing` → `ACTIVA`
   - `past_due` / `unpaid` → `IMPAGA`
   - `canceled` → `CANCELADA`
3. Upsert `Suscripcion` row by UNIQUE(`stripe_subscription_id`)
4. Set `Usuario.plan = (estado.esVigente() ? PRO : FREE)` (i.e., ACTIVA → PRO, all else → FREE)

**Idempotent:** Call 1 time or 100 times, same result.
**Order-independent:** Webhook and client confirmation can race; either wins.

### 6. Frontend updates badge (no page reload needed)
**Browser calls `authStore.actualizarPlan(plan)`**
- Rewrites `localStorage.katedra_user`
- Triggers sidebar re-render: badge changes from "Gratis" to ámbar Pro
- No token refresh needed (JWT claim is display-only)

---

## Plan Limit Enforcement

### Three gates
All live in `PlanLimitService`. **Read plan from DB, never from JWT.**

#### Gate 1: Capability check (→ 403)
```
validarGeneracion(usuario, modeloIA, piezas)
  if user is FREE and modeloIA is PRO → reject
  if piezas contains DIAPOSITIVAS and user is FREE → reject

validarOrigenTemario(usuario, origen)
  if user is FREE and origen is ARCHIVO or URL → reject

validarExportacion(usuario, pieza, formato)
  if user is FREE and format requires PRO → reject
```

#### Gate 2: Quota check (→ 429)
```
reservarGeneraciones(usuario, cantidad)
  @Transactional(REQUIRES_NEW)
  INSERT/UPDATE uso_diario for today
  if affected rows == 0 → throw 429 (quota exhausted)
  
liberarGeneraciones(usuario, cantidad)
  UPDATE uso_diario, subtract cantidad, clamp at 0
  (Called on failed piece generation to refund quota)
```

### Order in request processing
1. **Capability check first** (before any IO, before AI call)
2. **Quota reservation second** (last validation before commit to async AI pipeline)
3. **Quota refund in result handler** (if N pieces fail, release N from today's counter)

---

## Security Properties

### Trust model
- **Backend is the authority.** Never trust JWT claims, webhook payload, or client state.
- Sync always reads live from Stripe via `subscriptions().retrieve()`.
- Both webhook and client confirmation call the same sync (idempotent).

### What happens if...

**Client lies about payment?** → Server ignores the lie, syncs from Stripe. No grant.

**Webhook arrives after client confirms?** → Sync is idempotent. Second call re-reads same Stripe state, no double-grant.

**Webhook arrives, then client confirms?** → Same. Idempotent sync.

**Token expires mid-session, plan changes in DB?** → Next request reads new plan from DB (backend gate), not JWT (display-only).

**User cancels mid-month?** → `cancelaAlFinal=true`, plan stays PRO until `periodoFin`. Thereafter → FREE (on next request that reads the DB).

---

## Stripe Configuration (Test Mode)

Create one Product: **Katedra Pro**

Two Prices:
- `price_1234_monthly`: 19 USD/month, recurring monthly
- `price_1234_annual`: 180 USD/year, recurring annually

Environment variables (Doppler):
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_PRO_MENSUAL=price_1234_monthly
STRIPE_PRICE_PRO_ANUAL=price_1234_annual
```

Local webhook testing (free, no tunnel):
```bash
stripe login
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
stripe trigger invoice.paid
```

---

## Error Codes

| code | meaning | recovery |
|---|---|---|
| 400 | Webhook signature invalid or JSON malformed | Stripe retries; no action |
| 403 | Insufficient plan for this feature | Show upgrade prompt |
| 429 | Daily quota exhausted | Show upgrade prompt |
| 500 | Server error during payment | User retries; server re-syncs from Stripe |

Webhook always returns 200 (even on processing error) to prevent retry storms.

---

## Testing

### Backend unit tests
- `PlanLimitServiceTest` — capability gates (403), quota gates (429), refund logic
- `SuscripcionServiceTest` — idempotence, Stripe as source of truth, both webhook and client paths
- `StripeWebhookControllerTest` — signature verification, error handling

### Browser checklist
1. FREE user → gear → popover shows plan + usage meters (0/10 gen, 0/5 export)
2. Attempt Pro-only feature → 403 with upgrade CTA
3. Generate 10 pieces (hit quota) → 11th returns 429 with upgrade CTA
4. Checkout flow: enter details → enter card (`4242 4242 4242 4242`) → success → badge becomes Pro (no reload)
5. Back-button on card step → returns to details form, no new subscription in Stripe
6. Cancel subscription → plan stays Pro until period end

---

## References

- [PlanUsuario.java](../../../src/main/java/Katedra/Server/model/PlanUsuario.java) — enum with limits
- [SuscripcionService.java](../../../src/main/java/Katedra/Server/service/SuscripcionService.java) — subscription lifecycle
- [PlanLimitService.java](../../../src/main/java/Katedra/Server/service/PlanLimitService.java) — gates and quota
- [StripeService.java](../../../src/main/java/Katedra/Server/service/StripeService.java) — Stripe wrapper
- [Migrations V23–V25](../../../src/main/resources/db/migration/) — schema
