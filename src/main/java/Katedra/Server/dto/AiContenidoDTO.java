package Katedra.Server.dto;

import java.util.List;

public record AiContenidoDTO(
    String teoria,
    String ejercicios,
    List<EvaluacionPreguntaDTO> evaluacion,
    List<DiapositivaDTO> diapositivas
) {}
