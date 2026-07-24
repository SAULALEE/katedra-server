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

    /** The ingestion forms speak TUTOR/CATEDRATICO through ModeloGeneracion (distinct from content generation flow), never through this enum directly. */
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
        assertThat(ModeloGeneracion.TUTOR.toModeloIA()).isEqualTo(ModeloIA.FLASH);
        assertThat(ModeloGeneracion.CATEDRATICO.toModeloIA()).isEqualTo(ModeloIA.PRO);
    }

    /** Tutor only offers a compact (4) or standard (6) outline; Catedrático only a detailed (8) or exhaustive (10) one. */
    @Test
    void shouldRestrictModuloCountToTwoDiscreteOptionsPerTier() {
        assertThat(ModeloIA.FLASH.getMinModulos()).isEqualTo(4);
        assertThat(ModeloIA.FLASH.getMaxModulos()).isEqualTo(6);
        assertThat(ModeloIA.FLASH.getDefaultModulos()).isEqualTo(4);

        assertThat(ModeloIA.PRO.getMinModulos()).isEqualTo(8);
        assertThat(ModeloIA.PRO.getMaxModulos()).isEqualTo(10);
        assertThat(ModeloIA.PRO.getDefaultModulos()).isEqualTo(8);
    }
}
