package Katedra.Server.service.export;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownExportadorTest {

    private final MarkdownExportador exportador = new MarkdownExportador();

    private MaterialExportableDTO teoria(String markdown) {
        return new MaterialExportableDTO("Matemáticas", "Matemáticas para la Ingeniería",
                "universitario", PiezaMaterial.TEORIA, markdown, null, null);
    }

    private MaterialExportableDTO evaluacion(List<EvaluacionPreguntaDTO> preguntas) {
        return new MaterialExportableDTO("Matemáticas", "Matemáticas para la Ingeniería",
                "universitario", PiezaMaterial.EVALUACION, null, preguntas, null);
    }

    private String exportar(MaterialExportableDTO material) throws Exception {
        return new String(exportador.exportar(material), StandardCharsets.UTF_8);
    }

    @Test
    void deberiaDeclararSuFormato() {
        assertThat(exportador.formato()).isEqualTo(FormatoExportacion.MARKDOWN);
    }

    @Test
    void deberiaAbrirConLaMateriaYElTemarioComoEncabezado() throws Exception {
        String salida = exportar(teoria("## Introducción\n\nContenido."));

        assertThat(salida).startsWith("# Matemáticas\n## Matemáticas para la Ingeniería\n");
    }

    @Test
    void deberiaCerrarConLaLineaDeCopyright() throws Exception {
        String salida = exportar(teoria("Contenido."));

        assertThat(salida.stripTrailing()).endsWith(MarcaDocumento.PIE_PAGINA);
    }

    @Test
    void deberiaConservarLaTeoriaTalCual() throws Exception {
        String cuerpo = "## Fundamentos\n\nEl **álgebra lineal** es la base.\n\n- Vectores\n- Matrices";

        assertThat(exportar(teoria(cuerpo))).contains(cuerpo);
    }

    @Test
    void deberiaCodificarEnUtf8SinBom() throws Exception {
        byte[] bytes = exportador.exportar(teoria("Función á é í ó ú ñ"));

        assertThat(bytes[0]).isNotEqualTo((byte) 0xEF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Función á é í ó ú ñ");
    }

    @Test
    void deberiaFormatearCadaPreguntaConSusOpcionesYRespuestaCorrecta() throws Exception {
        String salida = exportar(evaluacion(List.of(
                new EvaluacionPreguntaDTO("¿Qué es un vector?",
                        List.of("Un escalar", "Un elemento de un espacio vectorial", "Una matriz"),
                        1, "Un vector pertenece a un espacio vectorial."))));

        assertThat(salida)
                .contains("### Pregunta 1")
                .contains("¿Qué es un vector?")
                .contains("A) Un escalar")
                .contains("B) Un elemento de un espacio vectorial")
                .contains("C) Una matriz")
                .contains("*Respuesta Correcta: B*")
                .contains("Explicación: Un vector pertenece a un espacio vectorial.");
    }

    @Test
    void deberiaNumerarLasPreguntasDeFormaCorrelativa() throws Exception {
        String salida = exportar(evaluacion(List.of(
                new EvaluacionPreguntaDTO("Primera", List.of("a", "b"), 0, "e1"),
                new EvaluacionPreguntaDTO("Segunda", List.of("a", "b"), 1, "e2"))));

        assertThat(salida).contains("### Pregunta 1").contains("### Pregunta 2");
    }

    @Test
    void deberiaRechazarLasDiapositivas() {
        MaterialExportableDTO material = new MaterialExportableDTO("Matemáticas", "Temario",
                "universitario", PiezaMaterial.DIAPOSITIVAS, null, null,
                List.of(new DiapositivaDTO("Portada", List.of("punto"))));

        assertThatThrownBy(() -> exportador.exportar(material))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
