package Katedra.Server.config;

import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    @Test
    void shouldFollowFlexibleSpineInTeoriaSystemPrompt() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);

        assertThat(prompt).contains("gancho o contexto");
        assertThat(prompt).contains("ejemplo resuelto");
        assertThat(prompt).contains("síntesis");
        assertThat(prompt).containsIgnoringCase("Katedra");
    }

    @Test
    void shouldEmbedLevelRubricInTeoriaSystemPrompt() {
        String primaria = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.PRIMARIA);
        String posgrado = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.MAX, NivelAcademico.POSGRADO);

        assertThat(primaria).contains(NivelAcademico.PRIMARIA.getRubrica());
        assertThat(posgrado).contains(NivelAcademico.POSGRADO.getRubrica());
        assertThat(primaria).isNotEqualTo(posgrado);
    }

    @Test
    void shouldEmbedTierParagraphTargetsInTeoriaSystemPrompt() {
        String flash = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO);
        String max = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.MAX, NivelAcademico.UNIVERSITARIO);

        assertThat(flash).contains(ModeloIA.FLASH.getMinParrafosTeoria() + " y " + ModeloIA.FLASH.getMaxParrafosTeoria());
        assertThat(max).contains(ModeloIA.MAX.getMinParrafosTeoria() + " y " + ModeloIA.MAX.getMaxParrafosTeoria());
    }

    @Test
    void shouldForbidCodeFencesAroundTeoriaContent() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.SECUNDARIA);

        assertThat(prompt).doesNotContain("```");
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
