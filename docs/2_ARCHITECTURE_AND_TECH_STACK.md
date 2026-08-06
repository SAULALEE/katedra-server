# Arquitectura y Stack Tecnológico

> Versión en español. English version: [2_ARCHITECTURE_AND_TECH_STACK.en.md](./2_ARCHITECTURE_AND_TECH_STACK.en.md).

## 1. Stack

| Aspecto | Elección |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Integración de IA | Spring AI (`ChatClient`) sobre OpenAI |
| Base de datos | MySQL 8.0 |
| Migraciones | Flyway |
| Pagos | Stripe Subscriptions (SDK de Java) |
| Secretos | Doppler en desarrollo; variables de entorno planas en producción |
| Auth | Spring Security, JWT sin estado, OAuth2 opcional (Google / Microsoft) |
| Tests | JUnit 5, Mockito, AssertJ, `@WebMvcTest` |
| Contenedor | Dockerfile multi-etapa sobre Eclipse Temurin 21, corre como usuario no-root |

## 2. Arquitectura

Katedra es un **monolito modular por capas**. Para un proyecto de un solo desarrollador, esto da
la disciplina estructural de los límites de servicio sin el costo operativo de correr varios
servicios — un solo desplegable, una sola base de datos, un solo límite transaccional.

### 2.1 Capas

```
Controlador REST  ──  DTO  ──  Service  ──  Entity  ──  Repository  ──  MySQL
                                  │
                                  └──  ChatClient (Spring AI)  ──  OpenAI
```

Límites aplicados:

1. Los **controladores** manejan solo HTTP — enrutamiento, códigos de estado, entrada de
   validación. Aceptan y devuelven DTOs, nunca entidades.
2. Los **services** contienen la lógica de negocio, la orquestación de IA, y el mapeo
   Entity↔DTO.
3. Los **repositories** son interfaces de Spring Data JPA. Sin lógica de negocio.
4. Las **entities** mapean a tablas de MySQL y nunca salen de la capa de servicio.

Los errores se manejan de forma centralizada mediante un manejador global de excepciones
`@RestControllerAdvice`, así que los controladores no contienen código de mapeo de errores.

### 2.2 Superficie de peticiones

Ocho controladores: autenticación, temarios (`temarios`), asignaturas (`asignaturas`), generación
de contenido, exportaciones, suscripciones, usuarios, y el webhook de Stripe. Todas las rutas
viven bajo el path de contexto `/api/v1`.

## 3. Integración de IA

- **`ChatClient` de Spring AI** abstrae al proveedor. Nada en el código llama directamente al SDK
  de OpenAI, así que cambiar de proveedor es un cambio de configuración.
- **Asíncrono por defecto.** La generación corre en métodos `@Async` que devuelven
  `CompletableFuture`, en un pool de hilos dedicado `ai-`. El nivel de razonamiento suele tomar
  entre 30 y 90 segundos, así que el timeout de petición asíncrona de Spring MVC se sube a 180s —
  el valor por defecto de 30s devolvía silenciosamente un 503 antes de que el modelo terminara.
- **Los prompts son contenido, no código.** Cada system prompt vive en
  `src/main/resources/prompts/*.st` y se renderiza a través del `PromptTemplate` de Spring AI.
  `PromptTemplates` es la única clase que toca esos archivos; el texto de los prompts nunca se
  escribe directamente en un `.java`.
- **El formato de respuesta sigue al contenido.** La prosa (teoría) vuelve como markdown plano.
  El contenido inherentemente estructurado (exámenes, diapositivas) usa Structured Outputs de
  OpenAI vía `.entity(...)`, así que no hay parseo de JSON hecho a mano.
- **Opciones de llamada por nivel.** Deliberadamente no hay un `spring.ai.openai.chat.options`
  global por defecto: un valor por defecto global se fusiona en cada llamada, que es como
  `temperature` y `max_tokens` se filtraban antes hacia peticiones de modelos de razonamiento que
  las rechazan. Cada llamada define en su lugar un objeto de opciones completo y específico del
  nivel.

## 4. Diseño de base de datos

- **Claves primarias UUID** almacenadas como `CHAR(36)`, generadas por `@UuidGenerator` de
  Hibernate.
- **Borrados suaves.** Los borrados físicos están prohibidos para entidades de dominio; cada
  tabla lleva un `deleted_at` opcional y la entidad usa `@SQLDelete` + un filtro
  `deleted_at IS NULL`. La tabla `suscripcion` es una excepción deliberada — es un registro
  histórico de facturación, así que sus filas nunca se borran.
- **Carga perezosa (lazy)** por defecto en todas las relaciones, para evitar consultas N+1.
- **Solo Flyway.** Cada cambio de esquema es una migración `V[Version]__[Descripción].sql`; no
  hay intervenciones manuales en la base de datos y `ddl-auto` está en `validate`, así que la
  aplicación se niega a arrancar si el esquema y las entidades no coinciden.
- **Nomenclatura.** `snake_case` en MySQL, `camelCase` en Java. El vocabulario de dominio se
  mantiene en español (`temario`, `asignatura`, `suscripcion`) porque ese es el idioma del
  dominio del problema.

## 5. Facturación y aplicación de cuotas

Los límites de plan se aplican en tres puntos de control, todos en `PlanLimitService`, y todos
leyendo el plan **desde la base de datos** en lugar de desde el JWT:

1. **Verificación de capacidad → 403.** Corre antes de cualquier I/O: ¿este nivel puede usar el
   modelo Catedrático, generar diapositivas, subir un archivo, importar una URL, o usar un
   formato de exportación avanzado?
2. **Reserva de cuota → 429.** Corre justo antes de comprometerse con el pipeline asíncrono de
   IA, en una transacción `REQUIRES_NEW` contra una fila diaria de uso con
   `UNIQUE(usuario_id, fecha)`.
3. **Reembolso de cuota.** Si alguna pieza falla durante la generación, el conteo reservado se
   libera de vuelta.

El JWT lleva un claim `plan`, pero solo para que la UI pueda pintar la insignia del plan de
inmediato. Como un token vive una hora y un plan puede cambiar a mitad de sesión, cada
verificación real vuelve a leer la base de datos.

## 6. Seguridad

- **JWT sin estado** validado por un `JwtAuthenticationFilter` personalizado antes de los
  controladores. CSRF está deshabilitado porque no hay sesión ni credencial en cookie.
- **Lista blanca de CORS** vía `app.cors.allowed-origins`. Por defecto apunta al servidor de
  desarrollo de Vite para trabajo local, y producción **debe** sobrescribirla — el perfil prod no
  tiene valor por defecto.
- **Configuración de producción a prueba de fallos.** `application-prod.properties` declara
  `DB_*`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS` sin valores de respaldo, así que un secreto
  faltante detiene el arranque de la aplicación en lugar de correr silenciosamente sobre un valor
  de desarrollo.
- Las **firmas del webhook de Stripe** se verifican contra el cuerpo crudo de la petición antes
  de deserializar el payload.
- **Validación de entrada** en el límite del controlador con anotaciones de Jakarta Validation
  sobre records DTO.
- Los **cuerpos de error** exponen únicamente mensajes de validación escritos a mano; los stack
  traces nunca se incluyen.
- **Verificaciones de propiedad** en la capa de servicio: un usuario solo puede acceder a sus
  propios temarios y material.
