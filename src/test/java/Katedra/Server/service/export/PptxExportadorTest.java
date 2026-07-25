package Katedra.Server.service.export;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PptxExportadorTest {

    private static final String MATERIA = "Matemáticas";
    private static final String TEMARIO = "Matemáticas para la Ingeniería";

    private final PptxExportador exportador = new PptxExportador();

    private static final List<DiapositivaDTO> DIAPOSITIVAS = List.of(
            new DiapositivaDTO("Fundamentos para la ingeniería", List.of("Del álgebra a los métodos numéricos")),
            new DiapositivaDTO("Espacios vectoriales", List.of("Cerrados bajo suma", "Base y dimensión")),
            new DiapositivaDTO("Transformaciones lineales", List.of("Preservan la estructura", "Se representan con matrices")));

    private MaterialExportableDTO material(List<DiapositivaDTO> diapositivas) {
        return new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.DIAPOSITIVAS, null, null, diapositivas);
    }

    private String textoDe(XSLFSlide slide) {
        StringBuilder sb = new StringBuilder();
        for (XSLFShape forma : slide.getShapes()) {
            if (forma instanceof XSLFTextShape texto) {
                sb.append(texto.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void deberiaDeclararSuFormato() {
        assertThat(exportador.formato()).isEqualTo(FormatoExportacion.PPTX);
    }

    @Test
    void deberiaUsarProporcion16x9() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            assertThat(ppt.getPageSize()).isEqualTo(new Dimension(960, 540));
        }
    }

    @Test
    void deberiaGenerarUnaLaminaPorDiapositiva() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            assertThat(ppt.getSlides()).hasSize(DIAPOSITIVAS.size());
        }
    }

    @Test
    void deberiaMostrarElPieDePaginaEnTodasLasLaminas() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            assertThat(ppt.getSlides())
                    .allSatisfy(slide -> assertThat(textoDe(slide)).contains(MarcaDocumento.PIE_PAGINA));
        }
    }

    @Test
    void deberiaMostrarMateriaYTemarioEnLaPortada() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            String portada = textoDe(ppt.getSlides().get(0));

            assertThat(portada)
                    .contains(MarcaDocumento.lineaMateria(MATERIA))
                    .contains(TEMARIO);
        }
    }

    @Test
    void deberiaIncluirTituloYPuntosDeCadaLamina() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            String segunda = textoDe(ppt.getSlides().get(1));

            assertThat(segunda)
                    .contains("Espacios vectoriales")
                    .contains("Cerrados bajo suma")
                    .contains("Base y dimensión");
        }
    }

    @Test
    void deberiaRegistrarLaAutoriaDeKatedra() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(exportador.exportar(material(DIAPOSITIVAS))))) {
            assertThat(ppt.getProperties().getCoreProperties().getCreator()).isEqualTo(MarcaDocumento.AUTOR);
        }
    }

    @Test
    void deberiaRechazarUnaPiezaQueNoSeanDiapositivas() {
        MaterialExportableDTO teoria = new MaterialExportableDTO(MATERIA, TEMARIO, "universitario",
                PiezaMaterial.TEORIA, "Texto", null, null);

        assertThatThrownBy(() -> exportador.exportar(teoria))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaRechazarUnaListaVaciaDeDiapositivas() {
        assertThatThrownBy(() -> exportador.exportar(material(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
