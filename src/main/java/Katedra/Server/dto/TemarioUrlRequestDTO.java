package Katedra.Server.dto;

import Katedra.Server.model.ModeloGeneracion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TemarioUrlRequestDTO(
    String url,
    String titulo,
    @NotBlank(message = "La asignatura es obligatoria")
    String asignaturaId,
    @NotBlank(message = "El grado académico es obligatorio")
    @Pattern(regexp = "^[^,;]+$", message = "Solo se permite un grado académico")
    String gradoAcademico,
    ModeloGeneracion modeloGeneracion
) {
    public TemarioUrlRequestDTO(
            String url,
            String titulo,
            String asignaturaId,
            String gradoAcademico) {
        this(url, titulo, asignaturaId, gradoAcademico, null);
    }
}
