package Katedra.Server.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloIATest {

    @Test
    void shouldExposeOnlyTheTwoBrandedTiers() {
        assertThat(ModeloIA.values()).containsExactly(ModeloIA.FLASH, ModeloIA.PRO);
    }

    @Test
    void shouldResolveTiersByTheirPublicKey() {
        assertThat(ModeloIA.fromValor("flash")).isEqualTo(ModeloIA.FLASH);
        assertThat(ModeloIA.fromValor("pro")).isEqualTo(ModeloIA.PRO);
    }

    /** Accepting the raw model id keeps rows persisted before the tier rename readable. */
    @Test
    void shouldResolveTiersByTheirUnderlyingModelId() {
        assertThat(ModeloIA.fromValor("gpt-4.1-mini")).isEqualTo(ModeloIA.FLASH);
        assertThat(ModeloIA.fromValor("o4-mini")).isEqualTo(ModeloIA.PRO);
    }

    /** The ingestion forms speak BASICO/AVANZADO through ModeloGeneracion, never through this enum. */
    @Test
    void shouldRejectRetiredTierKeys() {
        assertThatThrownBy(() -> ModeloIA.fromValor("basico"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloIA.fromValor("avanzado"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloIA.fromValor("max"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMapIngestionModelsOntoTheBrandedTiers() {
        assertThat(ModeloGeneracion.BASICO.toModeloIA()).isEqualTo(ModeloIA.FLASH);
        assertThat(ModeloGeneracion.AVANZADO.toModeloIA()).isEqualTo(ModeloIA.PRO);
    }
}
