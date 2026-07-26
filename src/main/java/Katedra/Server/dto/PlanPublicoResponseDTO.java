package Katedra.Server.dto;

import java.util.List;

public record PlanPublicoResponseDTO(
        String id,
        String nombre,
        String ciclo,
        long precio,
        String moneda,
        boolean recomendado,
        List<String> caracteristicas) {
}
