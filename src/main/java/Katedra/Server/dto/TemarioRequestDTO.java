package Katedra.Server.dto;

import Katedra.Server.model.ModeloGeneracion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TemarioRequestDTO(
    @NotBlank(message = "El título es obligatorio")
    String titulo,
    String descripcion,
    @NotBlank(message = "El grado académico es obligatorio")
    @Pattern(regexp = "^[^,;]+$", message = "Solo se permite un grado académico")
    String gradoAcademico,
    @NotBlank(message = "La asignatura es obligatoria")
    String asignaturaId,
    ModeloGeneracion modeloGeneracion
) {
    public TemarioRequestDTO(
            String titulo,
            String descripcion,
            String gradoAcademico,
            String asignaturaId) {
        this(titulo, descripcion, gradoAcademico, asignaturaId, null);
    }
}
