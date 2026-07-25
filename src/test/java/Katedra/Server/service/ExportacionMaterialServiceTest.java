package Katedra.Server.service;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.ExportacionArchivoDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.Asignatura;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.Temario;
import Katedra.Server.repository.ContenidoTemarioRepository;
import Katedra.Server.service.export.ExportadorMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportacionMaterialServiceTest {

    private static final String TEMARIO_ID = "temario-1";
    private static final String EMAIL = "profesor@katedra.com";
    private static final byte[] BYTES = "contenido".getBytes(StandardCharsets.UTF_8);

    @Mock
    private TemarioAccessGuard guard;

    @Mock
    private ContenidoTemarioRepository contenidoTemarioRepository;

    @Mock
    private ExportadorMaterial exportadorPdf;

    private ExportacionMaterialService service;
    private ContenidoTemario contenido;

    @BeforeEach
    void setUp() throws IOException {
        given(exportadorPdf.formato()).willReturn(FormatoExportacion.PDF);
        given(exportadorPdf.exportar(any())).willReturn(BYTES);
        service = new ExportacionMaterialService(guard, contenidoTemarioRepository, List.of(exportadorPdf));

        Asignatura asignatura = new Asignatura(null, "Matemáticas", null);

        Temario temario = new Temario();
        temario.setTitulo("Matemáticas para la Ingeniería");
        temario.setGradoAcademico("universitario");
        temario.setAsignatura(asignatura);

        contenido = new ContenidoTemario();
        contenido.setTeoria("## Teoría\n\nContenido.");
        contenido.setEvaluacion(List.of(new EvaluacionPreguntaDTO("¿Pregunta?", List.of("a", "b"), 0, "porque")));
        contenido.setDiapositivas(List.of(new DiapositivaDTO("Portada", List.of("punto"))));

        given(guard.findOwnedTemario(TEMARIO_ID, EMAIL)).willReturn(temario);
        given(contenidoTemarioRepository.findByTemarioId(TEMARIO_ID)).willReturn(Optional.of(contenido));
    }

    private ExportacionArchivoDTO exportarPdfDeTeoria() {
        return service.exportar(TEMARIO_ID, EMAIL, "teoria", "pdf");
    }

    @Test
    void deberiaDevolverElArchivoConSuNombreYTipoDeContenido() {
        ExportacionArchivoDTO archivo = exportarPdfDeTeoria();

        assertThat(archivo.nombreArchivo()).isEqualTo("matematicas-matematicas-para-la-ingenieria-teoria.pdf");
        assertThat(archivo.mediaType()).isEqualTo("application/pdf");
        assertThat(archivo.contenido()).isEqualTo(BYTES);
    }

    @Test
    void deberiaEntregarAlExportadorUnaInstantaneaDelMaterial() throws IOException {
        exportarPdfDeTeoria();

        var captor = org.mockito.ArgumentCaptor.forClass(MaterialExportableDTO.class);
        verify(exportadorPdf).exportar(captor.capture());

        MaterialExportableDTO material = captor.getValue();
        assertThat(material.materia()).isEqualTo("Matemáticas");
        assertThat(material.temarioTitulo()).isEqualTo("Matemáticas para la Ingeniería");
        assertThat(material.pieza()).isEqualTo(PiezaMaterial.TEORIA);
        assertThat(material.teoria()).contains("Contenido.");
    }

    @Test
    void deberiaPropagarElErrorCuandoElTemarioNoEsAccesible() {
        given(guard.findOwnedTemario(TEMARIO_ID, EMAIL))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a este temario"));

        assertThatThrownBy(this::exportarPdfDeTeoria)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deberiaResponder404CuandoNoHayContenidoGenerado() {
        given(contenidoTemarioRepository.findByTemarioId(TEMARIO_ID)).willReturn(Optional.empty());

        assertThatThrownBy(this::exportarPdfDeTeoria)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deberiaResponder404CuandoLaPiezaEstaVacia() {
        contenido.setTeoria("   ");

        assertThatThrownBy(this::exportarPdfDeTeoria)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deberiaResponder404CuandoLaListaDeDiapositivasEstaVacia() {
        contenido.setDiapositivas(List.of());

        assertThatThrownBy(() -> service.exportar(TEMARIO_ID, EMAIL, "diapositivas", "pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deberiaResponder400CuandoLaPiezaEsDesconocida() {
        assertThatThrownBy(() -> service.exportar(TEMARIO_ID, EMAIL, "resumen", "pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deberiaResponder400CuandoElFormatoEsDesconocido() {
        assertThatThrownBy(() -> service.exportar(TEMARIO_ID, EMAIL, "teoria", "xls"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deberiaResponder400CuandoElFormatoNoAplicaALaPieza() {
        assertThatThrownBy(() -> service.exportar(TEMARIO_ID, EMAIL, "teoria", "gs"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deberiaResponder500CuandoElExportadorFalla() throws IOException {
        willThrow(new IOException("disco lleno")).given(exportadorPdf).exportar(any());

        assertThatThrownBy(this::exportarPdfDeTeoria)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
