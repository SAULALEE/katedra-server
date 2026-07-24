package Katedra.Server.service;

import Katedra.Server.config.OpenAiOptionsFactory;
import Katedra.Server.config.PromptTemplates;
import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
    public CompletableFuture<String> generarEstructura(
            String asignatura, String titulo, NivelAcademico nivel, String fuente, ModeloIA modelo,
            int numeroModulos) {
        try {
            log.info("Generando estructura [{}] ({} módulos) para tema: {} (nivel {})",
                    modelo.getDisplayName(), numeroModulos, titulo, nivel.getEtiqueta());
            String texto = chatClient.prompt()
                    .system(PromptTemplates.buildEstructuraSystemPrompt(nivel, numeroModulos))
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, nivel.getEtiqueta(), fuente))
                    .options(OpenAiOptionsFactory.forModelo(modelo))
                    .call()
                    .content();
            return CompletableFuture.completedFuture(texto);
        } catch (Exception ex) {
            log.error("Error generando estructura para tema: {}", titulo, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async
    public CompletableFuture<String> generarTeoria(
            String asignatura, String titulo, NivelAcademico nivel, String fuente, ModeloIA modelo,
            int numeroParrafos) {
        try {
            log.info("Generando teoría [{}] ({} párrafos) para tema: {} (nivel {})",
                    modelo.getDisplayName(), numeroParrafos, titulo, nivel.getEtiqueta());
            String texto = chatClient.prompt()
                    .system(PromptTemplates.buildTeoriaSystemPrompt(modelo, nivel, numeroParrafos))
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, nivel.getEtiqueta(), fuente))
                    .options(OpenAiOptionsFactory.forModelo(modelo))
                    .call()
                    .content();
            return CompletableFuture.completedFuture(texto);
        } catch (Exception ex) {
            log.error("Error generando teoría para tema: {}", titulo, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Grounded in {@code teoriaTexto} (the theory already generated/stored for this
     * topic), never the raw syllabus source: {@link Katedra.Server.service.ContenidoTemarioService}
     * guarantees theory exists before dispatching this call.
     */
    @Async
    public CompletableFuture<List<EvaluacionPreguntaDTO>> generarEvaluacion(
            String asignatura, String titulo, NivelAcademico nivel, String teoriaTexto, ModeloIA modelo,
            int numeroPreguntas) {
        try {
            log.info("Generando {} preguntas de evaluación [{}] para tema: {} (nivel {})",
                    numeroPreguntas, modelo.getDisplayName(), titulo, nivel.getEtiqueta());
            List<EvaluacionPreguntaDTO> preguntas = chatClient.prompt()
                    .system(PromptTemplates.buildEvaluacionSystemPrompt(modelo, nivel, numeroPreguntas))
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, nivel.getEtiqueta(), teoriaTexto))
                    .options(OpenAiOptionsFactory.forModelo(modelo))
                    .call()
                    .entity(new ParameterizedTypeReference<List<EvaluacionPreguntaDTO>>() {});
            return CompletableFuture.completedFuture(preguntas);
        } catch (Exception ex) {
            log.error("Error generando evaluación para tema: {}", titulo, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Grounded in {@code teoriaTexto} (the theory already generated/stored for this
     * topic), never the raw syllabus source: {@link Katedra.Server.service.ContenidoTemarioService}
     * guarantees theory exists before dispatching this call.
     */
    @Async
    public CompletableFuture<List<DiapositivaDTO>> generarDiapositivas(
            String asignatura, String titulo, String gradoAcademico, String teoriaTexto, ModeloIA modelo,
            int numeroDiapositivas) {
        try {
            log.info("Generando {} diapositivas [{}] para tema: {}", numeroDiapositivas, modelo.getDisplayName(), titulo);
            List<DiapositivaDTO> diapositivas = chatClient.prompt()
                    .system(PromptTemplates.buildDiapositivasSystemPrompt(numeroDiapositivas))
                    .user(PromptTemplates.buildUserPrompt(asignatura, titulo, gradoAcademico, teoriaTexto))
                    .options(OpenAiOptionsFactory.forModelo(modelo))
                    .call()
                    .entity(new ParameterizedTypeReference<List<DiapositivaDTO>>() {});
            return CompletableFuture.completedFuture(diapositivas);
        } catch (Exception ex) {
            log.error("Error generando diapositivas para tema: {}", titulo, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
