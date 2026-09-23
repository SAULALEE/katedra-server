package Katedra.Server.teacher.dto;

import java.util.List;

public record EvaluacionPreguntaDTO(
    String pregunta,
    List<String> opciones,
    Integer opcionCorrectaIndex,
    String explicacion
) {}
