package Katedra.Server.dto;

public record AuthResponseDTO(
    String token,
    UsuarioDTO usuario
) {}
