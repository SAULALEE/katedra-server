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

    private static final PromptTemplate ESTRUCTURA_SYSTEM_TEMPLATE = loadTemplate("estructura-system.st");

    private static final String TEORIA_BASICO = readResource("teoria-basico.st");

    private static final String TEORIA_AVANZADO = readResource("teoria-avanzado.st");

    private static final PromptTemplate DIAPOSITIVAS_SYSTEM_TEMPLATE = loadTemplate("diapositivas-system.st");

    private static final PromptTemplate USER_PROMPT_TEMPLATE = loadTemplate("user-prompt.st");

    public static String buildTeoriaSystemPrompt(ModeloIA modelo, NivelAcademico nivel, int numeroParrafos) {
        return TEORIA_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "perfilTeoria", resolvePerfilTeoria(modelo),
                "parrafosInstruccion", buildParrafosInstruccion(numeroParrafos, modelo.getMaxPalabrasTeoria())));
    }

    public static String buildEstructuraSystemPrompt(NivelAcademico nivel, int numeroModulos) {
        return ESTRUCTURA_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "numeroModulos", numeroModulos));
    }

    /**
     * Picks the theory profile for the tier. Both profiles live as .st resources rather
     * than as {@code ModeloIA.estiloTeoria} literals, so the depth contract of each tier
     * is reviewable as prompt content.
     */
    private static String resolvePerfilTeoria(ModeloIA modelo) {
        return switch (modelo) {
            case FLASH -> TEORIA_BASICO;
            case PRO -> TEORIA_AVANZADO;
        };
    }

    public static String buildEvaluacionSystemPrompt(ModeloIA modelo, NivelAcademico nivel, int numeroPreguntas) {
        return EVALUACION_SYSTEM_TEMPLATE.render(Map.of(
                "katedraContext", KATEDRA_CONTEXT,
                "nivelRubrica", nivel.getRubrica(),
                "estiloEvaluacion", modelo.getEstiloEvaluacion(),
                "cantidadInstruccion", "Genera exactamente " + numeroPreguntas + " preguntas en total."));
    }

    /**
     * Renders the paragraph-count instruction for the user-selected (or tier-default)
     * count, appending a total word ceiling for tiers that define one (e.g. Catedrático's
     * 5000-word cap), since a high paragraph target alone doesn't bound total length.
     */
    private static String buildParrafosInstruccion(int numeroParrafos, Integer maxPalabras) {
        String instruccion = "Genera exactamente " + numeroParrafos + " párrafos en total.";
        if (maxPalabras != null) {
            instruccion += " El desarrollo completo no debe exceder " + maxPalabras + " palabras en total.";
        }
        return instruccion;
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
