package Katedra.Server.service.export;

import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxExportadorTest {

    private static final String MATERIA = "Matemáticas";
    private static final String TEMARIO = "Matemáticas para la Ingeniería";

    private final DocxExportador exportador = new DocxExportador();

    private MaterialExportableDTO teoria(String markdown) {
        return new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.TEORIA, markdown, null, null);
    }

    private XWPFDocument abrir(byte[] bytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private String textoCompleto(XWPFDocument doc) {
        return doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void deberiaDeclararSuFormato() {
        assertThat(exportador.formato()).isEqualTo(FormatoExportacion.DOCX);
    }

    @Test
    void deberiaRepetirElPieDePaginaEnTodoElDocumento() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("Contenido.")))) {
            assertThat(doc.getHeaderFooterPolicy().getDefaultFooter().getText())
                    .contains(MarcaDocumento.PIE_PAGINA);
        }
    }

    @Test
    void deberiaMostrarMateriaYTemarioEnElEncabezado() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("Contenido.")))) {
            String encabezado = doc.getHeaderFooterPolicy().getDefaultHeader().getText();

            assertThat(encabezado)
                    .contains(MarcaDocumento.lineaMateria(MATERIA))
                    .contains(TEMARIO);
        }
    }

    @Test
    void deberiaQuedarProtegidoContraEdicion() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("Contenido.")))) {
            assertThat(doc.isEnforcedReadonlyProtection()).isTrue();
        }
    }

    @Test
    void deberiaAplicarNegritaEnLosTitulos() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("## Fundamentos\n\nTexto normal.")))) {
            XWPFParagraph titulo = doc.getParagraphs().stream()
                    .filter(p -> "Fundamentos".equals(p.getText()))
                    .findFirst()
                    .orElseThrow();

            assertThat(titulo.getRuns()).isNotEmpty();
            assertThat(titulo.getRuns().get(0).isBold()).isTrue();
        }
    }

    @Test
    void deberiaConvertirNegritaMarkdownEnUnRunEnNegrita() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("Es un **vector** libre.")))) {
            List<XWPFRun> runs = doc.getParagraphs().stream()
                    .filter(p -> p.getText().contains("vector"))
                    .findFirst()
                    .orElseThrow()
                    .getRuns();

            assertThat(runs)
                    .anySatisfy(run -> {
                        assertThat(run.text()).contains("vector");
                        assertThat(run.isBold()).isTrue();
                    });
        }
    }

    @Test
    void deberiaConservarLasVinetasDeLasListas() throws IOException {
        try (XWPFDocument doc = abrir(exportador.exportar(teoria("- Vectores\n- Matrices")))) {
            assertThat(textoCompleto(doc)).contains("Vectores").contains("Matrices");
        }
    }

    @Test
    void deberiaListarPreguntasOpcionesYClaveDeRespuestas() throws IOException {
        MaterialExportableDTO material = new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.EVALUACION, null,
                List.of(new EvaluacionPreguntaDTO("¿Qué es un vector?",
                        List.of("Un escalar", "Un elemento de un espacio vectorial"), 1,
                        "Pertenece a un espacio vectorial.")),
                null);

        try (XWPFDocument doc = abrir(exportador.exportar(material))) {
            String texto = textoCompleto(doc);

            assertThat(texto)
                    .contains("Pregunta 1")
                    .contains("¿Qué es un vector?")
                    .contains("A) Un escalar")
                    .contains("B) Un elemento de un espacio vectorial")
                    .contains("Clave de respuestas")
                    .contains("Pertenece a un espacio vectorial.");
        }
    }

    @Test
    void deberiaRechazarLasDiapositivas() {
        MaterialExportableDTO material = new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.DIAPOSITIVAS, null, null, List.of());

        assertThatThrownBy(() -> exportador.exportar(material))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
