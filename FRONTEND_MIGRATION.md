# Katedra backend contract changes — frontend migration required

The backend (`katedra-server`, branch `feature/generation`) changed two request/response
contracts. Nothing else changed. Base URL prefix for all endpoints below: `/api/v1`
(e.g. `POST /api/v1/temarios`). Auth is unchanged (JWT bearer token, same as before).

## 1. `gradoAcademico` is now a fixed enum, and it's required

**Before:** free text string, optional, e.g. `"Universidad"`, `"Primaria"`, anything.

**After:** must be exactly one of these 5 lowercase values, and it is now **required**:

```
"primaria" | "secundaria" | "bachillerato" | "universitario" | "posgrado"
```

Affected endpoints:
- `POST /api/v1/temarios` (create syllabus) — request body field `gradoAcademico`
- `GET /api/v1/temarios` / `GET /api/v1/temarios/{id}` — response field `gradoAcademico`
  now only ever contains one of the 5 values above

**UI change needed:** replace whatever free-text input or old grade selector was posting
`gradoAcademico` with a **required select/dropdown** limited to the 5 values above. Suggested
display labels (Spanish): Primaria, Secundaria, Bachillerato, Universitario, Posgrado.

**Error behavior (verified):** if `gradoAcademico` is missing, null, or not one of the 5
values, the create request fails with `HTTP 400 Bad Request` and **an empty response
body** (no JSON error object — there is no custom error envelope on this API). Treat any
non-2xx as failure by status code alone; do not try to parse an error message out of the
body for this endpoint.

### Example — create syllabus

Request:

```json
POST /api/v1/temarios
{
  "titulo": "Estructuras de Datos Lineales",
  "descripcion": "Pilas, colas y listas enlazadas",
  "gradoAcademico": "universitario",
  "asignatura": "Programación I"
}
```

Response (`201 Created`):

```json
{
  "id": "c9d021f1-37f2-4bc4-9d51-46bb907eb6e5",
  "titulo": "Estructuras de Datos Lineales",
  "descripcion": "Pilas, colas y listas enlazadas",
  "gradoAcademico": "universitario",
  "asignatura": "Programación I",
  "createdAt": "2026-07-08T22:00:00",
  "updatedAt": "2026-07-08T22:00:00"
}
```

## 2. `modelo` is now a branded tier key, not a raw OpenAI model id

**Before:** `modelo` was a raw model id: `"gpt-4o-mini"` (tier "Sencillo") or `"gpt-4o"`
(tier "Avanzado").

**After:** `modelo` must be exactly one of these 3 tier keys:

```
"flash" | "pro" | "max"
```

| Key | Display name (for UI) | What it means for the user |
|---|---|---|
| `flash` | **Tutor** | fastest, most economical, shorter theory (4–5 paragraphs) |
| `pro` | **Maestro** | balanced depth (6–8 paragraphs) |
| `max` | **Catedrático** | deepest, most rigorous, uses a reasoning model (8–10 paragraphs), costs the most |

Affected endpoint:
- `POST /api/v1/temarios/{id}/generar-material` — request body field `modelo`

`modelo` is **optional** in the request — if omitted, the backend defaults to `"flash"`.

**Response side:** `GET /api/v1/temarios/{id}/contenido` and the response of
`POST /api/v1/temarios/{id}/generar-material` both return a `modelo` field in
`ContenidoTemarioResponseDTO` — it now contains the tier key (`"flash"/"pro"/"max"`)
instead of a raw model id. If the UI displays "generated with gpt-4o-mini" anywhere, it
must be changed to look up the display name from the tier key.

**Error behavior (verified):** an invalid `modelo` value fails JSON deserialization and
returns `HTTP 400 Bad Request` with **an empty response body** (same as above — no
parseable error message, check the status code only).

**UI change needed:** replace any tier selector currently showing "Sencillo"/"Avanzado"
or raw model names with a 3-option selector showing **Tutor / Maestro / Catedrático**,
sending `flash` / `pro` / `max` respectively.

### Example — generate material

Request:

```json
POST /api/v1/temarios/{id}/generar-material
{
  "piezas": ["teoria", "evaluacion"],
  "modelo": "max",
  "regenerarPiezas": [],
  "numeroDiapositivas": null
}
```

Response (`200 OK`, async):

```json
{
  "id": "54ef9a12-1bfa-4c40-bbd4-cc9d5e1f0e21",
  "temarioId": "c9d021f1-37f2-4bc4-9d51-46bb907eb6e5",
  "teoria": "## ...markdown...",
  "ejercicios": null,
  "evaluacion": [
    { "pregunta": "...", "opciones": ["A", "B", "C", "D"], "opcionCorrectaIndex": 1, "explicacion": "..." }
  ],
  "diapositivas": null,
  "modelo": "max",
  "piezasOmitidas": []
}
```

`piezas` values are unchanged: `"teoria"`, `"ejercicios"`, `"evaluacion"`, `"diapositivas"`
(any subset, non-empty array required — empty/missing `piezas` still returns 400).

## 3. Slide-count valid ranges changed per tier

If the UI has a slide-count input/slider for `numeroDiapositivas` (only relevant when
`"diapositivas"` is included in `piezas`), the valid range now depends on the **new**
tier, and the ranges changed from the old 2-tier system:

| Tier | Min | Max | Default (if `numeroDiapositivas` omitted) |
|---|---|---|---|
| `flash` | 1 | 8 | 5 |
| `pro` | 6 | 12 | 9 |
| `max` | 9 | 15 | 12 |

Sending a value outside the selected tier's range → `HTTP 400 Bad Request`, empty body
(only when `"diapositivas"` is actually in the requested `piezas`).

**UI change needed:** if there's a min/max bound on the slide-count input, it must update
dynamically based on the selected tier (flash/pro/max), matching the table above.

## Nothing else changed

- `TemarioRequestDTO.titulo` — still required, unchanged.
- `TemarioRequestDTO.descripcion` / `asignatura` — still optional strings, unchanged.
- `EvaluacionPreguntaDTO`, `DiapositivaDTO` shapes — unchanged.
- All endpoint paths, HTTP methods, and auth — unchanged.
- `GET /api/v1/temarios/{id}/contenido` still returns `404` if content hasn't been
  generated yet — unchanged.

## Summary of concrete field/value changes to grep for in the frontend codebase

1. Any place sending `gradoAcademico` as free text → change to a fixed select with values
   `primaria`, `secundaria`, `bachillerato`, `universitario`, `posgrado`; make it required.
2. Any place sending `modelo: "gpt-4o-mini"` or `modelo: "gpt-4o"` → change to
   `modelo: "flash"` / `"pro"` / `"max"`.
3. Any place displaying `modelo` from a response → map `flash`→"Tutor", `pro`→"Maestro",
   `max`→"Catedrático" instead of showing the raw string.
4. Any place bounding `numeroDiapositivas` by tier → update to the new ranges above.
