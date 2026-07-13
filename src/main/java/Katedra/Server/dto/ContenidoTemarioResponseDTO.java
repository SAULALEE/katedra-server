package Katedra.Server.dto;

import java.util.List;
import java.util.Map;

/**
 * Generated material for a syllabus.
 *
 * @param modelo         last model used to generate content (null if never generated)
 * @param piezasFallidas pieces whose generation failed, mapped to the underlying error
 *                       message (e.g. an OpenAI API error); empty on plain GET or full success
 */
public record ContenidoTemarioResponseDTO(
    String id,
    String temarioId,
    String teoria,
    List<EvaluacionPreguntaDTO> evaluacion,
    List<DiapositivaDTO> diapositivas,
    String modelo,
    Map<String, String> piezasFallidas
) {}
