package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Allowlist of Katedra's branded generation tiers. The JSON contract exposes only the
 * tier key (never a raw OpenAI model id), so the provider can change without breaking
 * clients. Each tier controls the underlying model, slide-count range, theory-paragraph
 * target, and the OpenAI call options used to avoid wasting tokens on cheaper tiers.
 */
public enum ModeloIA {
    FLASH(
            "flash", "Tutor", "gpt-4.1-mini",
            1, 8, 5,
            4, 5,
            0.5, 1500, false, null),
    PRO(
            "pro", "Maestro", "gpt-5.4",
            6, 12, 9,
            6, 8,
            0.6, 2500, false, null),
    MAX(
            "max", "Catedrático", "o4-mini",
            9, 15, 12,
            8, 10,
            null, 4000, true, "medium");

    private final String valor;
    private final String displayName;
    private final String modelId;
    private final int minDiapositivas;
    private final int maxDiapositivas;
    private final int defaultDiapositivas;
    private final int minParrafosTeoria;
    private final int maxParrafosTeoria;
    private final Double temperature;
    private final int maxTokens;
    private final boolean reasoning;
    private final String reasoningEffort;

    ModeloIA(String valor, String displayName, String modelId,
             int minDiapositivas, int maxDiapositivas, int defaultDiapositivas,
             int minParrafosTeoria, int maxParrafosTeoria,
             Double temperature, int maxTokens, boolean reasoning, String reasoningEffort) {
        this.valor = valor;
        this.displayName = displayName;
        this.modelId = modelId;
        this.minDiapositivas = minDiapositivas;
        this.maxDiapositivas = maxDiapositivas;
        this.defaultDiapositivas = defaultDiapositivas;
        this.minParrafosTeoria = minParrafosTeoria;
        this.maxParrafosTeoria = maxParrafosTeoria;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.reasoning = reasoning;
        this.reasoningEffort = reasoningEffort;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMinDiapositivas() {
        return minDiapositivas;
    }

    public int getMaxDiapositivas() {
        return maxDiapositivas;
    }

    public int getDefaultDiapositivas() {
        return defaultDiapositivas;
    }

    public int getMinParrafosTeoria() {
        return minParrafosTeoria;
    }

    public int getMaxParrafosTeoria() {
        return maxParrafosTeoria;
    }

    /** Null for reasoning tiers: temperature is not supported by o-series models. */
    public Double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    /** Null unless {@link #isReasoning()} is true. */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    @JsonCreator
    public static ModeloIA fromValor(String valor) {
        for (ModeloIA modelo : values()) {
            if (modelo.valor.equalsIgnoreCase(valor)) {
                return modelo;
            }
        }
        throw new IllegalArgumentException("Modelo no permitido: " + valor);
    }
}
