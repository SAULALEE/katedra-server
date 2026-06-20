# Arquitectura y Stack Tecnológico

## 1. Tecnologías Principales
El backend de Katedra está construido con tecnologías modernas y de nivel empresarial:
- **Lenguaje:** Java 21
- **Framework:** Spring Boot 3.x
- **Base de Datos:** MySQL 8.0
- **Migraciones de Base de Datos:** Flyway
- **Gestión de Secretos:** Doppler

## 2. Paradigma Arquitectónico
Katedra emplea una arquitectura de **Monolito Modular por Capas**. Este patrón proporciona el equilibrio perfecto entre la velocidad de desarrollo para un equipo de un solo desarrollador y el rigor estructural requerido para el escalado futuro.

### 2.1 Flujo de Datos y Límites
El sistema impone estrictamente la separación de responsabilidades a través de las siguientes capas:
1. **Controladores REST:** Responsables únicamente de manejar las peticiones HTTP, el formato de las respuestas y los códigos de estado. Interactúan exclusivamente con Objetos de Transferencia de Datos (DTOs).
2. **Capa de Servicio (Service):** Aloja la lógica de negocio central. Responsable del mapeo entre Entidades (representación de base de datos) y DTOs (representación de API).
3. **Capa de Repositorio (Repository):** Interfaces de Spring Data JPA que manejan las consultas a la base de datos. No reside ninguna lógica de negocio aquí.
4. **Entidades de Dominio (Entities):** Entidades JPA que se mapean directamente a las tablas de MySQL. Las entidades nunca se filtran a la capa del Controlador.

## 3. Principios de Diseño de Base de Datos
- **Claves Primarias:** Uso global de UUIDs (`CHAR(36)`) por seguridad y capacidades de generación distribuida.
- **Borrado Lógico (Soft Deletes):** La eliminación directa está prohibida. Todas las entidades de dominio utilizan una bandera de marca de tiempo `deleted_at` y anotaciones `@SQLDelete` / `@Where` en JPA para garantizar la retención de datos.
- **Migraciones:** Los cambios de esquema se gestionan estrictamente a través de Flyway (`V[Version]__[Description].sql`), asegurando despliegues consistentes.
- **Convenciones de Nomenclatura:** `snake_case` en MySQL, `camelCase` en Java.

## 4. Seguridad
- **JWT sin Estado:** La autenticación se maneja a través de JSON Web Tokens, validados por un filtro personalizado `JwtAuthenticationFilter` integrado con Spring Security.
- **Aislamiento de Entornos:** Las configuraciones sensibles (ej., `DB_PASSWORD`, `JWT_SECRET`) se inyectan dinámicamente a través de Doppler en tiempo de ejecución, evitando la filtración de credenciales en el repositorio.
