package Katedra.Server.dto;

import java.util.List;

public record GenerarMaterialTemarioRequestDTO(
    List<String> piezas,
    String modelo,
    List<String> regenerarPiezas
) {}
