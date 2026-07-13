package Katedra.Server.config;

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

    private static final PromptTemplate DIAPOSITIVAS_SYSTEM_TEMPLATE = loadTemplate("diapositivas-system.st");

    private static final PromptTemplate USER_PROMPT_TEMPLATE = loadTemplate("user-prompt.st");

    public static String buildTeoriaSystemPrompt(ModeloIA modelo, NivelAcademico nivel) {
        return TEORIA_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "estiloTeoria", modelo.getEstiloTeoria(),
                "parrafosInstruccion", buildCantidadInstruccion(
                        modelo.getMinParrafosTeoria(), modelo.getMaxParrafosTeoria(), "párrafos")));
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
