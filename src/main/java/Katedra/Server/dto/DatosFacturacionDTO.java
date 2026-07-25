package Katedra.Server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Billing identity collected in step one of the checkout, before any card data.
 *
 * <p>Sent to Stripe as the Customer's name/email/address. The email is asked for rather
 * than taken from the session because the person paying is not always the account holder
 * (a school administrator paying for a teacher).
 */
public record DatosFacturacionDTO(
        @NotBlank(message = "El nombre completo es obligatorio")
        String nombreCompleto,

        @NotBlank(message = "El correo de facturación es obligatorio")
        @Email(message = "El correo de facturación no es válido")
        String email,

        @NotBlank(message = "El país es obligatorio")
        String pais,

        String ciudad,

        String direccion,

        String codigoPostal) {
}
