package Katedra.Server.service;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiContentGeneratorServiceTest {

    private static final String MODELO = "gpt-4o-mini";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private AiContentGeneratorService service;

    @BeforeEach
    void setUp() {
        given(chatClientBuilder.build()).willReturn(chatClient);
        service = new AiContentGeneratorService(chatClientBuilder);
    }

    private void stubContent(String response) {
        given(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .content()).willReturn(response);
    }

    @SuppressWarnings("unchecked")
    private <T> void stubEntity(T response) {
        given(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()
                .entity(any(ParameterizedTypeReference.class))).willReturn(response);
    }

    private void stubFailure() {
        given(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .options(any())
                .call()).willThrow(new RuntimeException("API unavailable"));
    }

    // --- teoria ---

    @Test
    void shouldGenerateTeoriaMarkdown() throws ExecutionException, InterruptedException {
        stubContent("## Teoría de prueba");

        String result = service.generarTeoria("Programacion", "Pilas", "Universitario", "Pilas y colas", MODELO).get();

        assertThat(result).isEqualTo("## Teoría de prueba");
    }

    @Test
    void shouldReturnFallbackTeoriaWhenAiCallFails() throws ExecutionException, InterruptedException {
        stubFailure();

        String result = service.generarTeoria("Programacion", "Pilas", "Universitario", null, MODELO).get();

        assertThat(result).startsWith("## Error");
    }

    // --- ejercicios ---

    @Test
    void shouldGenerateEjerciciosMarkdown() throws ExecutionException, InterruptedException {
        stubContent("## Ejercicios de prueba");

        String result = service.generarEjercicios("Programacion", "Pilas", "Universitario", "Pilas y colas", MODELO).get();

        assertThat(result).isEqualTo("## Ejercicios de prueba");
    }

    @Test
    void shouldReturnFallbackEjerciciosWhenAiCallFails() throws ExecutionException, InterruptedException {
        stubFailure();

        String result = service.generarEjercicios("Programacion", "Pilas", "Universitario", null, MODELO).get();

        assertThat(result).startsWith("## Error");
    }

    // --- evaluacion ---

    @Test
    void shouldGenerateEvaluacionStructured() throws ExecutionException, InterruptedException {
        List<EvaluacionPreguntaDTO> preguntas = List.of(
                new EvaluacionPreguntaDTO("¿Qué es una pila?", List.of("A", "B", "C", "D"), 1, "LIFO"));
        stubEntity(preguntas);

        List<EvaluacionPreguntaDTO> result =
                service.generarEvaluacion("Programacion", "Pilas", "Universitario", "Pilas y colas", MODELO).get();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().pregunta()).isEqualTo("¿Qué es una pila?");
        assertThat(result.getFirst().opcionCorrectaIndex()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyEvaluacionWhenAiCallFails() throws ExecutionException, InterruptedException {
        stubFailure();

        List<EvaluacionPreguntaDTO> result =
                service.generarEvaluacion("Programacion", "Pilas", "Universitario", null, MODELO).get();

        assertThat(result).isEmpty();
    }

    // --- diapositivas ---

    @Test
    void shouldGenerateDiapositivasStructured() throws ExecutionException, InterruptedException {
        List<DiapositivaDTO> diapositivas = List.of(new DiapositivaDTO("Slide 1", List.of("punto1", "punto2")));
        stubEntity(diapositivas);

        List<DiapositivaDTO> result =
                service.generarDiapositivas("Programacion", "Pilas", "Universitario", "Pilas y colas", MODELO, 5).get();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().puntos()).containsExactly("punto1", "punto2");
    }

    @Test
    void shouldReturnEmptyDiapositivasWhenAiCallFails() throws ExecutionException, InterruptedException {
        stubFailure();

        List<DiapositivaDTO> result =
                service.generarDiapositivas("Programacion", "Pilas", "Universitario", null, MODELO, 5).get();

        assertThat(result).isEmpty();
    }
}
