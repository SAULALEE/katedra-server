package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Allowlist of OpenAI models the teacher can pick for generation.
 * Deserialization of any other value fails, rejecting arbitrary model ids.
 */
public enum ModeloIA {
    SENCILLO("gpt-4o-mini"),
    AVANZADO("gpt-4o");

    private final String modelId;

    ModeloIA(String modelId) {
        this.modelId = modelId;
    }

    @JsonValue
    public String getModelId() {
        return modelId;
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
