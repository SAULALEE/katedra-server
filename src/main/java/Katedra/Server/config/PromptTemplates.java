package Katedra.Server.config;

import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads generation prompts from {@code classpath:/prompts/*.st} instead of hardcoding
 * them as Java string literals — prompts are content, not code, so they live as
 * externalized, individually diffable/reviewable resource files (Spring AI's
 * {@link PromptTemplate} convention). Static prompts are read once as plain text;
 * prompts with variables are rendered per call via {@code {placeholder}} substitution.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    /** Cap on source text injected into prompts so a full 10-page PDF doesn't blow up token cost. */
    private static final int MAX_FUENTE_CHARS = 8000;

    private static final String KATEDRA_CONTEXT = readResource("context.st");

    private static final PromptTemplate EVALUACION_SYSTEM_TEMPLATE = loadTemplate("evaluacion-system.st");

    private static final PromptTemplate TEORIA_SYSTEM_TEMPLATE = loadTemplate("teoria-system.st");

    private static final String TEORIA_BASICO = readResource("teoria-basico.st");

    private static final String TEORIA_AVANZADO = readResource("teoria-avanzado.st");

    private static final PromptTemplate DIAPOSITIVAS_SYSTEM_TEMPLATE = loadTemplate("diapositivas-system.st");

    private static final PromptTemplate USER_PROMPT_TEMPLATE = loadTemplate("user-prompt.st");

    private static final PromptTemplate CORRECCION_SYSTEM_TEMPLATE = loadTemplate("correccion-system.st");

    private static final PromptTemplate CORRECCION_USER_TEMPLATE = loadTemplate("correccion-user.st");

    public static String buildTeoriaSystemPrompt(ModeloIA modelo, NivelAcademico nivel) {
        return TEORIA_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "perfilTeoria", resolvePerfilTeoria(modelo),
                "parrafosInstruccion", buildCantidadInstruccion(
                        modelo.getMinParrafosTeoria(), modelo.getMaxParrafosTeoria(), "párrafos")));
    }

    private static String resolvePerfilTeoria(ModeloIA modelo) {
        return switch (modelo) {
            case BASICO -> TEORIA_BASICO;
            case AVANZADO -> TEORIA_AVANZADO;
            default -> modelo.getEstiloTeoria();
        };
    }

    public static String buildEvaluacionSystemPrompt(ModeloIA modelo, NivelAcademico nivel) {
        return EVALUACION_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "estiloEvaluacion", modelo.getEstiloEvaluacion(),
                "cantidadInstruccion", buildCantidadInstruccion(
                        modelo.getMinPreguntas(), modelo.getMaxPreguntas(), "preguntas")));
    }

    /**
     * Renders the exact wording for a count instruction (paragraphs, exercises,
     * questions) in Java rather than in each .st template, since the phrasing branches
     * on whether the tier has a fixed count (min == max) or a range (e.g. Catedrático's
     * 15-20).
     */
    private static String buildCantidadInstruccion(int min, int max, String sustantivo) {
        if (min == max) {
            return "Genera exactamente " + min + " " + sustantivo + " en total.";
        }
        return "Genera entre " + min + " y " + max + " " + sustantivo + " en total, elige la "
                + "cantidad según la amplitud real del tema (temas simples cerca del mínimo, "
                + "temas amplios cerca del máximo) — nunca sacrifiques la calidad por alcanzar "
                + "el máximo.";
    }

    /**
     * System prompt for the theory-correction assistant: the model may only edit the
     * supplied theory (keeping the student's academic register) and must refuse anything else.
     */
    public static String buildCorreccionSystemPrompt(NivelAcademico nivel) {
        return CORRECCION_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica()));
    }

    /**
     * User prompt for a correction request: resolves the {@code action} to its canned
     * instruction and appends the free-form {@code message} (for {@link AssistantQuickAction#FREE_CHAT}
     * the message IS the instruction), then injects the current theory to edit.
     */
    public static String buildCorreccionUserPrompt(String teoriaActual, AssistantQuickAction action, String message) {
        return CORRECCION_USER_TEMPLATE.render(Map.of(
                "instruccion", buildCorreccionInstruccion(action, message),
                "teoriaActual", teoriaActual));
    }

    private static String buildCorreccionInstruccion(AssistantQuickAction action, String message) {
        String base = action.getInstruccion();
        String extra = (message == null) ? "" : message.strip();
        if (base == null) {
            return extra;
        }
        return extra.isEmpty() ? base : base + " " + extra;
    }

    public static String buildDiapositivasSystemPrompt(int numeroDiapositivas) {
        return DIAPOSITIVAS_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "numeroDiapositivas", numeroDiapositivas));
    }

    public static String buildUserPrompt(String asignatura, String titulo, String gradoAcademico, String fuente) {
        String fuenteRecortada = (fuente == null || fuente.isBlank())
                ? "(sin fuente adicional, usa tu conocimiento general del tema)"
                : fuente.substring(0, Math.min(fuente.length(), MAX_FUENTE_CHARS));
        return USER_PROMPT_TEMPLATE.render(Map.of(
                "asignatura", asignatura,
                "titulo", titulo,
                "gradoAcademico", gradoAcademico,
                "fuente", fuenteRecortada));
    }

    private static PromptTemplate loadTemplate(String filename) {
        return new PromptTemplate(resource(filename));
    }

    private static String readResource(String filename) {
        try {
            return resource(filename).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el prompt: " + filename, ex);
        }
    }

    private static Resource resource(String filename) {
        return new ClassPathResource("prompts/" + filename);
    }
}
