package Katedra.Server.dto;

import java.time.LocalDateTime;

public record TemarioResponseDTO(
    String id,
    String titulo,
    String descripcion,
    String gradoAcademico,
    String asignaturaId,
    String asignatura,
    boolean favorito,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int progreso
) {}
