package Katedra.Server.service;

import Katedra.Server.config.OpenAiOptionsFactory;
import Katedra.Server.config.PromptTemplates;
import Katedra.Server.dto.AssistantRequestDTO;
import Katedra.Server.dto.AssistantResponseDTO;
import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ContenidoTemario;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Theory-correction assistant. Scope is intentionally narrow: it only edits the already
 * generated {@code ContenidoTemario.teoria}, never answers general questions or generates
 * new material — see {@code correccion-system.st} for the enforcement prompt and
 * {@link #esInstruccionDeCorreccion(String)} for the zero-token pre-filter that rejects
 * obvious off-topic {@link AssistantQuickAction#FREE_CHAT} messages before any LLM call.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String MENSAJE_RECHAZO =
            "Solo puedo ayudarte a corregir o mejorar la teoría de este tema. Indícame qué cambio quieres hacer.";

    private static final String MENSAJE_TIMEOUT =
            "La corrección tardó demasiado y fue cancelada. Intenta de nuevo o pide un cambio más simple.";

    private static final String MENSAJE_ERROR =
            "No se pudo procesar la corrección en este momento. Intenta de nuevo.";

    /**
     * Corrections must never route to a reasoning tier (PRO/MAX routinely take 30-90s per
     * {@code application.properties}) — always use the fast, non-reasoning FLASH model
     * regardless of what the client requests, so every correction can realistically finish
     * within {@link #TIMEOUT_SECONDS}.
     */
    private static final ModeloIA MODELO_CORRECCION = ModeloIA.FLASH;

    private static final long TIMEOUT_SECONDS = 10;

    /** Rough heuristic (~4 chars/token) used only to size the output cap, not for billing. */
    private static final int CHARS_POR_TOKEN = 4;
    private static final int MIN_MAX_TOKENS = 500;
    private static final int MAX_MAX_TOKENS = MODELO_CORRECCION.getMaxTokens();

    /**
     * Keywords that signal an edit intent (accent/case-insensitive). Used only to gate
     * {@link AssistantQuickAction#FREE_CHAT} messages before spending any tokens; quick
     * actions always carry a canned instruction and skip this filter entirely.
     */
    private static final List<String> PALABRAS_CORRECCION = List.of(
            "acorta", "acortar", "corto", "reduce", "resume", "resumen",
            "amplia", "amplía", "extiende", "extender", "alarga", "alargar", "añade", "anade", "agrega", "agregar",
            "parrafo", "párrafo", "parrafos", "párrafos",
            "simplifica", "simplificar", "sencillo", "facil", "fácil", "claro", "clara",
            "ejemplo", "ejemplos",
            "corrige", "corregir", "correccion", "corrección", "ortografia", "ortografía", "redaccion", "redacción",
            "reescribe", "reescribir", "mejora", "mejorar", "formal", "informal", "cambia", "cambiar", "modifica", "modificar"
    );

    private final ChatClient chatClient;
    private final ContenidoTemarioService contenidoTemarioService;
    private final Executor executor;

    public AssistantService(
            ChatClient.Builder chatClientBuilder, ContenidoTemarioService contenidoTemarioService, Executor executor) {
        this.chatClient = chatClientBuilder.build();
        this.contenidoTemarioService = contenidoTemarioService;
        this.executor = executor;
    }

    @Async
    public CompletableFuture<AssistantResponseDTO> corregir(String temarioId, String userEmail, AssistantRequestDTO request) {
        AssistantQuickAction action = request.action() != null ? request.action() : AssistantQuickAction.FREE_CHAT;
        String message = request.message();

        if (action == AssistantQuickAction.FREE_CHAT && (message == null || message.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar un mensaje para el chat libre");
        }

        if (action == AssistantQuickAction.FREE_CHAT && !esInstruccionDeCorreccion(message)) {
            return CompletableFuture.completedFuture(
                    new AssistantResponseDTO(MENSAJE_RECHAZO, null, action, false));
        }

        ContenidoTemario contenido = contenidoTemarioService.getContenidoOwned(temarioId, userEmail);
        String teoriaActual = contenido.getTeoria();
        if (teoriaActual == null || teoriaActual.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Genera la teoría primero antes de usar el asistente de corrección");
        }

        NivelAcademico nivel = contenido.getTemario().getGradoAcademico();
        OpenAiChatOptions.Builder options = OpenAiOptionsFactory.forModelo(MODELO_CORRECCION)
                .maxTokens(estimarMaxTokensCorreccion(teoriaActual));

        log.info("Corrigiendo teoría [{}] del temario {} (acción {})", MODELO_CORRECCION.getDisplayName(), temarioId, action);

        // The LLM call runs in its own future (not inline) so orTimeout can race it: a plain
        // synchronous call inside this @Async method can't be preempted once started.
        return CompletableFuture
                .supplyAsync(() -> chatClient.prompt()
                        .system(PromptTemplates.buildCorreccionSystemPrompt(nivel))
                        .user(PromptTemplates.buildCorreccionUserPrompt(teoriaActual, action, message))
                        .options(options)
                        .call()
                        .content(), executor)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((teoriaCorregida, ex) -> {
                    if (ex != null) {
                        Throwable causa = (ex instanceof CompletionException) ? ex.getCause() : ex;
                        if (causa instanceof TimeoutException) {
                            log.warn("Corrección del temario {} superó {}s, cancelada", temarioId, TIMEOUT_SECONDS);
                            return new AssistantResponseDTO(MENSAJE_TIMEOUT, MODELO_CORRECCION, action, false);
                        }
                        log.error("Error corrigiendo teoría del temario {}", temarioId, causa);
                        return new AssistantResponseDTO(MENSAJE_ERROR, MODELO_CORRECCION, action, false);
                    }
                    contenidoTemarioService.guardarTeoria(contenido, teoriaCorregida);
                    return new AssistantResponseDTO(teoriaCorregida, MODELO_CORRECCION, action, true);
                });
    }

    /**
     * Sizes the output cap to the theory being edited (~4 chars/token, 1.5x margin for
     * growth actions like EXTENDER/AGREGAR_EJEMPLO) instead of always requesting the tier's
     * full ceiling — a tighter cap bounds worst-case decode time, keeping corrections inside
     * {@link #TIMEOUT_SECONDS}s even without the timeout race.
     */
    private int estimarMaxTokensCorreccion(String teoriaActual) {
        int tokensEstimados = teoriaActual.length() / CHARS_POR_TOKEN;
        int conMargen = (int) (tokensEstimados * 1.5);
        return Math.min(MAX_MAX_TOKENS, Math.max(MIN_MAX_TOKENS, conMargen));
    }

    private boolean esInstruccionDeCorreccion(String message) {
        String normalizado = quitarAcentos(message.toLowerCase(Locale.ROOT));
        return PALABRAS_CORRECCION.stream()
                .map(this::quitarAcentos)
                .anyMatch(normalizado::contains);
    }

    private String quitarAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
