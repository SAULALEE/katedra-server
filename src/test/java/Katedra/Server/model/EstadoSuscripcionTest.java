package Katedra.Server.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoSuscripcionTest {

    @ParameterizedTest
    @ValueSource(strings = {"active", "trialing"})
    @DisplayName("Stripe's paying and trialing states grant access")
    void estadosQueOtorganAcceso(String status) {
        EstadoSuscripcion estado = EstadoSuscripcion.fromStripe(status);

        assertThat(estado).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(estado.esVigente()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"past_due", "unpaid"})
    @DisplayName("A failed renewal revokes access without ending the subscription")
    void renovacionFallidaRevocaAcceso(String status) {
        EstadoSuscripcion estado = EstadoSuscripcion.fromStripe(status);

        assertThat(estado).isEqualTo(EstadoSuscripcion.IMPAGA);
        assertThat(estado.esVigente()).isFalse();
    }

    @Test
    @DisplayName("canceled maps to CANCELADA and grants nothing")
    void canceladaNoOtorgaAcceso() {
        EstadoSuscripcion estado = EstadoSuscripcion.fromStripe("canceled");

        assertThat(estado).isEqualTo(EstadoSuscripcion.CANCELADA);
        assertThat(estado.esVigente()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"incomplete", "incomplete_expired", "paused"})
    @DisplayName("A subscription whose first payment never completed grants nothing")
    void incompletaNoOtorgaAcceso(String status) {
        assertThat(EstadoSuscripcion.fromStripe(status).esVigente()).isFalse();
    }

    @Test
    @DisplayName("An unknown or null Stripe status fails closed, never granting access")
    void estadoDesconocidoFallaCerrado() {
        // If Stripe ever adds a status we do not know about, withholding access is the
        // safe failure. Granting it would be a paid feature given away by a typo.
        assertThat(EstadoSuscripcion.fromStripe("un_estado_futuro").esVigente()).isFalse();
        assertThat(EstadoSuscripcion.fromStripe(null).esVigente()).isFalse();
    }

    @Test
    @DisplayName("ACTIVA is the only state that grants PRO")
    void soloActivaEsVigente() {
        for (EstadoSuscripcion estado : EstadoSuscripcion.values()) {
            assertThat(estado.esVigente()).isEqualTo(estado == EstadoSuscripcion.ACTIVA);
        }
    }
}
