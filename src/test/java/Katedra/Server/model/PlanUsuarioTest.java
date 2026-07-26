package Katedra.Server.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the commercial terms of each tier.
 *
 * <p>These are business decisions, not implementation details: if someone changes a number
 * here they are changing what customers were sold, so it should take a deliberate test
 * edit rather than passing silently.
 */
class PlanUsuarioTest {

    @Test
    @DisplayName("FREE: 10 generations/day, 5 exports/day, no Pro model, slides, file or URL ingest")
    void limitesDelPlanFree() {
        PlanUsuario free = PlanUsuario.FREE;

        assertThat(free.getGeneracionesPorDia()).isEqualTo(10);
        assertThat(free.getExportacionesPorDia()).isEqualTo(5);
        assertThat(free.permiteModeloPro()).isFalse();
        assertThat(free.permiteDiapositivas()).isFalse();
        assertThat(free.permiteCargaArchivo()).isFalse();
        assertThat(free.permiteCargaUrl()).isFalse();
    }

    @Test
    @DisplayName("PRO: 100 generations/day, 100 exports/day, every capability unlocked")
    void limitesDelPlanPro() {
        PlanUsuario pro = PlanUsuario.PRO;

        assertThat(pro.getGeneracionesPorDia()).isEqualTo(100);
        assertThat(pro.getExportacionesPorDia()).isEqualTo(100);
        assertThat(pro.permiteModeloPro()).isTrue();
        assertThat(pro.permiteDiapositivas()).isTrue();
        assertThat(pro.permiteCargaArchivo()).isTrue();
        assertThat(pro.permiteCargaUrl()).isTrue();
    }

    @Test
    @DisplayName("PRO is strictly more permissive than FREE on every axis")
    void proNuncaEsMasRestrictivoQueFree() {
        PlanUsuario free = PlanUsuario.FREE;
        PlanUsuario pro = PlanUsuario.PRO;

        assertThat(pro.getGeneracionesPorDia()).isGreaterThan(free.getGeneracionesPorDia());
        assertThat(pro.getExportacionesPorDia()).isGreaterThan(free.getExportacionesPorDia());
    }

    @Test
    @DisplayName("fromValor accepts the JSON value and the enum name, case-insensitively")
    void fromValorAceptaAmbasFormas() {
        assertThat(PlanUsuario.fromValor("pro")).isEqualTo(PlanUsuario.PRO);
        assertThat(PlanUsuario.fromValor("PRO")).isEqualTo(PlanUsuario.PRO);
        assertThat(PlanUsuario.fromValor("free")).isEqualTo(PlanUsuario.FREE);
        assertThat(PlanUsuario.fromValor("FREE")).isEqualTo(PlanUsuario.FREE);
    }

    @Test
    @DisplayName("fromValor rejects an unknown plan instead of defaulting to one")
    void fromValorRechazaDesconocido() {
        assertThatThrownBy(() -> PlanUsuario.fromValor("enterprise"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan no permitido");
    }
}
