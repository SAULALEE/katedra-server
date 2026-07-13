package Katedra.Server.service;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiContentGeneratorServiceTest {

    private static final ModeloIA MODELO = ModeloIA.FLASH;

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

        String result = service.generarTeoria(
                "Programacion", "Pilas", NivelAcademico.UNIVERSITARIO, "Pilas y colas", ModeloIA.FLASH).get();

        assertThat(result).isEqualTo("## Teoría de prueba");
    }

    @Test
    void shouldGenerateTeoriaWithReasoningTierWithoutTemperature() throws ExecutionException, InterruptedException {
        stubContent("## Teoría profunda");

        String result = service.generarTeoria(
                "Programacion", "Pilas", NivelAcademico.POSGRADO, "Pilas y colas", ModeloIA.MAX).get();

        assertThat(result).isEqualTo("## Teoría profunda");
    }

    @Test
    void shouldPropagateFailureWhenTeoriaAiCallFails() {
        stubFailure();

        CompletableFuture<String> future = service.generarTeoria(
                "Programacion", "Pilas", NivelAcademico.UNIVERSITARIO, null, ModeloIA.FLASH);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause().hasMessageContaining("API unavailable");
    }

    // --- evaluacion ---

    @Test
    void shouldGenerateEvaluacionStructured() throws ExecutionException, InterruptedException {
        List<EvaluacionPreguntaDTO> preguntas = List.of(
                new EvaluacionPreguntaDTO("¿Qué es una pila?", List.of("A", "B", "C", "D"), 1, "LIFO"));
        stubEntity(preguntas);

        List<EvaluacionPreguntaDTO> result = service.generarEvaluacion(
                "Programacion", "Pilas", NivelAcademico.UNIVERSITARIO, "Pilas y colas", MODELO).get();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().pregunta()).isEqualTo("¿Qué es una pila?");
        assertThat(result.getFirst().opcionCorrectaIndex()).isEqualTo(1);
    }

    @Test
    void shouldPropagateFailureWhenEvaluacionAiCallFails() {
        stubFailure();

        CompletableFuture<List<EvaluacionPreguntaDTO>> future = service.generarEvaluacion(
                "Programacion", "Pilas", NivelAcademico.UNIVERSITARIO, null, MODELO);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause().hasMessageContaining("API unavailable");
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
    void shouldPropagateFailureWhenDiapositivasAiCallFails() {
        stubFailure();

        CompletableFuture<List<DiapositivaDTO>> future =
                service.generarDiapositivas("Programacion", "Pilas", "Universitario", null, MODELO, 5);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause().hasMessageContaining("API unavailable");
    }
}
