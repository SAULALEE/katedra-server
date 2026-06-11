# SKILL: Database Architecture, JPA, & Flyway (Katedra Core)

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Creating or modifying database schemas, writing Spring Boot JPA entities, creating repositories, or writing Flyway migration scripts.
- **Exclusion:** Does not handle REST routing (use `ARCHITECTURE.md`).

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Database Engine:** MySQL 8.0.
- **Naming Conventions:**
  - **Database:** `snake_case` for all table names and column names (e.g., `temarios_conceptos`, `deleted_at`).
  - **Java Entities:** `camelCase` for fields, PascalCase for Class names (e.g., `TemarioConcepto`).
- **Primary Keys:** Always use UUIDs stored as `CHAR(36)`.
  - **Java Mapping:**
    ```java
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;
    ```
- **Soft Deletes:** Direct hard deletes are strictly forbidden for major domain entities. All tables must have a `deleted_at` nullable timestamp column.
  - **Java Mapping:**
    ```java
    @SQLDelete(sql = "UPDATE table_name SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
    @Where(clause = "deleted_at IS NULL")
    ```
- **JPA Relationships:**
  - Avoid `FetchType.EAGER` unless strictly required. Default to `FetchType.LAZY` for all `@ManyToOne`, `@OneToOne`, and `@OneToMany` annotations to prevent N+1 select queries.
  - Always map foreign keys explicitly.
- **Migrations:** All schema changes must be declared using Flyway migration scripts named according to the pattern `V[Version]__[Description].sql` under `src/main/resources/db/migration/`. No direct DB manual interventions.

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Flyway Migration:** Write the SQL migration file. Ensure UUID fields are defined as `CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin`.
2. **JPA Entity Creation:**
   - Define fields, relationships, and soft-delete SQL updates.
   - Match MySQL table and column names exactly using `@Table` and `@Column`.
3. **Repository Definition:** Create the Spring Data JPA interface extending `JpaRepository<Entity, String>`. Use Query methods or `@Query` annotations for custom lookups.
4. **DTO and Mapping:** Construct the matching Request/Response DTOs and implement mapper logic in the service layer.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Add a database table and JPA entity for 'Modulo' with soft deletes and UUID"
Output Expected:
```sql
-- 1. Flyway: src/main/resources/db/migration/V2__create_modulo_table.sql
CREATE TABLE modulo (
    id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

```java
// 2. Entity: src/main/java/com/katedra/model/Modulo.java
package com.katedra.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(name = "modulo")
@SQLDelete(sql = "UPDATE modulo SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Modulo {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Getters, Setters, Constructors
}
```
