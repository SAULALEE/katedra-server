package Katedra.Server.dto;

import Katedra.Server.model.NivelAcademico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemarioRequestDTO(
    @NotBlank(message = "El título es obligatorio")
    String titulo,
    String descripcion,
    @NotNull(message = "El grado académico es obligatorio")
    NivelAcademico gradoAcademico,
    String asignatura
) {}
