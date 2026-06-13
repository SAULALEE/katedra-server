package Katedra.Server.dto;

import java.time.LocalDateTime;

public record TemarioResponseDTO(
    String id,
    String titulo,
    String descripcion,
    String gradoAcademico,
    String asignatura,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
