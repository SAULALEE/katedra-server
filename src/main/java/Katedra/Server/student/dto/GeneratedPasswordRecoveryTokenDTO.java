package Katedra.Server.student.dto;

import java.time.LocalDateTime;

/** Transient result for the future delivery layer; never persist or log the raw token. */
public record GeneratedPasswordRecoveryTokenDTO(String token, LocalDateTime expiresAt) {
}
