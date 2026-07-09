package Katedra.Server.dto;

import Katedra.Server.model.NivelAcademico;

import java.time.LocalDateTime;

public record TemarioResponseDTO(
    String id,
    String titulo,
    String descripcion,
    NivelAcademico gradoAcademico,
    String asignatura,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
