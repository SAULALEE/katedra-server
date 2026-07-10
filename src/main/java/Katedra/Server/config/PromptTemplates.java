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

    public static final String EJERCICIOS_SYSTEM_PROMPT = readResource("ejercicios-system.st");

    public static final String EVALUACION_SYSTEM_PROMPT = readResource("evaluacion-system.st");

    private static final PromptTemplate TEORIA_SYSTEM_TEMPLATE = loadTemplate("teoria-system.st");

    private static final PromptTemplate DIAPOSITIVAS_SYSTEM_TEMPLATE = loadTemplate("diapositivas-system.st");

    private static final PromptTemplate USER_PROMPT_TEMPLATE = loadTemplate("user-prompt.st");

    public static String buildTeoriaSystemPrompt(ModeloIA modelo, NivelAcademico nivel) {
        return TEORIA_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "estiloTeoria", modelo.getEstiloTeoria(),
                "parrafosInstruccion", buildParrafosInstruccion(modelo)));
    }

    /**
     * Renders the exact wording for the paragraph-count instruction in Java rather
     * than in the .st template, since the phrasing branches on whether the tier has
     * a fixed count (min == max) or a range (e.g. Catedrático's 8-10).
     */
    private static String buildParrafosInstruccion(ModeloIA modelo) {
        int min = modelo.getMinParrafosTeoria();
        int max = modelo.getMaxParrafosTeoria();
        if (min == max) {
            return "Redacta exactamente " + min + " párrafos en total.";
        }
        return "Redacta entre " + min + " y " + max + " párrafos en total, distribuidos "
                + "según la amplitud real del tema (temas simples cerca del mínimo, "
                + "temas amplios cerca del máximo).";
    }

    public static String buildDiapositivasSystemPrompt(int numeroDiapositivas) {
        return DIAPOSITIVAS_SYSTEM_TEMPLATE.render(Map.of("numeroDiapositivas", numeroDiapositivas));
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
