package Katedra.Server.dto;

public record TemarioRequestDTO(
    String titulo,
    String descripcion,
    String gradoAcademico,
    String asignatura
) {}
