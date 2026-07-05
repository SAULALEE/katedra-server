package Katedra.Server.config;

public final class PromptTemplates {

    private PromptTemplates() {}

    /** Cap on source text injected into prompts so a full 10-page PDF doesn't blow up token cost. */
    private static final int MAX_FUENTE_CHARS = 8000;

    public static final String TEORIA_SYSTEM_PROMPT = """
        Eres un experto académico. Genera una explicación teórica clara, estructurada y
        pedagógicamente sólida en formato markdown (máximo 3000 caracteres), en español
        académico impecable, adecuada al grado académico indicado.
        Usa encabezados, ejemplos concretos y definiciones precisas.
        No incluyas JSON ni bloques de código cercados (code fences) alrededor del contenido.
        """;

    public static final String EJERCICIOS_SYSTEM_PROMPT = """
        Eres un experto académico. Genera al menos 3 ejercicios prácticos con su solución
        detallada, en formato markdown (máximo 2000 caracteres), en español académico
        impecable, con dificultad progresiva y adecuados al grado académico indicado.
        No incluyas JSON ni bloques de código cercados (code fences) alrededor del contenido.
        """;

    public static final String EVALUACION_SYSTEM_PROMPT = """
        Eres un experto académico. Genera exactamente 3 preguntas de opción múltiple en
        español académico impecable que evalúen la comprensión del tema, cada una con
        4 opciones, el índice de la opción correcta y una explicación detallada de por qué
        esa respuesta es correcta.
        """;

    public static final String DIAPOSITIVAS_SYSTEM_PROMPT = """
        Eres un experto académico. Genera al menos 3 diapositivas (título + puntos clave
        concisos) en español académico impecable para exponer el tema en clase, cubriendo
        introducción, desarrollo y cierre.
        """;

    public static String buildUserPrompt(String asignatura, String titulo, String gradoAcademico, String fuente) {
        String fuenteRecortada = (fuente == null || fuente.isBlank())
                ? "(sin fuente adicional, usa tu conocimiento general del tema)"
                : fuente.substring(0, Math.min(fuente.length(), MAX_FUENTE_CHARS));
        return String.format("""
                Asignatura: %s
                Tema/Título: %s
                Grado académico: %s
                Contenido fuente (referencia principal si está disponible):
                %s
                """, asignatura, titulo, gradoAcademico, fuenteRecortada);
    }
}
