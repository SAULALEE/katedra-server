package Katedra.Server.dto;

import java.util.List;

public record ContenidoTemarioResponseDTO(
    String id,
    String temarioId,
    Object teoria,
    Object ejercicios,
    Object evaluacion,
    Object diapositivas,
    List<String> piezasOmitidas
) {
    public ContenidoTemarioResponseDTO(
            String id,
            String temarioId,
            Object teoria,
            Object ejercicios,
            Object evaluacion,
            Object diapositivas) {
        this(id, temarioId, teoria, ejercicios, evaluacion, diapositivas, List.of());
    }
}
