# SKILL: Backend Architecture & Data Flow (Spring Boot)

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Creating or modifying backend business logic, REST APIs, service layers, or data access repositories.
- **Exclusion:** Does not handle database table migrations directly (use `database-jpa-architect.md`) or AI prompt logic (use `ai-integration.md`).

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Paradigm:** Layered Modular Monolith architecture.
- **Data Flow:** `REST Controller` ↔ `DTO` ↔ `Service` ↔ `Entity` ↔ `Repository` ↔ `MySQL`.
- **Strict Boundaries:**
  - **Controllers:** Only handle HTTP requests/responses. Must accept and return ONLY Data Transfer Objects (DTOs), never JPA Entities.
  - **Services:** Contain core business logic. Responsible for mapping between Entities and DTOs.
  - **Entities:** Represent database state. Must NEVER leave the Service layer.
  - **Repositories:** Interfaces extending `JpaRepository` or similar. No business logic allowed here.
- **Communication:**
  - Standard REST over HTTP.
  - Strict HTTP status code usage (200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error).
  - Global Exception Handling via `@ControllerAdvice`.

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Contract/DTO Definition:** Define Request and Response DTO records for the API endpoint.
2. **Repository Update:** Ensure the repository has the necessary query methods.
3. **Service Implementation:** Implement the business logic and Entity-to-DTO mapping.
4. **Controller Routing:** Expose the endpoint with appropriate HTTP verbs (`@GetMapping`, `@PostMapping`, etc.) and secure it if necessary.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Create an endpoint to get a course by ID"
Output Expected:
```java
// 1. DTO
public record CourseResponseDTO(String id, String title) {}

// 2. Service
@Service
@RequiredArgsConstructor
public class CourseService {
    private final CourseRepository repository;
    
    public CourseResponseDTO getCourseById(String id) {
        Course entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        return new CourseResponseDTO(entity.getId(), entity.getTitle());
    }
}

// 3. Controller
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;
    
    @GetMapping("/{id}")
    public ResponseEntity<CourseResponseDTO> getCourse(@PathVariable String id) {
        return ResponseEntity.ok(service.getCourseById(id));
    }
}
```
