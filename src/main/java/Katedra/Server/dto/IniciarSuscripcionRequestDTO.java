package Katedra.Server.dto;

import Katedra.Server.model.CicloFacturacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Step one of the checkout: pick a billing period and supply billing identity. */
public record IniciarSuscripcionRequestDTO(
        @NotNull(message = "El ciclo de facturación es obligatorio")
        CicloFacturacion ciclo,

        @Valid
        @NotNull(message = "Los datos de facturación son obligatorios")
        DatosFacturacionDTO facturacion) {
}
