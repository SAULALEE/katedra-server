package Katedra.Server.student.dto;

import java.time.LocalDateTime;

public record StudentProfileResponseDTO(
        String id,
        String email,
        String nombre,
        LocalDateTime createdAt
) {}
