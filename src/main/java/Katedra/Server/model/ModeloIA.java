package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Allowlist of Katedra's branded generation tiers. The JSON contract exposes only the
 * tier key (never a raw OpenAI model id), so the provider can change without breaking
 * clients. Each tier controls the underlying model, slide-count range, theory-paragraph
 * target and cognitive style, and the OpenAI call options used to avoid wasting tokens
 * on cheaper tiers.
 */
public enum ModeloIA {
    FLASH(
            "flash", "Tutor", "gpt-4.1-mini",
            8, 8, 8,
            3, 5,
            3, 5,
            0.5, 2500, false, null,
            """
            Estilo TUTOR — rápido y directo, pero SIEMPRE completo: el tono es ágil \
            y sin rodeos, no una lista de fragmentos sueltos. Cada párrafo debe \
            desarrollar su idea por completo, con una estructura clara (como \
            respondería un asistente de IA convencional tipo Claude o Gemini), no \
            una respuesta apresurada o inconclusa. No profundices en matices \
            secundarios ni en comparaciones extensas — eso es trabajo de niveles \
            superiores — pero nunca sacrifiques completitud ni claridad por \
            brevedad.""",
            """
            Estilo TUTOR — examen rápido y esencial: cada pregunta evalúa un solo \
            concepto central, con enunciados cortos y sin contexto extenso. \
            Prioriza los niveles de recordar/comprender y aplicar en situaciones \
            sencillas. Las explicaciones son breves pero completas: siempre \
            justifican la respuesta correcta sin rodeos."""),
    PRO(
            "pro", "Maestro", "gpt-5.4",
            15, 15, 15,
            5, 8,
            5, 7,
            null, 6000, true, "low",
            """
            Estilo MAESTRO — respuestas razonadas, coherentes y analíticas, con \
            mucho razonamiento explícito: no te limites a enunciar conceptos, \
            explica el porqué detrás de cada uno, cómo se conectan entre sí y qué \
            lo distingue de alternativas cercanas. Anticipa dudas frecuentes, \
            compara alternativas en profundidad y desarrolla el ejemplo paso a \
            paso, sin omitir ningún paso del razonamiento. Los párrafos pueden ser \
            extensos si el tema lo amerita: prioriza la coherencia argumentativa \
            sobre la brevedad. El desarrollo debe sentirse como la explicación de \
            un profesor que profundiza muy por encima de lo básico.""",
            """
            Estilo MAESTRO — examen razonado y con contexto: la mayoría de las \
            preguntas plantea una situación concreta o un mini-caso que exige \
            aplicar y analizar el concepto, no solo recordarlo. Los distractores \
            encarnan los errores conceptuales típicos del estudiante, y cada \
            explicación desmonta explícitamente ese error además de justificar \
            la opción correcta."""),
    MAX(
            "max", "Catedrático", "o4-mini",
            20, 20, 20,
            8, 12,
            15, 20,
            null, 12000, true, "medium",
            """
            Estilo CATEDRÁTICO — el nivel más alto y potente posible, con registro \
            profesional de publicación técnica: escribe como un catedrático experto \
            redactando un capítulo de referencia, con terminología precisa, \
            justificación formal y cero informalidad. PRIORIZA SIEMPRE la \
            profundidad y la extensión de cada párrafo por encima de la cantidad \
            de párrafos: es preferible tener menos párrafos, pero cada uno largo, \
            denso y completamente desarrollado, que muchos párrafos breves. Un \
            párrafo no termina hasta agotar su idea: justificación formal, \
            matices, comparaciones y ejemplos internos antes de pasar al \
            siguiente encabezado. No te conformes con igualar el nivel de \
            Maestro: donde Maestro dedica un párrafo a una idea, tú debes \
            profundizar esa misma idea con más rigor, más matices, y con un \
            párrafo notablemente más largo y completo — la extensión y la \
            profundidad POR PÁRRAFO deben superar claramente a las de Maestro, \
            nunca ser menores. Desarrolla el contenido en párrafos de prosa \
            completos y argumentados, NO como una sucesión de listas de \
            viñetas: usa listas solo para enumerar operaciones puntuales cuando \
            sea imprescindible, y aun así explica cada una con la misma densidad \
            narrativa que el resto del texto. Cubre explícitamente, con el mismo \
            cuidado profesional en cada uno: fundamentos formales y su \
            justificación, matices y casos límite, comparaciones cuantificadas \
            entre enfoques (costos, trade-offs, notación de complejidad en texto \
            plano), errores comunes o antipatrones, y conexiones con temas más \
            avanzados del área. Este es nuestro modelo más potente: su rigor \
            técnico, su atención al detalle y la extensión de cada párrafo \
            individual deben ser inconfundiblemente superiores a los de Maestro, \
            de modo que cualquier lector note de inmediato que está ante la \
            respuesta más completa, profesional y poderosa posible.""",
            """
            Estilo CATEDRÁTICO — el examen más riguroso posible: preguntas \
            multi-paso de análisis y diagnóstico, con escenarios realistas, datos \
            concretos, comparaciones entre enfoques y casos límite. Los \
            distractores son sutiles y solo distinguibles con dominio profundo \
            del tema, nunca descartables por sentido común. Cada explicación \
            justifica formalmente la opción correcta y detalla por qué falla \
            cada distractor.""");

