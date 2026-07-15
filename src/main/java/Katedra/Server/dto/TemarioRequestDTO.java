package Katedra.Server.dto;

import jakarta.validation.constraints.NotBlank;

public record TemarioRequestDTO(
    @NotBlank(message = "El título es obligatorio")
    String titulo,
    String descripcion,
    @NotBlank(message = "El grado académico es obligatorio")
    String gradoAcademico,
    String asignatura
) {}
