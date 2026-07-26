package Katedra.Server.service.export;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfExportadorTest {

    private static final String MATERIA = "Matemáticas";
    private static final String TEMARIO = "Matemáticas para la Ingeniería";

    private final PdfExportador exportador = new PdfExportador(new PdfFuentes());

    private MaterialExportableDTO teoria(String markdown) {
        return new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.TEORIA, markdown, null, null);
    }

    /** Long enough to spill over several pages, so per-page branding is really exercised. */
    private String teoriaLarga() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            sb.append("## Sección ").append(i).append("\n\n");
            sb.append("El álgebra lineal describe espacios vectoriales y transformaciones. ".repeat(14));
            sb.append("\n\n- Primer punto de la sección\n- Segundo punto de la sección\n\n");
        }
        return sb.toString();
    }

    private String textoDePagina(PDDocument doc, int pagina) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        return stripper.getText(doc);
    }

    private List<String> textoPorPagina(byte[] bytes) throws IOException {
        List<String> paginas = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                paginas.add(textoDePagina(doc, i));
            }
        }
        return paginas;
    }

    @Test
    void deberiaDeclararSuFormato() {
        assertThat(exportador.formato()).isEqualTo(FormatoExportacion.PDF);
    }

    @Test
    void deberiaEscribirElPieDePaginaEnTodasLasPaginas() throws IOException {
        List<String> paginas = textoPorPagina(exportador.exportar(teoria(teoriaLarga())));

        assertThat(paginas).hasSizeGreaterThanOrEqualTo(3);
        assertThat(paginas).allSatisfy(texto -> assertThat(texto).contains(MarcaDocumento.PIE_PAGINA));
    }

    @Test
    void deberiaEscribirMateriaYTemarioEnElEncabezadoDeCadaPagina() throws IOException {
        List<String> paginas = textoPorPagina(exportador.exportar(teoria(teoriaLarga())));

        assertThat(paginas).allSatisfy(texto -> assertThat(texto)
                .contains(MarcaDocumento.lineaMateria(MATERIA))
                .contains(TEMARIO));
    }

    @Test
    void deberiaEstarProtegidoContraModificacion() throws IOException {
        byte[] bytes = exportador.exportar(teoria("Contenido breve."));

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertThat(doc.isEncrypted()).isTrue();
            AccessPermission permisos = doc.getCurrentAccessPermission();
            assertThat(permisos.canModify()).isFalse();
            assertThat(permisos.canModifyAnnotations()).isFalse();
            assertThat(permisos.canPrint()).isTrue();
            assertThat(permisos.canExtractContent()).isTrue();
            assertThat(permisos.canExtractForAccessibility()).isTrue();
        }
    }

    @Test
    void deberiaConservarAcentosYSimbolosDelContenido() throws IOException {
        byte[] bytes = exportador.exportar(teoria("La función ƒ converge: á é í ó ú ñ ¿cómo? α ≤ β → ∞"));

        String texto = String.join("\n", textoPorPagina(bytes));
        assertThat(texto).contains("á é í ó ú ñ").contains("¿cómo?");
    }

    /**
     * Regression test: PDFBox auto-applies GSUB ligature substitution for Inter, and the
     * substituted glyph's ToUnicode entry came out wrong — "Definición" extracted back as
     * "Deznición". Every word with "fi"/"fl" was silently corrupted on copy/search until fixed.
     */
    @Test
    void noDeberiaCorromperPalabrasConLigadurasComoFiOFl() throws IOException {
        byte[] bytes = exportador.exportar(teoria(
                "La definición oficial del flujo eficiente requiere disciplina científica."));

        String texto = String.join("\n", textoPorPagina(bytes));
        assertThat(texto)
                .contains("definición")
                .contains("oficial")
                .contains("flujo")
                .contains("eficiente")
                .contains("científica")
                .doesNotContain("dezinición", "ozicial", "zujo", "ezicente", "cientízica");
    }

    @Test
    void deberiaRenderizarLosTitulosDelMarkdown() throws IOException {
        byte[] bytes = exportador.exportar(teoria("# Introducción\n\n## Fundamentos\n\nTexto **en negrita**."));

        String texto = String.join("\n", textoPorPagina(bytes));
        assertThat(texto).contains("Introducción").contains("Fundamentos").contains("en negrita");
    }

    @Test
    void deberiaIncluirPreguntasOpcionesYClaveDeRespuestas() throws IOException {
        MaterialExportableDTO material = new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.EVALUACION, null,
                List.of(new EvaluacionPreguntaDTO("¿Qué es un vector?",
                                List.of("Un escalar", "Un elemento de un espacio vectorial"), 1,
                                "Pertenece a un espacio vectorial."),
                        new EvaluacionPreguntaDTO("¿Qué es una matriz?",
                                List.of("Una tabla de escalares", "Un punto"), 0,
                                "Representa una transformación lineal.")),
                null);

        String texto = String.join("\n", textoPorPagina(exportador.exportar(material)));

        assertThat(texto)
                .contains("Pregunta 1")
                .contains("¿Qué es un vector?")
                .contains("A) Un escalar")
                .contains("B) Un elemento de un espacio vectorial")
                .contains("Clave de respuestas")
                .contains("Pertenece a un espacio vectorial.");
    }

    @Test
    void deberiaGenerarUnaPaginaApaisadaPorDiapositiva() throws IOException {
        List<DiapositivaDTO> diapositivas = List.of(
                new DiapositivaDTO("Portada", List.of("Contexto")),
                new DiapositivaDTO("Vectores", List.of("Definición", "Operaciones")),
                new DiapositivaDTO("Matrices", List.of("Definición", "Producto", "Inversa")));
        MaterialExportableDTO material = new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.DIAPOSITIVAS, null, null, diapositivas);

        byte[] bytes = exportador.exportar(material);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(diapositivas.size());
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(960f);
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(540f);
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                assertThat(textoDePagina(doc, i)).contains(MarcaDocumento.PIE_PAGINA);
            }
            assertThat(textoDePagina(doc, 2)).contains("Vectores").contains("Definición");
        }
    }

    @Test
    void deberiaRechazarUnaPiezaSinContenido() {
        assertThatThrownBy(() -> exportador.exportar(teoria("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
