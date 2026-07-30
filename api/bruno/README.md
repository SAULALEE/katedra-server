# Katedra API — Bruno collection

Open this folder (`katedra-server/api/bruno`) as a collection in
[Bruno](https://www.usebruno.com/) (free, offline, requests stored as plain text — nothing
to sign up for).

This collection is the *runnable* way to explore the API — request bodies, run order, and
the caveats each request has. For the *authoritative, always-current* contract, the running
server also generates its own OpenAPI spec straight from the controllers (springdoc), so it
can never drift the way a hand-written spec can: open `/api/v1/swagger-ui/index.html`, or
fetch the raw JSON from `/api/v1/v3/api-docs`. Both sources describe the same 37 endpoints;
use whichever fits the moment.

## Environments

Two environments ship with the collection, matching this repo's branch model:

- **Staging-Local** — `http://localhost:8080/api/v1`. Use this while iterating on code
  living on `staging` or a `feature/*` branch, against a server you're running yourself
  (`docker compose up -d`, see the root README).
- **Main-Production** — `https://katedra-server.onrender.com/api/v1`. Use this only to
  exercise what is actually deployed from `main`.

Pick one from Bruno's environment selector before running anything.

## Getting a token

Every folder except **Auth** and the public **Suscripciones** reads (`Get Public Plans`,
the webhook) needs a JWT. Run **Auth → Login** once — its post-response script writes the
token into the collection's `{{jwt}}` variable, and every other request already sends
`Authorization: Bearer {{jwt}}`. Re-run Login whenever the token expires (1 hour by
default).

To get an admin token, log in with an account whose email ends in `@katedra.com` — see
`Auth/Register.bru` for why that specific domain matters.

## Suggested run order for a cold environment

1. `Auth/Register` (any email) → `Auth/Login`
2. `Asignaturas/Create` → sets `{{asignaturaId}}`
3. `Temarios/Create` → sets `{{temarioId}}` (this call generates the outline for real, so
   it takes a few seconds and spends an OpenAI call)
4. `Temarios/Generate Material` with `"piezas": ["teoria"]`
5. `Exportaciones/Export` with `pieza=teoria&formato=pdf`

## What can't run from Bruno

- **Auth → Google/Microsoft Login** — these return a 302 into a real OAuth2 dance; useful
  only to confirm the redirect fires.
- **Suscripciones → 1. Start Checkout** gets you a Stripe `clientSecret`, but confirming a
  payment requires Stripe Elements tokenizing a card in a real browser — see that request's
  docs for the exact next step.
- **Webhooks → Stripe Webhook** cannot produce a valid signature outside Stripe itself. Use
  `stripe listen` / `stripe trigger` against a local server instead — see that request's
  docs.
