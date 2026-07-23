package Katedra.Server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AsignaturaRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,
        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
        String descripcion
) {
}
