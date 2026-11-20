package Katedra.Server.dto;

import Katedra.Server.model.RolUsuario;

import java.time.LocalDateTime;

/** Transient result for the delivery layer; never persist or log the raw token. */
public record GeneratedPasswordRecoveryTokenDTO(String token, LocalDateTime expiresAt, RolUsuario rol) {
}
