package Katedra.Server.service;

import Katedra.Server.config.PromptTemplates;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Per-piece AI generation. Each method is independently async so callers can
 * generate any subset of the material in parallel, with the model chosen per request.
 * Structured pieces (evaluacion, diapositivas) use Spring AI structured output
 * instead of manual JSON parsing.
 */
@Service
public class AiContentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiContentGeneratorService.class);

    private final ChatClient chatClient;

    public AiContentGeneratorService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Async
    public CompletableFuture<String> generarTeoria(
            String asignatura, String titulo, String gradoAcademico, String fuente, String modelo) {
        try {
            log.info("Generando teoría [{}] para tema: {}", modelo, titulo);
            String texto = chatClient.prompt()
                    .system(PromptTemplates.TEORIA_SYSTEM_PROMPT)
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, gradoAcademico, fuente))
                    .options(OpenAiChatOptions.builder().model(modelo))
                    .call()
                    .content();
            return CompletableFuture.completedFuture(texto);
        } catch (Exception ex) {
            log.error("Error generando teoría para tema: {}", titulo, ex);
            return CompletableFuture.completedFuture(
                    "## Error: La generación de teoría falló. Intenta de nuevo.");
        }
    }

    @Async
    public CompletableFuture<String> generarEjercicios(
            String asignatura, String titulo, String gradoAcademico, String fuente, String modelo) {
        try {
            log.info("Generando ejercicios [{}] para tema: {}", modelo, titulo);
            String texto = chatClient.prompt()
                    .system(PromptTemplates.EJERCICIOS_SYSTEM_PROMPT)
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, gradoAcademico, fuente))
                    .options(OpenAiChatOptions.builder().model(modelo))
                    .call()
                    .content();
            return CompletableFuture.completedFuture(texto);
        } catch (Exception ex) {
            log.error("Error generando ejercicios para tema: {}", titulo, ex);
            return CompletableFuture.completedFuture(
                    "## Error: La generación de ejercicios falló. Intenta de nuevo.");
        }
    }

    @Async
    public CompletableFuture<List<EvaluacionPreguntaDTO>> generarEvaluacion(
            String asignatura, String titulo, String gradoAcademico, String fuente, String modelo) {
        try {
            log.info("Generando evaluación [{}] para tema: {}", modelo, titulo);
            List<EvaluacionPreguntaDTO> preguntas = chatClient.prompt()
                    .system(PromptTemplates.EVALUACION_SYSTEM_PROMPT)
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, gradoAcademico, fuente))
                    .options(OpenAiChatOptions.builder().model(modelo))
                    .call()
                    .entity(new ParameterizedTypeReference<List<EvaluacionPreguntaDTO>>() {});
            return CompletableFuture.completedFuture(preguntas);
        } catch (Exception ex) {
            log.error("Error generando evaluación para tema: {}", titulo, ex);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Async
    public CompletableFuture<List<DiapositivaDTO>> generarDiapositivas(
            String asignatura, String titulo, String gradoAcademico, String fuente, String modelo,
            int numeroDiapositivas) {
        try {
            log.info("Generando {} diapositivas [{}] para tema: {}", numeroDiapositivas, modelo, titulo);
            List<DiapositivaDTO> diapositivas = chatClient.prompt()
                    .system(PromptTemplates.buildDiapositivasSystemPrompt(numeroDiapositivas))
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, gradoAcademico, fuente))
                    .options(OpenAiChatOptions.builder().model(modelo))
                    .call()
                    .entity(new ParameterizedTypeReference<List<DiapositivaDTO>>() {});
            return CompletableFuture.completedFuture(diapositivas);
        } catch (Exception ex) {
            log.error("Error generando diapositivas para tema: {}", titulo, ex);
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
