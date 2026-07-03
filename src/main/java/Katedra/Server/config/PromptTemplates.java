package Katedra.Server.config;

public class PromptTemplates {

    private PromptTemplates() {}

    public static final String CONTENIDO_TEMARIO_SYSTEM_PROMPT = """
        Eres experto académico. Genera contenido educativo profesional.
        Responde SOLO con JSON válido, sin markdown.
        {
          "teoria": "string (markdown, máx 3000 chars)",
          "ejercicios": "string (markdown, máx 2000 chars)",
          "evaluacion": [
            {"pregunta": "...", "opciones": ["A","B","C","D"], "opcionCorrectaIndex": 0, "explicacion": "..."}
          ],
          "diapositivas": [
            {"titulo": "...", "puntos": ["punto1", "punto2"]}
          ]
        }
        Requisitos: Teoría estructurada, 3+ ejercicios, 3 preguntas eval, 3+ diapositivas, español académico.
        """;

    public static String buildUserPrompt(String materia, String tema, String unidades, String gradoAcademico) {
        return String.format("""
            Materia: %s
            Tema: %s
            Grado: %s
            Unidades: %s
            Genera contenido completo.
            """, materia, tema, gradoAcademico, unidades);
    }
}
