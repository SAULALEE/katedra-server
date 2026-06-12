package Katedra.Server.dto;

public record AuthRegisterRequestDTO(
    String email,
    String password,
    String nombre
) {}
