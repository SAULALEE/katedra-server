package Katedra.Server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDTO(
        @NotBlank(message = "El token es obligatorio")
        @Size(max = 512)
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetRequestDTO[token=[REDACTED], newPassword=[REDACTED]]";
    }
}
