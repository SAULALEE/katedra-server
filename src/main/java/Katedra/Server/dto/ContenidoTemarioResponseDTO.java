package Katedra.Server.dto;

import java.util.Map;

public record ContenidoTemarioResponseDTO(
    String id,
    String temarioId,
    Object teoria,
    Object ejercicios,
    Object evaluacion,
    Object diapositivas
) {}