    private final String valor;
    private final String displayName;
    private final String modelId;
    private final int minDiapositivas;
    private final int maxDiapositivas;
    private final int defaultDiapositivas;
    private final int minParrafosTeoria;
    private final int maxParrafosTeoria;
    private final int minPreguntas;
    private final int maxPreguntas;
    private final Double temperature;
    private final int maxTokens;
    private final boolean reasoning;
    private final String reasoningEffort;
    private final String estiloTeoria;
    private final String estiloEvaluacion;

    ModeloIA(String valor, String displayName, String modelId,
             int minDiapositivas, int maxDiapositivas, int defaultDiapositivas,
             int minParrafosTeoria, int maxParrafosTeoria,
             int minPreguntas, int maxPreguntas,
             Double temperature, int maxTokens, boolean reasoning, String reasoningEffort,
             String estiloTeoria, String estiloEvaluacion) {
        this.valor = valor;
        this.displayName = displayName;
        this.modelId = modelId;
        this.minDiapositivas = minDiapositivas;
        this.maxDiapositivas = maxDiapositivas;
        this.defaultDiapositivas = defaultDiapositivas;
        this.minParrafosTeoria = minParrafosTeoria;
        this.maxParrafosTeoria = maxParrafosTeoria;
        this.minPreguntas = minPreguntas;
        this.maxPreguntas = maxPreguntas;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.reasoning = reasoning;
        this.reasoningEffort = reasoningEffort;
        this.estiloTeoria = estiloTeoria;
        this.estiloEvaluacion = estiloEvaluacion;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMinDiapositivas() {
        return minDiapositivas;
    }

    public int getMaxDiapositivas() {
        return maxDiapositivas;
    }

    public int getDefaultDiapositivas() {
        return defaultDiapositivas;
    }

    public int getMinParrafosTeoria() {
        return minParrafosTeoria;
    }

    public int getMaxParrafosTeoria() {
        return maxParrafosTeoria;
    }

    public int getMinPreguntas() {
        return minPreguntas;
    }

    public int getMaxPreguntas() {
        return maxPreguntas;
    }

    /** Null for reasoning tiers: temperature is rejected by o-series and GPT-5 family models. */
    public Double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * True for tiers backed by a reasoning-style model (o-series, GPT-5 family). These
     * models reject {@code temperature} and use {@code max_completion_tokens} instead of
     * {@code max_tokens}, since it also covers hidden reasoning tokens.
     */
    public boolean isReasoning() {
        return reasoning;
    }

    /** Null unless {@link #isReasoning()} is true. */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * Tier-specific cognitive style for theory generation (Tutor: quick and direct;
     * Maestro: reasoned and analytical; Catedrático: most rigorous and in-depth).
     * Independent of {@link NivelAcademico}, which adapts to the student's grade
     * level rather than to how deep or fast the chosen tier should reason.
     */
    public String getEstiloTeoria() {
        return estiloTeoria;
    }

    /**
     * Tier-specific cognitive style for exam generation (Tutor: rápido y esencial;
     * Maestro: razonado y con contexto; Catedrático: el examen más riguroso posible,
     * con distractores sutiles y justificación formal).
     */
    public String getEstiloEvaluacion() {
        return estiloEvaluacion;
    }

    @JsonCreator
    public static ModeloIA fromValor(String valor) {
        for (ModeloIA modelo : values()) {
            if (modelo.valor.equalsIgnoreCase(valor)) {
                return modelo;
            }
        }
        throw new IllegalArgumentException("Modelo no permitido: " + valor);
    }
}
