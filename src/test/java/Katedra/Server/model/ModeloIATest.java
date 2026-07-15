package Katedra.Server.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloIATest {

    @Test
    void shouldMapGpt4oMiniToBasicTier() {
        assertThat(ModeloIA.fromValor("gpt-4o-mini").getValor()).isEqualTo("basico");
    }

    @Test
    void shouldMapGpt4oToAdvancedTier() {
        assertThat(ModeloIA.fromValor("gpt-4o").getValor()).isEqualTo("avanzado");
    }

    @Test
    void shouldInterpretCurrentFrontendKeysAsBasicAndAdvanced() {
        assertThat(ModeloIA.fromValor("flash")).isEqualTo(ModeloIA.BASICO);
        assertThat(ModeloIA.fromValor("pro")).isEqualTo(ModeloIA.AVANZADO);
    }
}
