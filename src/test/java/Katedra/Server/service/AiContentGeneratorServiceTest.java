package Katedra.Server.service;

import Katedra.Server.dto.AiContenidoDTO;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiContentGeneratorServiceTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private AiContentGeneratorService service;

    private static final String VALID_JSON = """
            {
              "teoria": "## Teoría de prueba",
              "ejercicios": "## Ejercicios de prueba",
              "evaluacion": [
                {"pregunta": "¿Qué es una pila?", "opciones": ["A", "B", "C", "D"], "opcionCorrectaIndex": 1, "explicacion": "LIFO"}
              ],
              "diapositivas": [
                {"titulo": "Slide 1", "puntos": ["punto1", "punto2"]}
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.build()).willReturn(chatClient);
        service = new AiContentGeneratorService(chatClientBuilder, new ObjectMapper());
    }

    private void stubAiResponse(String response) {
        given(chatClient.prompt()
                .system(org.mockito.ArgumentMatchers.anyString())
                .user(org.mockito.ArgumentMatchers.anyString())
                .call()
                .content()).willReturn(response);
    }

    @Test
    void shouldParseValidJsonResponse() throws ExecutionException, InterruptedException {
        stubAiResponse(VALID_JSON);

        AiContenidoDTO result = service.generarContenido("Programacion", "Pilas", "Unidad 1", "Universitario").get();

        assertThat(result.teoria()).isEqualTo("## Teoría de prueba");
        assertThat(result.ejercicios()).isEqualTo("## Ejercicios de prueba");
        assertThat(result.evaluacion()).hasSize(1);
        assertThat(result.evaluacion().getFirst().pregunta()).isEqualTo("¿Qué es una pila?");
        assertThat(result.evaluacion().getFirst().opcionCorrectaIndex()).isEqualTo(1);
        assertThat(result.diapositivas()).hasSize(1);
        assertThat(result.diapositivas().getFirst().puntos()).containsExactly("punto1", "punto2");
    }

    @Test
    void shouldParseJsonWrappedInMarkdownFences() throws ExecutionException, InterruptedException {
        stubAiResponse("```json\n" + VALID_JSON + "\n```");

        AiContenidoDTO result = service.generarContenido("Programacion", "Pilas", "Unidad 1", "Universitario").get();

        assertThat(result.teoria()).isEqualTo("## Teoría de prueba");
        assertThat(result.evaluacion()).hasSize(1);
    }

    @Test
    void shouldReturnFallbackContentWhenResponseIsInvalidJson() throws ExecutionException, InterruptedException {
        stubAiResponse("esto no es JSON");

        AiContenidoDTO result = service.generarContenido("Programacion", "Pilas", "Unidad 1", "Universitario").get();

        assertThat(result.teoria()).startsWith("## Error");
        assertThat(result.evaluacion()).isEmpty();
        assertThat(result.diapositivas()).isEmpty();
    }

    @Test
    void shouldReturnFallbackContentWhenAiCallFails() throws ExecutionException, InterruptedException {
        given(chatClient.prompt()
                .system(org.mockito.ArgumentMatchers.anyString())
                .user(org.mockito.ArgumentMatchers.anyString())
                .call()
                .content()).willThrow(new RuntimeException("API unavailable"));

        AiContenidoDTO result = service.generarContenido("Programacion", "Pilas", "Unidad 1", "Universitario").get();

        assertThat(result.teoria()).startsWith("## Error");
        assertThat(result.evaluacion()).isEmpty();
    }
}
