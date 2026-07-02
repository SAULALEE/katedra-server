---
name: ai-integration
description: AI prompt engineering and structured content generation guidelines
---

# SKILL: AI Integration & Prompt Engineering (Katedra Core)

## 1. CONTEXT OF ACTIVATION (C_σ)
- **Trigger:** Defining prompt templates, integrating Spring AI ChatClient, calling OpenAI via Spring AI, parsing structured LLM responses, or modifying services that generate educational content.
- **Exclusion:** Does not handle database storage structure (use `database-jpa-architect.md`) or REST routing mechanisms (use `architecture.md`).

## 2. STRICT ARCHITECTURAL RULES (T_σ)

- **Spring AI Integration:** Use ChatClient (Spring AI's abstraction) for all LLM interactions. Never call OpenAI SDK directly.
- **Structured JSON Engine:** Always use JSON mode in prompts. Leverage OpenAI's `response_format: {"type": "json_object"}` for guaranteed valid JSON.
- **Prompt Isolation:** Store prompts in isolated constants, config files, or dedicated template builders. Never hardcode prompts in methods.
- **Async-First Requirement:** ALL LLM calls MUST use async patterns:
  - Use `@Async` with `CompletableFuture<T>` for Service methods
  - Or use `Mono<T>` for Project Reactor integration
  - NEVER use blocking calls without wrapping in async
- **Error Resilience:** Implement robust validation for LLM JSON responses. If parsing fails or response is incomplete, log error and return a fallback academic payload (never throw raw 500 to client).
- **Localisation:** All generated academic content (theory, exercises, evaluations, slides) must be professional, grammatically impeccable Spanish appropriate for the target audience.
- **Token Optimization:** Keep prompts dense and precise. Avoid sending excessive historical context. Use few-shot examples sparingly to reduce latency and API costs.
- **LLM Provider:** Primary provider is **OpenAI (GPT-4, GPT-4-turbo, GPT-4o)** with structured output support. Spring AI abstraction allows switching to Anthropic, Azure OpenAI, etc. with config-only changes.

## 3. STANDARD OPERATING PROCEDURE (π_σ)

1. **JSON Schema Definition:** Design the exact target JSON structure that represents the desired educational output (e.g., questions, explanations, theory sections).
2. **Prompt Drafting:** Write the system prompt (define persona as academic expert) and user prompt (supply syllabus variables, grade level, constraints). Store in constants/config.
3. **Inject ChatClient:** In your Service, inject Spring AI's `ChatClient` bean (auto-configured if `spring-ai-openai-starter` is in pom.xml).
4. **Async LLM Call:** Use `@Async` or `Mono` wrapper:
   ```java
   @Async
   public CompletableFuture<String> generarContenido(String tema) {
       String json = chatClient.prompt()
           .system("Eres experto académico...")
           .user("Tema: " + tema)
           .call()
           .getResult()
           .getOutput()
           .getContent();
       return CompletableFuture.completedFuture(json);
   }
   ```
5. **Response Parsing:** Parse JSON response into Spring DTOs. Validate required fields exist.
6. **Error Handling:** Catch exceptions. Log and return graceful error to client (not raw 500).
7. **Persistence:** Save validated content to MySQL via JPA Repository.

## 4. COMPACT RECIPE (FEW-SHOT)
Input: "Create a system to generate 3 multiple choice questions from a syllabus (using Spring AI)"
Output Expected:

```java
// 1. Prompt Constants
public class PromptTemplates {
    public static final String EVALUACION_SYSTEM_PROMPT = """
        Eres un generador académico experto. Genera preguntas de opción múltiple en español.
        Responde SOLO con JSON válido, sin markdown.
        {
          "preguntas": [
            {
              "pregunta": "Texto de la pregunta",
              "opciones": ["Opción A", "Opción B", "Opción C", "Opción D"],
              "opcionCorrectaIndex": 0,
              "explicacion": "Explicación detallada"
            }
          ]
        }
        """;
}

// 2. DTOs
public record EvaluacionPreguntaDTO(
    String pregunta,
    List<String> opciones,
    Integer opcionCorrectaIndex,
    String explicacion
) {}

public record EvaluacionResponseDTO(
    List<EvaluacionPreguntaDTO> preguntas
) {}

// 3. Service (async with Spring AI ChatClient)
@Service
@RequiredArgsConstructor
public class TemarioService {
    private final ChatClient chatClient;
    private final ContenidoTemarioRepository contenidoRepository;
    private final ObjectMapper objectMapper;
    
    @Async
    public CompletableFuture<EvaluacionResponseDTO> generarEvaluacionAsync(String temarioId, String tema, String materia) {
        String userPrompt = "Genera 3 preguntas sobre: " + tema + " en " + materia;
        
        // Async call to OpenAI via Spring AI (non-blocking)
        String rawJson = chatClient.prompt()
            .system(PromptTemplates.EVALUACION_SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .getResult()
            .getOutput()
            .getContent();
        
        // Parse and validate JSON
        EvaluacionResponseDTO evaluacion = objectMapper.readValue(rawJson, EvaluacionResponseDTO.class);
        
        // Save to database
        ContenidoTemario contenido = contenidoRepository.findByTemarioId(temarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Temario not found"));
        contenido.setEvaluacion(evaluacion.preguntas());
        contenidoRepository.save(contenido);
        
        return CompletableFuture.completedFuture(evaluacion);
    }
}

// 4. Controller (returns async response)
@RestController
@RequestMapping("/temarios")
@RequiredArgsConstructor
public class TemarioController {
    private final TemarioService service;
    
    @PostMapping("/{id}/generar-evaluacion")
    public CompletableFuture<ResponseEntity<EvaluacionResponseDTO>> generarEvaluacion(
            @PathVariable String id,
            @RequestBody GenerarEvaluacionRequestDTO request,
            Authentication auth) {
        String userEmail = auth.getName();
        return service.generarEvaluacionAsync(id, request.tema(), request.materia())
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.internalServerError().build());
    }
}
```

**Key Advantages:**
- ✅ Single service (Spring Boot + Spring AI integrated)
- ✅ No thread blocking (async with @Async/CompletableFuture)
- ✅ Low CPU usage (non-blocking I/O)
- ✅ Easy provider switch (config change only, no code change)
