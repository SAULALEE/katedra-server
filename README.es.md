# Katedra Server

[![CI](https://github.com/SAULALEE/katedra-server/actions/workflows/ci.yml/badge.svg)](https://github.com/SAULALEE/katedra-server/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Backend de **Katedra**, un generador de contenido académico impulsado por IA para
profesores. Se le da un temario —escrito a mano, subido en archivo o desde una URL— y genera
la estructura, la teoría, un examen y diapositivas, usando llamadas reales a OpenAI a través
de Spring AI. Spring Boot 4, Java 21, MySQL 8, facturación con Stripe.

Versión en inglés: [README.md](README.md) · Frontend: [katedra-client](https://github.com/SAULALEE/katedra-client)

---

## Demo en vivo

- **App:** https://katedra-client.vercel.app
- **API:** https://katedra-server.onrender.com/api/v1

> **Credenciales de demo:** aún no publicadas — ver [Cuentas de demo](#cuentas-de-demo) más abajo.
> El API está alojado en el plan gratuito de Render, así que la primera petición tras un
> período de inactividad puede tardar hasta un minuto en despertar el servicio.

### Cuentas de demo

Cualquier correo que termine en `@katedra.com` se autoregistra como administrador
(`AuthService.resolveRoleByEmail`); cualquier otro correo se registra como cuenta de profesor
normal en el plan Gratis. Regístrate en `/auth/register` para probar la app de inmediato —
una cuenta de demo pública con un login Free y otro Pro está planeada pero aún no publicada.

---

## Arquitectura

```mermaid
flowchart LR
    Client["katedra-client (React)"] -->|REST /api/v1| Controller
    subgraph Server["katedra-server (Spring Boot)"]
        Controller["Controllers\n(solo DTOs)"] --> Service["Services\n(lógica de negocio)"]
        Service --> Repo["Repositories\n(Spring Data JPA)"]
        Service --> AI["Spring AI ChatClient"]
        Service --> Stripe["Stripe SDK"]
    end
    Repo --> DB[(MySQL 8)]
    AI --> OpenAI[("OpenAI\ngpt-4.1-mini / o4-mini")]
    Stripe --> StripeAPI[("Stripe API")]
```

Un monolito modular por capas: los controladores solo ven DTOs, las entidades nunca salen de
la capa de servicio, todas las llamadas a IA y Stripe son asíncronas. Detalles en
[docs/2_ARCHITECTURE_AND_TECH_STACK.md](docs/2_ARCHITECTURE_AND_TECH_STACK.md).

---

## Stack tecnológico

| | |
|---|---|
| Lenguaje / framework | Java 21, Spring Boot 4.0.6 |
| IA | Spring AI `ChatClient` → OpenAI (`gpt-4.1-mini` / `o4-mini`) |
| Base de datos | MySQL 8.0, migraciones Flyway |
| Autenticación | JWT sin estado, OAuth2 opcional (Google/Microsoft) |
| Facturación | Stripe Subscriptions |
| Tests | JUnit 5, Mockito, AssertJ, `@WebMvcTest` — 336 tests |
| Documentación de API | springdoc (generado en vivo desde el código) + colección Bruno |
| Contenedor | Dockerfile multi-etapa, usuario no-root |

Panorama completo: [docs/1_PROJECT_OVERVIEW.md](docs/1_PROJECT_OVERVIEW.md) ·
[docs/3_STATUS_AND_ROADMAP.md](docs/3_STATUS_AND_ROADMAP.md).

---

## Inicio rápido (sin necesitar cuenta de Doppler)

Requiere Docker.

```bash
git clone https://github.com/SAULALEE/katedra-server.git
cd katedra-server
cp .env.example .env
# edita .env y define OPENAI_API_KEY para generar contenido real — todo lo demás ya tiene un valor por defecto funcional
docker compose up -d
```

El API queda disponible en `http://localhost:8080/api/v1`. Confírmalo:

```bash
curl http://localhost:8080/api/v1/auth/register -X POST -H "Content-Type: application/json" \
  -d '{"email":"tu@ejemplo.com","password":"Test1234!","nombre":"Tu Nombre"}'
```

Stripe es opcional para todo excepto `GET /suscripciones/planes` (el listado público de
precios) — ese endpoint llama a Stripe incondicionalmente y necesita
`STRIPE_PRICE_PRO_MENSUAL` / `STRIPE_PRICE_PRO_ANUAL` configurados para responder. Todo lo
demás funciona con la facturación en blanco.

### Ejecutar sin Docker

Java 21 y un MySQL 8 local son suficientes — cada propiedad en `application.properties` tiene
un valor por defecto de desarrollo local, así que la app arranca contra
`localhost:3306/katedra_dev` sin ninguna configuración adicional:

```bash
./mvnw spring-boot:run
```

Quienes tengan acceso a la organización de Doppler del proyecto pueden omitir `.env` por completo:

```bash
doppler run -- ./mvnw spring-boot:run
```

---

## Tests

```bash
./mvnw test
```

336 tests —JUnit 5, Mockito, AssertJ, slices `@WebMvcTest`— corren contra una base de datos
H2 en memoria con Flyway deshabilitado, sin necesitar ningún servicio externo. CI ejecuta esto
en cada push a `main` y `staging`.

---

## Documentación de la API

Dos fuentes, siempre sincronizadas entre sí porque ambas provienen del mismo código en ejecución:

- **Swagger UI** — arranca la app y abre `http://localhost:8080/api/v1/swagger-ui/index.html`.
  Generado en vivo desde los controladores (springdoc), por lo que no puede desincronizarse
  del código como sí le pasaba a una especificación escrita a mano. Spec cruda en
  `/api/v1/v3/api-docs`.
- **[Colección de Bruno](api/bruno/)** — 37 peticiones, una por endpoint, con un entorno
  `Local` y otro `Production` y un orden de ejecución sugerido. Gratis, offline, en texto
  plano — abre `api/bruno/` como colección en [Bruno](https://www.usebruno.com/).

---

## Estructura del proyecto

```
src/main/java/Katedra/Server/
  controller/   endpoints REST — solo DTOs de entrada y salida
  service/      lógica de negocio, orquestación de IA, orquestación de Stripe
  repository/   interfaces de Spring Data JPA
  model/        entidades JPA y enums de dominio
  dto/          records de request/response
  config/       seguridad, CORS, OpenAPI, OAuth2, configuración de Stripe
src/main/resources/
  db/migration/ scripts de Flyway
  prompts/      prompts de IA (contenido, no código — nunca en línea en un .java)
api/bruno/      la colección de API de Bruno
docs/           documentación de arquitectura y roadmap
```

---

## Roadmap

Ver [docs/3_STATUS_AND_ROADMAP.md](docs/3_STATUS_AND_ROADMAP.md) para lo que ya está
publicado y lo que sigue —brevemente: modo live de Stripe, límite de uso para la cuenta de
demo, tests de integración contra una base de datos real, y generación de diapositivas más
profunda.
