# Stripe Setup Checklist for Next Developer

**Rama:** `feature/pricing` (backend and frontend)

**Status:** Billing system **built**, but Stripe account **not yet configured**. This is the setup guide for the next dev.

---

## Prerequisites

- [ ] Stripe account (sign up at stripe.com, test mode enabled)
- [ ] Admin access to Katedra's Stripe dashboard
- [ ] Access to **Doppler** (where secrets live)
- [ ] Local backend running (`mvn spring-boot:run`)
- [ ] Local frontend running (`npm run dev`)
- [ ] `stripe-cli` installed (`brew install stripe` or `npm install -g @stripe/cli`)

---

## Step 1: Gather Stripe API Keys

### In Stripe Dashboard (test mode — check the toggle says "Test mode")
1. **Developers** → **API keys**
2. **Secret key**: `sk_test_...` → copy
3. **Publishable key**: `pk_test_...` → copy

No product or price needs to be created by hand; the script in Step 2 does it.

---

## Step 2: Run the Setup Script

`scripts/setup_stripe.py` creates the `Katedra Pro` product, both recurring prices
($19.00/month, $180.00/year, matching `utils/plan.js` on the client), and writes every
secret to Doppler. It is idempotent: an existing product, and any price with the same
amount and interval, are reused rather than duplicated.

```bash
cd katedra-server
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_PUBLISHABLE_KEY=pk_test_...
python3 scripts/setup_stripe.py
```

It refuses `sk_live_` keys on purpose: this project bills in test mode only.

Verify:
```bash
doppler secrets --only-names | grep STRIPE
```

---

## Step 3: Add the Webhook Secret

The checkout completes without this — the browser confirms explicitly via
`POST /suscripciones/{id}/confirmar`, and the webhook is the safety net for when that call
never lands. Add it once the endpoint exists:

### In Stripe Dashboard → Webhooks
1. **Webhooks** → **+ Add endpoint**
   - URL: `http://localhost:8080/api/v1/webhooks/stripe` (local testing)
     - For prod: your actual domain
   - Events to send:
     - `invoice.paid`
     - `invoice.payment_failed`
     - `customer.subscription.updated`
     - `customer.subscription.deleted`
   - Save → **Signing secret** (`whsec_...`): copy

```bash
doppler secrets set STRIPE_WEBHOOK_SECRET "whsec_..."
```

For local testing, `stripe listen` (Step 5) prints its own signing secret — use that one
instead while developing.

---

## Step 4: Start Backend with Doppler

```bash
cd katedra-server
doppler run -- ./mvnw -q spring-boot:run
```

Watch logs for:
```
... Successfully started Stripe configuration
... Server running on http://localhost:8080
```

If you see:
```
java.lang.IllegalArgumentException: stripe.secret-key property cannot be empty
```
→ Doppler secrets not loaded. Run `doppler login` and retry.

---

## Step 5: Test Webhook Locally (Optional but Recommended)

### Terminal 1: Backend (already running with Doppler)
```bash
cd katedra-server
doppler run -- ./mvnw -q spring-boot:run
```

### Terminal 2: Stripe CLI
```bash
stripe login
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
# Prints: listening for events...
# Prints: Ready! Your webhook signing secret is: whsec_test_...
```

**Compare the whsec_test_... with your Doppler `STRIPE_WEBHOOK_SECRET`.** They should match.

### Terminal 3: Trigger test event
```bash
stripe trigger invoice.paid
```

Back in Terminal 1, watch logs for:
```
[webhook] Received event: invoice.paid
[webhook] Syncing subscription from Stripe...
```

---

## Step 6: Test Frontend Checkout

### Prerequisites
- Backend running (Step 4)
- Frontend running: `npm run dev` (in katedra-client)
- Logged in as a test user (FREE plan)

### Flow
1. Open `http://localhost:5173/dashboard`
2. Click gear icon (bottom-left) → **Mejorar a Pro** → plan modal → **Mejorar a Pro**
3. The checkout page opens: cycle selector, order summary, renewal notice, payment method
4. Fill billing details (name, email and country are required) → **Continuar al pago**
5. The card block reveals in place below the details
6. Enter test card: `4242 4242 4242 4242` / `12/25` / `123`
7. Tick the recurring-charge consent → **Suscribirse**

**Expected outcomes:**

- [ ] Card accepted → modal shows "Success!"
- [ ] Sidebar badge changes to **Pro** (ámbar) **without page reload**
- [ ] Gear popover now shows "Gestionar mi plan" (not "Mejorar a Pro")
- [ ] Medidores jump to 0/100 and 0/100 (Pro limits)

