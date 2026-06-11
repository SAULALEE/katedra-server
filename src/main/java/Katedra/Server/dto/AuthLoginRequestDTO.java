package Katedra.Server.dto;

public record AuthLoginRequestDTO(
    String email,
    String password
) {}
