package Katedra.Server.dto;

public record TemarioUploadResponseDTO(
    TemarioResponseDTO temario,
    String contenidoId,
    String archivoNombre,
    String archivoTipo,
    String fuenteUrl,
    int caracteresExtraidos
) {}