### If it fails
1. Check browser console for JavaScript errors
2. Check backend logs for 400/429/503 errors
3. In Stripe Dashboard → Events, check if `invoice.paid` arrived
4. Run `doppler run -- ./mvnw test` to verify unit tests still pass

---

## Step 7: Test Edge Cases

### Declined card
Use `4000 0000 0000 0002`, expect modal to show error "Your card was declined."

### 3DS required (authenticate)
Use `4000 0025 0000 3155`, expect modal to show 3DS popup. Authenticate with any 6-digit code.

### Quota exhausted
1. As PRO user, generator page
2. Create 100 pieces in one day
3. 101st should return 429: "Has alcanzado tu límite de generaciones por hoy"

### Back-button safety
1. Open checkout, go to card step
2. Click "Volver"
3. Verify: no new row in Stripe dashboard's subscription list
4. Re-enter card, pay again → same subscription in Stripe

---

## Step 8: Deploy Configuration

### Prod environment (when ready)

1. **Create new Stripe webhook endpoint** (prod mode, not test)
   - URL: `https://katedra.example.com/api/v1/webhooks/stripe`
   - Signing secret: `whsec_live_...`

2. **Update Doppler** (prod env):
   ```bash
   doppler secrets set STRIPE_SECRET_KEY "sk_live_..." --config prod
   doppler secrets set STRIPE_PUBLISHABLE_KEY "pk_live_..." --config prod
   doppler secrets set STRIPE_WEBHOOK_SECRET "whsec_live_..." --config prod
   # (price IDs remain the same, just switch to live prices)
   ```

3. **Update price IDs in prod** if you create new live prices:
   ```bash
   doppler secrets set STRIPE_PRICE_PRO_MENSUAL "price_live_..." --config prod
   doppler secrets set STRIPE_PRICE_PRO_ANUAL "price_live_..." --config prod
   ```

4. **Redeploy backend** with prod Doppler config

5. **Test on prod** with real card (small amount, e.g., $1)

---

## Troubleshooting

### "Stripe API key is not set"
→ Doppler secrets not loaded. Run `doppler login` and verify `doppler secrets list | grep STRIPE`.

### "stripe_subscription_id is not unique"
→ DB constraint violation. Check migrations V24 ran successfully:
```sql
SELECT * FROM suscripcion WHERE stripe_subscription_id IS NOT NULL GROUP BY stripe_subscription_id HAVING COUNT(*) > 1;
```
If duplicates exist, it's a data issue (shouldn't happen on fresh DB).

### Webhook not triggering in Stripe CLI
→ Ensure backend is running on `localhost:8080`.
→ Ensure `stripe listen` shows "Ready!" (not just "listening for...").
→ Check Stripe Dashboard → Webhooks → Recent attempts (red X = failure; click to see details).

### Frontend shows "pk_test_..." instead of publishable key loading
→ Check `VITE_STRIPE_PUBLISHABLE_KEY` in Doppler. Frontend must run `doppler run -- npm run dev`.

### Payment succeeds in Stripe but plan doesn't change on frontend
→ Webhook might have arrived late. Refresh page.
→ Or click "Confirmar" button again (idempotent).
→ Check backend logs for sync errors.

---

## After Setup: Next Phases

Once Stripe is live and tested:

1. **Feature locks** — Add UI elements to block Pro-only features for FREE users:
   - Generator: disable "Catedrático" model
   - Generator: disable "Diapositivas" piece
   - Dashboard: disable PDF/URL upload tabs
   - Users list: replace fake "Premium" badge with real plan

2. **Notifications** — Integrate quota-exhausted (429) and permission-denied (403) errors with user-friendly upgrade CTAs

3. **Analytics** — Track conversion funnel:
   - FREE users clicking "Mejorar a Pro"
   - Checkout starts
   - Checkout completions
   - Churn (cancellations)

4. **Billing portal** — Add link in sidebar to Stripe's hosted customer portal (optional but recommended for self-service refunds/invoices)

---

## References

- [Stripe Billing Architecture](./stripe-billing.md) — How the system works
- [Plan Limits](../../../src/main/java/Katedra/Server/model/PlanUsuario.java) — FREE vs PRO terms
- [Test Cards](https://stripe.com/docs/testing#cards) — Stripe's official test card list
- [Stripe API Docs](https://stripe.com/docs/api) — Official reference
