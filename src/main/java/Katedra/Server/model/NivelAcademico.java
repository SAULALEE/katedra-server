package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Allowlist of academic levels a syllabus can target. The rubric is injected into every
 * generation prompt so the same topic reads differently depending on the student's level.
 */
public enum NivelAcademico {
    PRIMARIA(
            "primaria", "Primaria",
            "Nivel escolar primaria: frases muy cortas y directas, lenguaje cotidiano sin "
                    + "jerga técnica, ejemplos concretos y visuales tomados de la vida diaria del niño."),
    SECUNDARIA(
            "secundaria", "Secundaria",
            "Nivel escolar secundaria: introduce términos técnicos acompañados siempre de su "
                    + "definición, ejemplos guiados paso a paso, tono cercano pero ya académico."),
    BACHILLERATO(
            "bachillerato", "Bachillerato",
            "Nivel bachillerato: mayor rigor conceptual, conecta el tema con otros conceptos ya "
                    + "vistos, introduce notación o formalismo básico cuando aporte claridad."),
    UNIVERSITARIO(
            "universitario", "Universitario (pregrado)",
            "Nivel universitario (pregrado): rigor conceptual, notación técnica y formal cuando "
                    + "aplique, razonamiento explícito paso a paso, vocabulario propio de la disciplina."),
    POSGRADO(
            "posgrado", "Posgrado",
            "Nivel posgrado: profundidad teórica, matices y limitaciones del tema, conexión con el "
                    + "estado del arte, juicio crítico y vocabulario altamente especializado.");

    private final String valor;
    private final String etiqueta;
    private final String rubrica;

    NivelAcademico(String valor, String etiqueta, String rubrica) {
        this.valor = valor;
        this.etiqueta = etiqueta;
        this.rubrica = rubrica;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    /** Human-readable label injected into the user prompt so the level is always read. */
    public String getEtiqueta() {
        return etiqueta;
    }

    public String getRubrica() {
        return rubrica;
    }

    @JsonCreator
    public static NivelAcademico fromValor(String valor) {
        for (NivelAcademico nivel : values()) {
            if (nivel.valor.equalsIgnoreCase(valor)) {
                return nivel;
            }
        }
        throw new IllegalArgumentException("Nivel académico desconocido: " + valor);
    }

    public static NivelAcademico fromGradoAcademico(String gradoAcademico) {
        if (gradoAcademico == null || gradoAcademico.isBlank()) {
            throw new IllegalArgumentException("Nivel académico desconocido: " + gradoAcademico);
        }

        NivelAcademico resolved = null;
        for (String item : gradoAcademico.split(",")) {
            NivelAcademico candidate = fromEtiquetaFlexible(item);
            if (candidate != null && (resolved == null || candidate.ordinal() > resolved.ordinal())) {
                resolved = candidate;
            }
        }
        return resolved != null ? resolved : UNIVERSITARIO;
    }

    private static NivelAcademico fromEtiquetaFlexible(String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "primaria" -> PRIMARIA;
            case "secundaria" -> SECUNDARIA;
            case "preparatoria", "bachillerato" -> BACHILLERATO;
            case "universidad", "universitario", "universitario (pregrado)" -> UNIVERSITARIO;
            case "posgrado" -> POSGRADO;
            default -> null;
        };
    }
}
