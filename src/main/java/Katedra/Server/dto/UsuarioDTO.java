package Katedra.Server.dto;

import Katedra.Server.model.RolUsuario;

public record UsuarioDTO(
    String id,
    String email,
    String nombre,
    RolUsuario rol
) {}
