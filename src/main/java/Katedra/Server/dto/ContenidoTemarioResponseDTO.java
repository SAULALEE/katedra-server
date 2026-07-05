package Katedra.Server.dto;

import java.util.List;

/**
 * Generated material for a syllabus.
 *
 * @param modelo         last model used to generate content (null if never generated)
 * @param piezasOmitidas pieces skipped because they already existed and were not
 *                       force-regenerated; empty on plain GET
 */
public record ContenidoTemarioResponseDTO(
    String id,
    String temarioId,
    String teoria,
    String ejercicios,
    List<EvaluacionPreguntaDTO> evaluacion,
    List<DiapositivaDTO> diapositivas,
    String modelo,
    List<String> piezasOmitidas
) {}
