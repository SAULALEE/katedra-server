package Katedra.Server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequestDTO(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 6, max = 100) String password,
    @NotBlank @Size(max = 100) String nombre
) {}
