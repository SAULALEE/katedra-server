package Katedra.Server.dto;

import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;

public record AssistantResponseDTO(
    String content,
    ModeloIA modeloUsed,
    AssistantQuickAction action
) {}
