# Fases de Implementación y Roadmap

El desarrollo de Katedra se ha estructurado en fases iterativas, asegurando una base sólida antes de avanzar hacia integraciones complejas de IA.

## Fase 1: Infraestructura y Configuración (✅ Completado)
- Inicialización del proyecto Spring Boot 3.x con Java 21.
- Configuración de la conectividad a la base de datos MySQL utilizando Doppler para la inyección segura de variables de entorno.
- Integración de Flyway para las migraciones de base de datos.
- Establecimiento de Registros de Decisiones Arquitectónicas (ADRs) y flujos de trabajo de Git.

## Fase 2: Gestión de Identidad y Accesos (✅ Completado)
- Diseño de la entidad `Usuario` con UUIDs y borrados lógicos (Migración V1).
- Implementación de la configuración de Spring Security.
- Desarrollo de generación, validación y filtrado de JWT sin estado.
- Creación de `AuthController` para los endpoints de registro e inicio de sesión de usuarios.

## Fase 3: Dominio Central - Gestión de Temarios (✅ Completado)
- Diseño de la entidad `Temario` para representar los temarios educativos (Migración V2).
- Implementación de operaciones CRUD (Crear, Leer, Eliminar) dentro de `TemarioService`.
- Exposición de endpoints REST protegidos en `TemarioController`, asegurando que los usuarios solo puedan acceder a sus propios temarios.

## Fase 4: Andamiaje para Generación con IA (🔄 En Progreso)
- Creación de la entidad `ContenidoTemario` y la migración de base de datos V3 para almacenar materiales educativos estructurados en JSON (teoría, ejercicios, evaluaciones, diapositivas).
- Implementación de la estructura de endpoints para la generación de material.
- **Estado Actual:** Los endpoints de generación están conectados correctamente, pero actualmente llenan la base de datos con datos simulados (mock data).

## Fase 5: Integración Real con IA (⏳ Pendiente)
- Integrar clientes API de Anthropic u OpenAI.
- Implementar ingeniería de prompts estructurada para generar respuestas JSON confiables.
- Reemplazar la lógica de datos simulados en `TemarioService` con llamadas reales a LLMs.

## Fase 6: Monetización y Suscripciones (⏳ Pendiente)
- Diseñar e implementar planes de precios y niveles de suscripción.
- Integrar una pasarela de pagos (ej., Stripe) para la facturación recurrente.

## Fase 7: Aseguramiento de Calidad Automatizado (⏳ Pendiente)
- Implementar pruebas unitarias exhaustivas utilizando JUnit 5 y Mockito para todas las capas de Servicio.
- Implementar pruebas de integración para los Controladores REST.
