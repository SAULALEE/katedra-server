package Katedra.Server.dto;

import Katedra.Server.model.RolUsuario;

import java.time.LocalDateTime;

public record UsuarioDTO(
    String id,
    String email,
    String nombre,
    RolUsuario rol,
    LocalDateTime createdAt
) {
    public UsuarioDTO(String id, String email, String nombre, RolUsuario rol) {
        this(id, email, nombre, rol, null);
    }
}
