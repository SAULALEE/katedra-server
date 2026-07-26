package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The three kinds of educational material that can be generated for a syllabus.
 * JSON values are lowercase Spanish to match the frontend contract.
 */
public enum PiezaMaterial {
    ESTRUCTURA("estructura"),
    TEORIA("teoria"),
    EVALUACION("evaluacion"),
    DIAPOSITIVAS("diapositivas");

    private final String valor;

    PiezaMaterial(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static PiezaMaterial fromValor(String valor) {
        for (PiezaMaterial pieza : values()) {
            if (pieza.valor.equalsIgnoreCase(valor)) {
                return pieza;
            }
        }
        throw new IllegalArgumentException("Pieza de material desconocida: " + valor);
    }
}
