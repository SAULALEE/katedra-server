package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kinds of temario lifecycle events tracked in the read-only content history
 * ({@code HistorialEvento}). JSON values are uppercase Spanish to match the frontend contract.
 */
public enum TipoEventoHistorial {
    CREADO("CREADO"),
    EDITADO("EDITADO"),
    GENERADO("GENERADO"),
    FAVORITO_AGREGADO("FAVORITO_AGREGADO"),
    FAVORITO_QUITADO("FAVORITO_QUITADO"),
    ELIMINADO("ELIMINADO");

    private final String valor;

    TipoEventoHistorial(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static TipoEventoHistorial fromValor(String valor) {
        for (TipoEventoHistorial tipo : values()) {
            if (tipo.valor.equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de evento de historial desconocido: " + valor);
    }
}
