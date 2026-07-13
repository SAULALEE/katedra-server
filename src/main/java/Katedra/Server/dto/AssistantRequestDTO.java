package Katedra.Server.dto;

import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;

public record AssistantRequestDTO(
    String temarioId,
    AssistantQuickAction action,
    String message,
    ModeloIA modelo
) {}
