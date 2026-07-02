---
name: architecture-server
description: Backend architecture and modular monolith layered data flow
---

# SKILL: Backend Architecture & Data Flow (Spring Boot)

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Creating or modifying backend business logic, REST APIs, service layers, or data access repositories.
- **Exclusion:** Does not handle database table migrations directly (use `database-jpa-architect.md`) or AI prompt logic (use `ai-integration.md`).

## 2. STRICT ARCHITECTURAL RULES (T_σ)
- **Paradigm:** Layered Modular Monolith with integrated Spring AI.
- **Data Flow:** `REST Controller` ↔ `DTO` ↔ `Service` (ChatClient.prompt) ↔ `OpenAI API` → structured JSON ↔ `Entity` ↔ `Repository` ↔ `MySQL`.
- **Strict Boundaries:**
  - **Controllers:** Only handle HTTP requests/responses. Must accept and return ONLY Data Transfer Objects (DTOs), never JPA Entities.
  - **Services:** Contain core business logic and AI orchestration. Responsible for mapping between Entities and DTOs. Use ChatClient for LLM calls.
  - **ChatClient (Spring AI):** Abstracts OpenAI communication. All calls **MUST** be async (Mono, CompletableFuture, @Async).
  - **Entities:** Represent database state. Must NEVER leave the Service layer.
  - **Repositories:** Interfaces extending `JpaRepository` or similar. No business logic allowed here.
- **AI Integration Rules:**
  - **Async-First:** Never use blocking LLM calls. Use `chatClient.prompt().call()` with reactive wrappers.
  - **Prompt Isolation:** Store prompts in constants, config files, or dedicated builders. Never hardcode in methods.
  - **Structured Output:** Always use JSON mode. Validate response structure before persistence.
  - **Spanish Content:** All generated academic content must be professional Spanish appropriate for target grade level.
- **Communication:**
  - Standard REST over HTTP (Client ↔ Spring).
  - Strict HTTP status code usage (200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error).
  - Global Exception Handling via `@ControllerAdvice`.

## 3. STANDARD OPERATING PROCEDURE (π_σ)
1. **Identify AI Requirement:** Does the endpoint need LLM content generation?
   - **No:** Follow standard Spring monolith flow (steps 2-4 below)
   - **Yes:** Follow Spring AI flow (steps 2-5 below)

2. **Contract/DTO Definition:** Define Request and Response DTO records for the API endpoint.
3. **Repository Update:** Ensure the repository has the necessary query methods.
4. **Service Implementation:** Implement the business logic and Entity-to-DTO mapping.
   - If AI required: Inject `ChatClient` (Spring AI) into Service
   - Use `@Async` or return `Mono<T>` for async processing
   - Call `chatClient.prompt().system(...).user(...).call()` 
   - Parse structured JSON response, validate, save to database
5. **Controller Routing:** Expose the endpoint with appropriate HTTP verbs (`@GetMapping`, `@PostMapping`, etc.) and secure it if necessary.
   - If Service returns `Mono<T>` or `CompletableFuture<T>`, return `ResponseEntity<T>` wrapped in async type.

## 4. COMPACT RECIPE (FEW-SHOT)

### Example 1: Non-AI Endpoint (Standard Monolith)
Input: "Create an endpoint to get a course by ID"
Output Expected:
```java
// 1. DTO
public record CourseResponseDTO(String id, String title) {}

// 2. Service (NO AI call)
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

### Example 2: AI Content Generation Endpoint (Spring Boot + Spring AI)
Input: "Create an endpoint to generate course theory from syllabus"
Output Expected:
```java
// 1. Request/Response DTOs
public record GenerateTheoryRequestDTO(String tema, String materia) {}
public record GenerateTheoryResponseDTO(String teoria, String timestamp) {}

// 2. Prompt Constants
public class PromptTemplates {
    public static final String TEORIA_SYSTEM_PROMPT = """
        Eres un experto académico universitario. Genera teoría clara, estructurada y rigurosa.
        Responde SOLO con JSON válido, sin markdown.
        {
          "teoria": "Contenido de la teoría aquí (markdown permitido)"
        }
        """;
}

// 3. Service (async LLM call with Spring AI)
@Service
@RequiredArgsConstructor
public class TemarioService {
    private final TemarioRepository repository;
    private final ChatClient chatClient;
    
    @Async
    public CompletableFuture<GenerateTheoryResponseDTO> generarTeoriaAsync(String temarioId, String userEmail) {
        Temario temario = repository.findById(temarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Temario not found"));
        
        // Async LLM call via Spring AI (non-blocking)
        String userPrompt = "Tema: " + temario.getTema() + "\nMateria: " + temario.getMateria();
        
        String respuestaJson = chatClient.prompt()
            .system(PromptTemplates.TEORIA_SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .getResult()
            .getOutput()
            .getContent();
        
        // Parse JSON and validate
        Map<String, String> parsed = new ObjectMapper().readValue(respuestaJson, Map.class);
        String teoria = parsed.get("teoria");
        
        // Save to database
        temario.setTeoria(teoria);
        repository.save(temario);
        
        return CompletableFuture.completedFuture(
            new GenerateTheoryResponseDTO(teoria, Instant.now().toString())
        );
    }
}

// 4. Controller (returns async response)
@RestController
@RequestMapping("/temarios")
@RequiredArgsConstructor
public class TemarioController {
    private final TemarioService service;
    
    @PostMapping("/{id}/generar-teoria")
    public CompletableFuture<ResponseEntity<GenerateTheoryResponseDTO>> generarTeoria(
            @PathVariable String id,
            Authentication auth) {
        String userEmail = auth.getName();
        return service.generarTeoriaAsync(id, userEmail)
            .thenApply(ResponseEntity::ok);
    }
}
```

**Key Points:**
- ✅ No blocking calls (async with @Async/CompletableFuture)
- ✅ ChatClient handles OpenAI communication
- ✅ Structured JSON response validation
- ✅ No thread exhaustion, low CPU usage
