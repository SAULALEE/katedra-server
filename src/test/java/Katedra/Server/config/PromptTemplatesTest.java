package Katedra.Server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    @Test
    void shouldContainRequiredTeoriaSectionsInSystemPrompt() {
        String prompt = PromptTemplates.TEORIA_SYSTEM_PROMPT;

        assertThat(prompt).contains("## Introducción");
        assertThat(prompt).contains("## Conceptos clave");
        assertThat(prompt).contains("## Desarrollo");
        assertThat(prompt).contains("## Ejemplos aplicados");
        assertThat(prompt).contains("## Resumen");
    }

    @Test
    void shouldAdjustTeoriaDepthByAcademicGrade() {
        String prompt = PromptTemplates.TEORIA_SYSTEM_PROMPT;

        assertThat(prompt).contains("Nivel escolar");
        assertThat(prompt).contains("Pregrado");
        assertThat(prompt).contains("Posgrado");
    }

    @Test
    void shouldBuildUserPromptWithAllFields() {
        String userPrompt = PromptTemplates.buildUserPrompt(
            "Programación",
            "Pilas",
            "Universitario",
            "Algoritmos y estructuras de datos"
        );

        assertThat(userPrompt)
            .contains("Asignatura: Programación")
            .contains("Tema/Título: Pilas")
            .contains("Grado académico: Universitario")
            .contains("Contenido fuente");
    }

    @Test
    void shouldContainLabeledDifficultyTiersInEjerciciosPrompt() {
        String prompt = PromptTemplates.EJERCICIOS_SYSTEM_PROMPT;

        assertThat(prompt).contains("Ejercicio 1 — Básico");
        assertThat(prompt).contains("Ejercicio 2 — Intermedio");
        assertThat(prompt).contains("Ejercicio 3 — Avanzado");
        assertThat(prompt).contains("Nivel escolar");
        assertThat(prompt).contains("Pregrado");
        assertThat(prompt).contains("Posgrado");
    }

    @Test
    void shouldRequireCognitiveVarietyAndPlausibleDistractorsInEvaluacionPrompt() {
        String prompt = PromptTemplates.EVALUACION_SYSTEM_PROMPT;

        assertThat(prompt).contains("exactamente 3");
        assertThat(prompt).contains("recordar o comprender");
        assertThat(prompt).contains("aplicar");
        assertThat(prompt).contains("analizar");
        assertThat(prompt).contains("distractores");
        assertThat(prompt).contains("todas las anteriores");
    }

    @Test
    void shouldNotMentionFormatOrJsonInEvaluacionPrompt() {
        String prompt = PromptTemplates.EVALUACION_SYSTEM_PROMPT;

        assertThat(prompt).doesNotContainIgnoringCase("json");
        assertThat(prompt).doesNotContain("```");
    }

    @Test
    void shouldEmbedRequestedSlideCountInDiapositivasPrompt() {
        String prompt = PromptTemplates.buildDiapositivasSystemPrompt(7);

        assertThat(prompt).contains("exactamente 7 diapositivas");
        assertThat(prompt).contains("Nivel escolar");
        assertThat(prompt).contains("Pregrado");
        assertThat(prompt).contains("Posgrado");
    }

    @Test
    void shouldNotMentionFormatOrJsonInDiapositivasPrompt() {
        String prompt = PromptTemplates.buildDiapositivasSystemPrompt(5);

        assertThat(prompt).doesNotContainIgnoringCase("json");
        assertThat(prompt).doesNotContain("```");
    }
}
