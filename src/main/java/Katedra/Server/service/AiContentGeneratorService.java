package Katedra.Server.service;

import Katedra.Server.config.PromptTemplates;
import Katedra.Server.dto.AiContenidoDTO;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class AiContentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiContentGeneratorService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiContentGeneratorService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Async
    public CompletableFuture<AiContenidoDTO> generarContenido(
            String materia, String tema, String unidades, String gradoAcademico) {
        try {
            log.info("Generando contenido IA para tema: {}", tema);
            String userPrompt = PromptTemplates.buildUserPrompt(materia, tema, unidades, gradoAcademico);
            String jsonResponse = chatClient.prompt()
                    .system(PromptTemplates.CONTENIDO_TEMARIO_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            AiContenidoDTO contenido = objectMapper.readValue(stripMarkdownFences(jsonResponse), AiContenidoDTO.class);
            log.info("Contenido generado exitosamente para tema: {}", tema);
            return CompletableFuture.completedFuture(contenido);
        } catch (Exception ex) {
            log.error("Error generando contenido IA para tema: {}", tema, ex);
            return CompletableFuture.completedFuture(new AiContenidoDTO(
                    "## Error: La generación falló",
                    "Intenta de nuevo",
                    List.of(),
                    List.of()
            ));
        }
    }

    // The model may ignore the "no markdown" instruction and wrap the JSON in code fences
    private String stripMarkdownFences(String response) {
        if (response == null) {
            return "";
        }
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned;
    }
}
