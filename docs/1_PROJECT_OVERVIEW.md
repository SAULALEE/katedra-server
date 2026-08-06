# Katedra — Descripción General del Proyecto

> Versión en español. English version: [1_PROJECT_OVERVIEW.en.md](./1_PROJECT_OVERVIEW.en.md).

## 1. Qué es

Katedra es un generador de contenido académico impulsado por IA, construido para profesores. Un
profesor describe un temario una sola vez —a mano, subiendo un documento, o desde una URL— y
Katedra genera el material didáctico correspondiente: la estructura del temario, la teoría, un
examen de opción múltiple y diapositivas de presentación. Todo lo generado queda organizado bajo
el temario del que proviene, y puede exportarse a los formatos que los profesores realmente
entregan.

El usuario objetivo es un profesor en activo, y el objetivo es eliminar la mayor parte del tiempo
de preparación manual, no reemplazar el criterio del profesor.

## 2. Qué genera

Cuatro piezas de material, definidas por `PiezaMaterial`:

| Pieza | Valor | Salida |
|---|---|---|
| Estructura | `estructura` | Unidades / temas / subtemas en markdown. Se genera automáticamente al crear un temario. |
| Teoría | `teoria` | Prosa completa de la clase en markdown, a demanda. |
| Examen | `evaluacion` | Preguntas estructuradas de opción múltiple, cada una con la opción correcta y una explicación. |
| Diapositivas | `diapositivas` | Presentación estructurada. Solo plan Pro. |

Los exámenes se generan **a partir del texto de la teoría**, no del temario en bruto, para que el
examen evalúe lo que el estudiante realmente leyó. Pedir un examen sin teoría disponible se
rechaza con un 400.

## 3. Dos niveles de generación

La API expone una clave de nivel de marca, nunca un id de modelo en bruto, para que el proveedor
de IA pueda cambiarse por configuración sin romper a los clientes. Definido en `ModeloIA`:

| Nivel | Clave | Nombre visible | Modelo | Comportamiento |
|---|---|---|---|---|
| Rápido | `flash` | Tutor | `gpt-4.1-mini` | 5–15 párrafos de teoría, 1–10 preguntas de examen, 5–10 diapositivas. `temperature 0.5`, 2500 tokens máximos. |
| Profundo | `pro` | Catedrático | `o4-mini` (razonamiento) | 20–40 párrafos de teoría con tope de 5000 palabras, 15–30 preguntas de examen, 10–20 diapositivas. `reasoningEffort=medium`, 12000 tokens máximos de completado, y **sin** temperature — los modelos de razonamiento la rechazan. |

Dentro de un nivel, la cantidad exacta de párrafos / preguntas / diapositivas es seleccionable por
el usuario en cada petición y se valida contra el rango del nivel; los valores fuera de rango se
rechazan con un 400.

## 4. Nivel académico

Cada temario apunta a una de cinco bandas (`NivelAcademico`): `primaria`, `secundaria`,
`bachillerato`, `universitario`, `posgrado`. Cada banda lleva una rúbrica que se inyecta en cada
prompt de generación, así el mismo tema se lee distinto para un niño de 10 años que para un
estudiante de posgrado. El nivel nunca toma un valor por defecto en silencio, y cuando un temario
lista varios niveles, gana el más avanzado — el material nunca debe quedarse corto frente a su
audiencia más exigente.

## 5. Exportación

La exportación pasa por `FormatoExportacion`. No todos los formatos aplican a todas las piezas;
`FormatoExportacion.soporta(...)` es la única fuente de verdad:

| Formato | Extensión | Aplica a | Plan |
|---|---|---|---|
| PDF | `.pdf` | teoría, examen, diapositivas | Free |
| Word | `.docx` | teoría, examen | Free |
| PowerPoint | `.pptx` | diapositivas | Pro (las diapositivas son Pro) |
| Markdown | `.md` | teoría, examen | Pro |
| Script de Google Forms | `.gs` | examen | Pro |

La exportación a Google Forms genera un Apps Script que recrea el examen como un Google Form
autocalificable.

## 6. Planes

Dos niveles de facturación (`PlanUsuario`), aplicados en el servidor contra la base de datos —
nunca contra el claim del JWT, que es solo informativo:

| | Free | Pro |
|---|---|---|
| Generaciones / día | 10 | 100 |
| Exportaciones / día | 5 | 100 |
| Nivel Catedrático | — | sí |
| Diapositivas | — | sí |
| Subida de archivo | — | sí |
| Importación por URL | — | sí |
| Exportación Markdown + Google Forms | — | sí |

Los límites son por día y no de por vida a propósito: un plan gratuito permanentemente útil
genera visitas de retorno, mientras que un límite de por vida provoca abandono en cuanto se
alcanza. La presión para actualizar debe venir de las funciones bloqueadas, no de asfixiar la
cuota. La cifra de 100/día en Pro es un techo contra abuso, no un muro de pago — cada generación
es una llamada real a OpenAI.

La facturación corre sobre Stripe Subscriptions (mensual y anual). El servidor trata a Stripe
como la fuente de verdad: tanto el webhook como la confirmación explícita del navegador llaman a
la misma sincronización idempotente, así que quien llegue primero gana y el segundo no hace nada.

## 7. Mapa de documentación

- [2_ARCHITECTURE_AND_TECH_STACK.md](./2_ARCHITECTURE_AND_TECH_STACK.md) — capas, flujo de datos,
  integración de IA, seguridad.
- [3_STATUS_AND_ROADMAP.md](./3_STATUS_AND_ROADMAP.md) — qué está publicado hoy, qué sigue.
- Contrato REST: generado en vivo desde los controladores vía springdoc — arranca la app y abre
  `/api/v1/swagger-ui/index.html`, o descarga el spec crudo desde `/api/v1/v3/api-docs`.
- [`api/bruno/`](../api/bruno/) — una colección de Bruno ejecutable que cubre cada endpoint.
- Repositorio del frontend: [katedra-client](https://github.com/SAULALEE/katedra-client).
