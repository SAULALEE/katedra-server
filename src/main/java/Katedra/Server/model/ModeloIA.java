package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Allowlist of OpenAI models the teacher can pick for generation.
 * Deserialization of any other value fails, rejecting arbitrary model ids.
 */
public enum ModeloIA {
    SENCILLO("gpt-4o-mini", 1, 8, 5),
    AVANZADO("gpt-4o", 9, 15, 10);

    private final String modelId;
    private final int minDiapositivas;
    private final int maxDiapositivas;
    private final int defaultDiapositivas;

    ModeloIA(String modelId, int minDiapositivas, int maxDiapositivas, int defaultDiapositivas) {
        this.modelId = modelId;
        this.minDiapositivas = minDiapositivas;
        this.maxDiapositivas = maxDiapositivas;
        this.defaultDiapositivas = defaultDiapositivas;
    }

    @JsonValue
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

    @JsonCreator
    public static ModeloIA fromModelId(String value) {
        for (ModeloIA modelo : values()) {
            if (modelo.modelId.equalsIgnoreCase(value)) {
                return modelo;
            }
        }
        throw new IllegalArgumentException("Modelo no permitido: " + value);
    }
}
