# Estado y Roadmap

> Versión en español. English version: [3_STATUS_AND_ROADMAP.en.md](./3_STATUS_AND_ROADMAP.en.md).

Katedra está funcionalmente completo para su primera versión. Este documento describe con
claridad qué funciona hoy y qué se ha dejado deliberadamente para después. Reemplaza al antiguo
`3_IMPLEMENTATION_PHASES.md`, que se había quedado muy desactualizado.

## Publicado

### Infraestructura
- Spring Boot 4.0.6 sobre Java 21, monolito modular por capas.
- MySQL 8.0 con migraciones Flyway. `ddl-auto=validate`, así que la app no arranca contra un
  esquema que no coincide con las entidades.
- Doppler para secretos de desarrollo; variables de entorno planas en producción.
- Dockerfile multi-etapa corriendo como usuario no-root.
- Perfil de producción sin secretos de respaldo, así que una variable faltante hace fallar el
  arranque de forma ruidosa.

### Autenticación
- JWT sin estado con un `JwtAuthenticationFilter` personalizado.
- Registro, login, logout.
- Inicio de sesión con Google y Microsoft OAuth2, ambos opcionales — la app arranca sin problemas
  cuando faltan sus credenciales.
- Rol y plan modelados de forma independiente: un admin en el plan Free es una combinación
  válida.

### Gestión de temarios
- CRUD completo para temarios (`temarios`) y asignaturas (`asignaturas`).
- Tres vías de creación: entrada manual, subida de documento (`pdf` / `doc` / `docx` / `md`), e
  importación por URL. Las dos últimas son solo Pro.
- Favoritos, estadísticas por usuario, e historial de actividad.
- Propiedad aplicada en la capa de servicio — los usuarios solo acceden a sus propios datos.

### Generación con IA
- Integración real con OpenAI a través del `ChatClient` de Spring AI. Sin datos simulados en
  ningún lado.
- Cuatro piezas de material: estructura, teoría, examen, diapositivas.
- Dos niveles con nombre de marca — Tutor (`gpt-4.1-mini`) y Catedrático (`o4-mini`,
  razonamiento) — expuestos a los clientes como claves de nivel, nunca como ids de modelo en
  bruto.
- Cinco rúbricas de nivel académico que ajustan el registro del prompt, desde primaria hasta
  posgrado.
- Totalmente asíncrono en un pool de hilos dedicado, con el timeout asíncrono de MVC subido a
  180s para cubrir el nivel de razonamiento.
- Prompts almacenados como recursos `.st`, nunca en línea en Java.
- Structured Outputs para exámenes y diapositivas; prosa markdown para la teoría.
- Exámenes fundamentados en la teoría generada en lugar del temario en bruto.

### Exportación
- Cinco exportadores: PDF, DOCX, PPTX, Markdown, y un Google Apps Script que reconstruye un
  examen como un Google Form autocalificable.
- Compatibilidad formato/pieza centralizada en `FormatoExportacion.soporta(...)`.

### Facturación
- Stripe Subscriptions, mensual y anual.
- Stripe es la fuente de verdad. El webhook y la confirmación explícita del navegador llaman a
  una misma sincronización idempotente, así que ambos pueden competir de forma segura y ninguno
  otorga acceso dos veces.
- Niveles Free y Pro con bloqueos de capacidad y cuotas diarias.
- Tres puertas de control: capacidad (403), reserva de cuota (429), y reembolso de cuota si la
  generación falla.
- Verificación de firma en el webhook contra el cuerpo crudo de la petición.
- Cancelación autoservicio que mantiene Pro activo hasta que termina el período.

### Calidad
- 36 archivos de test en el servidor: JUnit 5, Mockito, AssertJ, y slices `@WebMvcTest` que
  cubren services, controllers, exportadores, configuración de seguridad, y las puertas de
  facturación.

### Despliegue
- Backend en vivo en Render, detrás de una lista blanca de CORS.

## Siguiente

Honesto y deliberadamente corto — esto son deseos, no compromisos.

- **Modo live de Stripe.** El sistema de facturación está completo pero corre en modo de prueba
  de Stripe. Pasar a modo live es una tarea de configuración y verificación de cuenta, no de
  código.
- **Límite de uso en la demo pública.** Cada generación es una llamada pagada a OpenAI, así que
  una demo pública necesita un tope por cuenta más estricto que la cuota Pro.
- **Tests de integración contra una base de datos real.** La suite actual simula los
  repositorios. Testcontainers cubriría las migraciones de Flyway y los mapeos JPA a los que los
  tests unitarios no llegan.
- **Observabilidad.** Logging estructurado y métricas sobre la latencia de generación, el gasto
  en tokens, y la tasa de fallos por nivel.
- **Profundidad en la generación de diapositivas.** Las diapositivas son la pieza menos
  desarrollada; resumen el temario en lugar de la teoría generada, a diferencia de los exámenes.
- **Edición del material generado.** Hoy el material se regenera en lugar de editarse en sitio.

## No planeado

- Microservicios. El monolito modular es una elección deliberada para un proyecto de un solo
  desarrollador; dividirlo añadiría costo operativo sin beneficio a esta escala.
- Un segundo proveedor de IA en tiempo de ejecución. Spring AI hace que cambiar sea un cambio de
  configuración, así que mantener dos proveedores simultáneamente no aporta nada.
