package Katedra.Server.service;

import Katedra.Server.dto.UsoPlanResponseDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.OrigenTemario;
import Katedra.Server.model.PiezaMaterial;
import Katedra.Server.model.PlanUsuario;
import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsoDiarioRepository;
import Katedra.Server.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsoDiarioRepository usoDiarioRepository;

    @InjectMocks
    private PlanLimitService planLimitService;

    private Usuario free;
    private Usuario pro;

    @BeforeEach
    void setUp() {
        free = new Usuario("gratis@katedra.test", "hash", "Docente Gratis", RolUsuario.ROLE_PROFESOR);
        free.setPlan(PlanUsuario.FREE);

        pro = new Usuario("pro@katedra.test", "hash", "Docente Pro", RolUsuario.ROLE_PROFESOR);
        pro.setPlan(PlanUsuario.PRO);
    }

    private ResponseStatusException capturar(Runnable accion) {
        return (ResponseStatusException) org.assertj.core.api.Assertions.catchThrowable(accion::run);
    }

    // --- capability gates: 403, upgrading is the only fix ---

    @Test
    @DisplayName("FREE cannot request the Catedrático model")
    void freeNoPuedeUsarModeloPro() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarGeneracion(free, ModeloIA.PRO, Set.of(PiezaMaterial.TEORIA)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Catedrático").contains("Pro");
    }

    @Test
    @DisplayName("FREE can still use the Tutor model")
    void freePuedeUsarModeloFlash() {
        assertThatCode(() -> planLimitService.validarGeneracion(free, ModeloIA.FLASH, Set.of(PiezaMaterial.TEORIA)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FREE cannot generate slides")
    void freeNoPuedeGenerarDiapositivas() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarGeneracion(free, ModeloIA.FLASH, Set.of(PiezaMaterial.DIAPOSITIVAS)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("diapositivas");
    }

    @Test
    @DisplayName("The slide gate triggers even when slides are only one piece among several")
    void freeNoPuedeGenerarDiapositivasNiMezcladas() {
        assertThatThrownBy(() -> planLimitService.validarGeneracion(
                free, ModeloIA.FLASH, Set.of(PiezaMaterial.TEORIA, PiezaMaterial.DIAPOSITIVAS)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("PRO passes every capability gate")
    void proPasaTodasLasCapacidades() {
        assertThatCode(() -> planLimitService.validarGeneracion(
                pro, ModeloIA.PRO, Set.of(PiezaMaterial.TEORIA, PiezaMaterial.DIAPOSITIVAS)))
                .doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarOrigenTemario(pro, OrigenTemario.ARCHIVO))
                .doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarOrigenTemario(pro, OrigenTemario.URL))
                .doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarExportacion(
                pro, PiezaMaterial.DIAPOSITIVAS, FormatoExportacion.PPTX))
                .doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarExportacion(
                pro, PiezaMaterial.TEORIA, FormatoExportacion.MARKDOWN))
                .doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarExportacion(
                pro, PiezaMaterial.EVALUACION, FormatoExportacion.APPS_SCRIPT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FREE may create a syllabus manually")
    void freePuedeCrearManual() {
        assertThatCode(() -> planLimitService.validarOrigenTemario(free, OrigenTemario.MANUAL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FREE cannot create a syllabus from an uploaded file")
    void freeNoPuedeCargarArchivo() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarOrigenTemario(free, OrigenTemario.ARCHIVO));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("archivo").contains("manualmente");
    }

    @Test
    @DisplayName("FREE cannot create a syllabus from a web URL")
    void freeNoPuedeCargarUrl() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarOrigenTemario(free, OrigenTemario.URL));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("enlace web");
    }

    @Test
    @DisplayName("FREE cannot export to PPTX")
    void freeNoPuedeExportarPptx() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarExportacion(free, PiezaMaterial.DIAPOSITIVAS, FormatoExportacion.PPTX));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("FREE can still export the formats its own material supports")
    void freePuedeExportarFormatosBasicos() {
        assertThatCode(() -> planLimitService.validarExportacion(
                free, PiezaMaterial.TEORIA, FormatoExportacion.PDF)).doesNotThrowAnyException();
        assertThatCode(() -> planLimitService.validarExportacion(
                free, PiezaMaterial.TEORIA, FormatoExportacion.DOCX)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FREE cannot export to Markdown")
    void freeNoPuedeExportarMarkdown() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarExportacion(free, PiezaMaterial.TEORIA, FormatoExportacion.MARKDOWN));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Markdown");
    }

    @Test
    @DisplayName("FREE cannot export to Google Forms (Apps Script)")
    void freeNoPuedeExportarAppsScript() {
        ResponseStatusException ex = capturar(
                () -> planLimitService.validarExportacion(free, PiezaMaterial.EVALUACION, FormatoExportacion.APPS_SCRIPT));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).contains("Google Forms");
    }

    @Test
    @DisplayName("A user with no plan is treated as FREE, not as unrestricted")
    void planNuloSeTrataComoFree() {
        Usuario sinPlan = new Usuario("viejo@katedra.test", "hash", "Legado", RolUsuario.ROLE_PROFESOR);
        sinPlan.setPlan(null);

        assertThatThrownBy(() -> planLimitService.validarGeneracion(sinPlan, ModeloIA.PRO, Set.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- quota gates: 429, waiting also fixes it ---

    @Test
    @DisplayName("A reservation within the daily allowance succeeds")
    void reservaDentroDelLimite() {
        when(usoDiarioRepository.consumirGeneraciones(any(), any(), eq(3), eq(10))).thenReturn(1);

        assertThatCode(() -> planLimitService.reservarGeneraciones(free, 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("0 affected rows becomes a 429 naming the plan and the usage")
    void cuotaAgotadaDevuelve429() {
        when(usoDiarioRepository.consumirGeneraciones(any(), any(), anyInt(), anyInt())).thenReturn(0);
        when(usoDiarioRepository.findByUsuarioIdAndFecha(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = capturar(() -> planLimitService.reservarGeneraciones(free, 2));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getReason()).contains("Gratis").contains("10");
    }

    @Test
    @DisplayName("A request larger than the whole daily allowance is rejected before touching SQL")
    void peticionMayorQueElLimiteSeRechazaAntesDeSql() {
        // The conditional UPDATE could never match, and a bare "limit reached" would not
        // explain that the request is impossible rather than merely early.
        ResponseStatusException ex = capturar(() -> planLimitService.reservarGeneraciones(free, 11));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getReason()).contains("11").contains("10");
        verify(usoDiarioRepository, never()).consumirGeneraciones(any(), any(), anyInt(), anyInt());
        verify(usoDiarioRepository, never()).crearFilaSiNoExiste(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PRO reserves against its own, larger allowance")
    void proReservaContraSuPropioLimite() {
        when(usoDiarioRepository.consumirGeneraciones(any(), any(), eq(50), eq(100))).thenReturn(1);

        assertThatCode(() -> planLimitService.reservarGeneraciones(pro, 50)).doesNotThrowAnyException();
        verify(usoDiarioRepository).consumirGeneraciones(any(), any(), eq(50), eq(100));
    }

    @Test
    @DisplayName("Today's row is created before the conditional consume runs")
    void aseguraLaFilaAntesDeConsumir() {
        when(usoDiarioRepository.consumirGeneraciones(any(), any(), anyInt(), anyInt())).thenReturn(1);

        planLimitService.reservarGeneraciones(free, 1);

        verify(usoDiarioRepository).crearFilaSiNoExiste(anyString(), any(), eq(LocalDate.now()));
    }

    @Test
    @DisplayName("Reserving nothing is a no-op, not an error")
    void reservarCeroNoHaceNada() {
        assertThatCode(() -> planLimitService.reservarGeneraciones(free, 0)).doesNotThrowAnyException();
        verify(usoDiarioRepository, never()).consumirGeneraciones(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("liberarGeneraciones refunds the given amount")
    void liberarDevuelveCuota() {
        planLimitService.liberarGeneraciones(free, 2);

        verify(usoDiarioRepository).liberarGeneraciones(any(), eq(LocalDate.now()), eq(2));
    }

    @Test
    @DisplayName("Refunding zero touches nothing")
    void liberarCeroNoHaceNada() {
        planLimitService.liberarGeneraciones(free, 0);

        verify(usoDiarioRepository, never()).liberarGeneraciones(any(), any(), anyInt());
    }

    @Test
    @DisplayName("An exhausted export allowance is a 429")
    void exportacionesAgotadasDevuelve429() {
        when(usoDiarioRepository.consumirExportaciones(any(), any(), eq(1), eq(5))).thenReturn(0);

        ResponseStatusException ex = capturar(() -> planLimitService.reservarExportacion(free));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getReason()).contains("exportaciones");
    }

    // --- usage snapshot ---

    @Test
    @DisplayName("consultarUso reports zeros and the tier's capabilities before any usage")
    void consultarUsoSinFilaDevuelveCeros() {
        when(usuarioRepository.findByEmail("gratis@katedra.test")).thenReturn(Optional.of(free));
        when(usoDiarioRepository.findByUsuarioIdAndFecha(any(), any())).thenReturn(Optional.empty());

        UsoPlanResponseDTO uso = planLimitService.consultarUso("gratis@katedra.test");

        assertThat(uso.plan()).isEqualTo(PlanUsuario.FREE);
        assertThat(uso.generacionesUsadas()).isZero();
        assertThat(uso.generacionesLimite()).isEqualTo(10);
        assertThat(uso.exportacionesLimite()).isEqualTo(5);
        assertThat(uso.permiteModeloPro()).isFalse();
        assertThat(uso.permiteDiapositivas()).isFalse();
        assertThat(uso.permiteCargaArchivo()).isFalse();
        assertThat(uso.permiteCargaUrl()).isFalse();
        assertThat(uso.permiteExportacionAvanzada()).isFalse();
    }

    @Test
    @DisplayName("consultarUso 404s for an unknown user")
    void consultarUsoUsuarioInexistente() {
        when(usuarioRepository.findByEmail("nadie@katedra.test")).thenReturn(Optional.empty());

        ResponseStatusException ex = capturar(() -> planLimitService.consultarUso("nadie@katedra.test"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
