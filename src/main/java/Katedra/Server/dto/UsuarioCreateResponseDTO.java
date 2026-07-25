package Katedra.Server.dto;

public record UsuarioCreateResponseDTO(
        UsuarioDTO usuario,
        String temporaryPassword
) {
}
