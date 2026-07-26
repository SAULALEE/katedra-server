package Katedra.Server.dto;

import jakarta.validation.constraints.NotNull;

public record TemarioFavoritoRequestDTO(
        @NotNull(message = "El estado favorito es obligatorio")
        Boolean favorito
) {}
