package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Billing period the user picked at checkout. Each value resolves to a different Stripe
 * Price id, configured in Doppler — the amounts live in the Stripe dashboard, never here,
 * so a promotion does not require a deploy.
 */
public enum CicloFacturacion {
    MENSUAL("mensual"),
    ANUAL("anual");

    private final String valor;

    CicloFacturacion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static CicloFacturacion fromValor(String valor) {
        for (CicloFacturacion ciclo : values()) {
            if (ciclo.valor.equalsIgnoreCase(valor) || ciclo.name().equalsIgnoreCase(valor)) {
                return ciclo;
            }
        }
        throw new IllegalArgumentException("Ciclo de facturación no permitido: " + valor);
    }
}
