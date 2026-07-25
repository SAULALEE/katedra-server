package Katedra.Server.service.export;

import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppsScriptExportadorTest {

    private final AppsScriptExportador exportador = new AppsScriptExportador();

    private MaterialExportableDTO evaluacion(List<EvaluacionPreguntaDTO> preguntas) {
        return new MaterialExportableDTO("Matemáticas", "Matemáticas para la Ingeniería",
                "universitario", PiezaMaterial.EVALUACION, null, preguntas, null);
    }

    private String exportar(List<EvaluacionPreguntaDTO> preguntas) throws IOException {
        return new String(exportador.exportar(evaluacion(preguntas)), StandardCharsets.UTF_8);
    }

    private static final List<EvaluacionPreguntaDTO> PREGUNTAS = List.of(
            new EvaluacionPreguntaDTO("¿Qué es un vector?",
                    List.of("Un escalar", "Un elemento de un espacio vectorial"), 1,
                    "Pertenece a un espacio vectorial."));

    @Test
    void deberiaDeclararSuFormato() {
        assertThat(exportador.formato()).isEqualTo(FormatoExportacion.APPS_SCRIPT);
    }

    @Test
    void deberiaCrearElFormularioEnModoCuestionario() throws IOException {
        String script = exportar(PREGUNTAS);

        assertThat(script)
                .contains("function crearFormulario()")
                .contains("FormApp.create(")
                .contains("setIsQuiz(true)")
                .contains("addMultipleChoiceItem()")
                .contains("setChoices(")
                .contains("setPoints(");
    }

    @Test
    void deberiaMarcarLaOpcionCorrecta() throws IOException {
        String script = exportar(PREGUNTAS);

        assertThat(script).contains("\"correcta\":1");
        assertThat(script).contains("idx === p.correcta");
    }

    @Test
    void deberiaIncluirLasOpcionesYLaExplicacion() throws IOException {
        String script = exportar(PREGUNTAS);

        assertThat(script)
                .contains("Un elemento de un espacio vectorial")
                .contains("Pertenece a un espacio vectorial.");
    }

    @Test
    void deberiaProducirSoloCaracteresAscii() throws IOException {
        String script = exportar(PREGUNTAS);

        assertThat(script.chars().allMatch(c -> c < 128)).isTrue();
        assertThat(script).contains("\\u00e1"); // "Matemáticas" escaped, never a raw á
    }

    @Test
    void deberiaEscaparComillasYSaltosDeLinea() throws IOException {
        String script = exportar(List.of(new EvaluacionPreguntaDTO(
                "Es \"obvio\"\nen dos líneas", List.of("a's", "b\\c"), 0, "Explicación 'simple'")));

        assertThat(script).doesNotContain("Es \"obvio\"");
        for (String linea : script.split("\n")) {
            long comillas = linea.chars().filter(c -> c == '"').count();
            assertThat(comillas % 2).as("comillas balanceadas en: %s", linea).isZero();
        }
    }

    @Test
    void deberiaIncluirElAvisoDeCopyrightYLasInstrucciones() throws IOException {
        String script = exportar(PREGUNTAS);

        assertThat(script)
                .contains(EscapadorJs.aAscii(MarcaDocumento.PIE_PAGINA))
                .contains("script.google.com");
    }

    @Test
    void deberiaRechazarUnaPiezaQueNoSeaEvaluacion() {
        MaterialExportableDTO teoria = new MaterialExportableDTO("Matemáticas", "Temario",
                "universitario", PiezaMaterial.TEORIA, "Texto", null, null);

        assertThatThrownBy(() -> exportador.exportar(teoria))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaRechazarUnaEvaluacionVacia() {
        assertThatThrownBy(() -> exportador.exportar(evaluacion(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
