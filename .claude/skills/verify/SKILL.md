---
name: verify
description: Drive the real Katedra app (backend + frontend) end-to-end to verify a change actually works, not just that tests/build pass.
---

# Katedra E2E verify recipe

Two sibling repos: this one (`katedra-server`, Spring Boot API) and
`../katedra-client` (Vite/React SPA). Both must run together.

## Launch

- MySQL: `docker start katedra_db` if not already running (container may
  already be up — check `docker ps`).
- Backend: `doppler run -- ./mvnw -q spring-boot:run` from `katedra-server/`.
  Boots on `:8080`, context path `/api/v1`. Watch the log for
  `Started KatedraServerApplication` and the Flyway `Schema ... is up to
  date` / `Current version of schema` lines to confirm the DB migration
  state.
- Frontend: `npm run dev` (→ `doppler run -- vite`) from `katedra-client/`,
  serves `:5173`, proxies `/api/v1` to `:8080` (see `vite.config.js`). It may
  already be running from a previous session (long-lived dev server) — check
  `lsof -i :5173` before starting a second one.
- Real OpenAI calls: `OPENAI_API_KEY` comes through Doppler; generation
  (`POST /temarios/{id}/generar-material`) hits the live API, so driving it
  in a test costs real tokens. The `flash` (Tutor) tier is fastest/cheapest
  for smoke tests (~20s for teoria+evaluacion+diapositivas together).

## Browser driving (Playwright)

No project devDependency on Playwright — install it in a scratch dir:
`mkdir /tmp/.../pw && cd $_ && npm init -y && npm install playwright`. The
Chromium binary is already cached at `~/.cache/ms-playwright` so this is
fast and doesn't need `playwright install`.

**Known gotcha — do NOT `page.goto()` a protected route directly.**
`ProtectedRoute.jsx` reads `isAuthenticated` synchronously on first render,
before `useAuth`'s restore-session `useEffect` has run. On a hard
navigation/reload to any protected path (`/generador`, `/contenido/:id`,
`/dashboard`, ...) while logged in, it briefly renders
`<Navigate to="/login" replace>`, and `Login.jsx` then bounces you straight
to `/dashboard` (`useEffect(() => { if (isAuthenticated) navigate('/dashboard') })`
— hardcoded, doesn't preserve the originally-requested path). Net effect:
`page.goto(base + '/generador')` silently lands you on `/dashboard` instead.
**Always navigate via in-app clicks** (sidebar links, "Abrir" card buttons)
after the initial login/register, never via `page.goto` to a protected URL.
This is a real product bug (deep-links don't survive a refresh), not a test
artifact — worth fixing in `ProtectedRoute.jsx` (gate on `loading` before
deciding auth state) if it comes up again.

**Selector gotcha:** the piece-selection checkboxes ("Teoría Docente",
"Examen / Evaluación", "Diapositivas") in `Generator.jsx`'s config panel are
plain `<div onClick>`, while the content tabs below with the *same label
text* are real `<button>`. `getByText(...).first()` grabs the checkbox
(earlier in DOM order), not the tab. Use
`getByRole('button', { name: '...', exact: true })` to hit the tabs
specifically.

**Two independent slide renderers.** `Generator.jsx` has its own inline
diapositivas preview (around `activeTab === 'diapositivas'`), separate from
`ContentViewer.jsx`'s slide viewer (the `/contenido/:id` page). A redesign
applied to one does NOT show up in the other — check both if the task
concerns slide presentation.

## Golden path to drive real generation

1. `/register` → fill nombre/email/password/confirmPassword + check the
   terms checkbox → submit → lands on `/dashboard`.
2. Click "Nuevo Temario" → tab "Manual" (`[data-tab-opt="manual"]`) to reveal
   the description textarea → fill título/asignatura/grado(select) +
   descripción → click "Analizar Temario".
3. Click sidebar "Generador" link (NOT goto) → `<select>` the created
   temario by label (`"{titulo} · {asignatura}"`) → check the piece
   checkboxes → pick a model tier → click "Generar (N)".
4. Wait for `text=Creando contenido con IA` to detach (up to ~90s for
   reasoning tiers Maestro/Catedrático; ~20s for Tutor).
5. To see the polished slide viewer: sidebar "Mis Temarios" → "Abrir" on the
   course card → lands on `/contenido/:id` (client-side nav, safe).
