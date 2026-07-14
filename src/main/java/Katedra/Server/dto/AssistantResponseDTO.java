package Katedra.Server.dto;

import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;

/**
 * Result of a correction-assistant request.
 *
 * @param content    the corrected theory markdown, or the canned refusal message when the
 *                   request was off-topic
 * @param modeloUsed tier used for the correction (null when refused before any LLM call)
 * @param action     the resolved action
 * @param corregido  {@code true} if the theory was actually edited and saved; {@code false}
 *                   when the request was refused as off-topic (theory left unchanged)
 */
public record AssistantResponseDTO(
    String content,
    ModeloIA modeloUsed,
    AssistantQuickAction action,
    boolean corregido
) {}
