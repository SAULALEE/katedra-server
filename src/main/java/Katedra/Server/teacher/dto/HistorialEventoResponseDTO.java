package Katedra.Server.teacher.dto;

import Katedra.Server.teacher.model.TipoEventoHistorial;

import java.time.LocalDateTime;

public record HistorialEventoResponseDTO(
    String id,
    String temarioId,
    String temarioTitulo,
    String asignaturaNombre,
    TipoEventoHistorial tipo,
    String detalle,
    LocalDateTime createdAt
) {}
