package Katedra.Server.config;

import Katedra.Server.model.ModeloIA;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Builds per-tier OpenAI call options, shared by every service that talks to the model
 * (content generation and the correction assistant). Reasoning-style models (o-series,
 * GPT-5 family) reject {@code temperature} and require {@code max_completion_tokens}
 * instead of {@code max_tokens}, since it also covers hidden reasoning tokens.
 */
public final class OpenAiOptionsFactory {

    private OpenAiOptionsFactory() {}

    public static OpenAiChatOptions.Builder forModelo(ModeloIA modelo) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(modelo.getModelId());
        if (modelo.isReasoning()) {
            options.reasoningEffort(modelo.getReasoningEffort())
                    .maxCompletionTokens(modelo.getMaxTokens());
        } else {
            options.temperature(modelo.getTemperature())
                    .maxTokens(modelo.getMaxTokens());
        }
        return options;
    }
}
