package Katedra.Server.dto;

import Katedra.Server.model.TipoEventoHistorial;

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
