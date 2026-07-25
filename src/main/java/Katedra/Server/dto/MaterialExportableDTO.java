package Katedra.Server.dto;

import Katedra.Server.model.PiezaMaterial;

import java.util.List;

/**
 * Read-only snapshot of everything an exporter needs, assembled inside the service transaction.
 *
 * <p>Exporters never touch entities: {@code Temario.asignatura} and {@code Temario.usuario} are
 * LAZY, so reading them outside the transaction would throw {@code LazyInitializationException}.
 * Building this record while the session is still open keeps that boundary explicit.
 *
 * <p>Only the field matching {@code pieza} is guaranteed to be populated.
 */
public record MaterialExportableDTO(
    String materia,
    String temarioTitulo,
    String gradoAcademico,
    PiezaMaterial pieza,
    String teoria,
    List<EvaluacionPreguntaDTO> evaluacion,
    List<DiapositivaDTO> diapositivas,
    String theme
) {
    public MaterialExportableDTO(
            String materia,
            String temarioTitulo,
            String gradoAcademico,
            PiezaMaterial pieza,
            String teoria,
            List<EvaluacionPreguntaDTO> evaluacion,
            List<DiapositivaDTO> diapositivas) {
        this(materia, temarioTitulo, gradoAcademico, pieza, teoria, evaluacion, diapositivas, "light");
    }
}
