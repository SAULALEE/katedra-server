package Katedra.Server.dto;

import java.util.List;

public record DiapositivaDTO(
    String titulo,
    List<String> puntos
) {}
