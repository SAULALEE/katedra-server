package Katedra.Server.dto;

import Katedra.Server.model.RolUsuario;

public record UsuarioUpdateRequestDTO(
        String nombre,
        String email,
        RolUsuario rol
) {
}
