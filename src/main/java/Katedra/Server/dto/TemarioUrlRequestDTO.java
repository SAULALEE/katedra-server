package Katedra.Server.dto;

public record TemarioUrlRequestDTO(
    String url,
    String titulo,
    String asignatura,
    String gradoAcademico
) {}
