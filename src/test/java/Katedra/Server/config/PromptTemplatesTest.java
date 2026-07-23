package Katedra.Server.config;

import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    @Test
    void shouldFollowFlexibleSpineInTeoriaSystemPrompt() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, ModeloIA.PRO.getDefaultParrafosTeoria());

        assertThat(prompt).contains("gancho o contexto");
        assertThat(prompt).contains("ejemplo resuelto");
        assertThat(prompt).contains("síntesis");
        assertThat(prompt).containsIgnoringCase("Katedra");
    }

    @Test
    void shouldEmbedLevelRubricInTeoriaSystemPrompt() {
        String primaria = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.PRIMARIA, ModeloIA.FLASH.getDefaultParrafosTeoria());
        String posgrado = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.POSGRADO, ModeloIA.PRO.getDefaultParrafosTeoria());

        assertThat(primaria).contains(NivelAcademico.PRIMARIA.getRubrica());
        assertThat(posgrado).contains(NivelAcademico.POSGRADO.getRubrica());
        assertThat(primaria).isNotEqualTo(posgrado);
    }

    @Test
    void shouldEmbedRequestedParagraphCountInTeoriaSystemPrompt() {
        String flash = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO, 12);
        String pro = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, 25);

        assertThat(flash).contains("exactamente 12 párrafos");
        assertThat(pro).contains("exactamente 25 párrafos");
        // Catedrático additionally caps total length by word count.
        assertThat(pro).contains(ModeloIA.PRO.getMaxPalabrasTeoria() + " palabras");
        assertThat(flash).doesNotContain("palabras en total");
    }

    @Test
    void shouldEmbedDistinctTierStyleInTeoriaSystemPrompt() {
        String tutor = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO, ModeloIA.FLASH.getDefaultParrafosTeoria());
        String catedratico = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, ModeloIA.PRO.getDefaultParrafosTeoria());

        assertThat(tutor).contains(ModeloIA.FLASH.getEstiloTeoria());
        assertThat(catedratico).contains(ModeloIA.PRO.getEstiloTeoria());
        assertThat(tutor).isNotEqualTo(catedratico);

        // The mandatory intro/development/conclusion structure applies to every tier.
        assertThat(tutor).contains("INTRODUCCIÓN").contains("DESARROLLO").contains("CONCLUSIÓN");
    }

    @Test
    void shouldForbidCodeFencesAroundTeoriaContent() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.SECUNDARIA, ModeloIA.PRO.getDefaultParrafosTeoria());

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
    void shouldRequireCognitiveVarietyAndPlausibleDistractorsInEvaluacionPrompt() {
        String prompt = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO, ModeloIA.FLASH.getDefaultPreguntas());

        assertThat(prompt).contains("exactamente " + ModeloIA.FLASH.getDefaultPreguntas() + " preguntas");
        assertThat(prompt).containsIgnoringCase("recordar o comprender");
        assertThat(prompt).containsIgnoringCase("aplicar");
        assertThat(prompt).containsIgnoringCase("analizar");
        assertThat(prompt).contains("distractores");
        assertThat(prompt).contains("todas las anteriores");
    }

    @Test
    void shouldEmbedRequestedQuestionCountInEvaluacionPrompt() {
        String catedratico = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, 18);

        assertThat(catedratico).contains("exactamente 18 preguntas");
    }

    @Test
    void shouldEmbedDistinctTierStyleInEvaluacionPrompt() {
        String tutor = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO, ModeloIA.FLASH.getDefaultPreguntas());
        String catedratico = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, ModeloIA.PRO.getDefaultPreguntas());

        assertThat(tutor).contains(ModeloIA.FLASH.getEstiloEvaluacion());
        assertThat(catedratico).contains(ModeloIA.PRO.getEstiloEvaluacion());
        assertThat(tutor).isNotEqualTo(catedratico);
    }

    @Test
    void shouldNotMentionFormatOrJsonInEvaluacionPrompt() {
        String prompt = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO, ModeloIA.PRO.getDefaultPreguntas());

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
    void shouldRequireCoverSlideAndTheoryGroundingInDiapositivasPrompt() {
        String prompt = PromptTemplates.buildDiapositivasSystemPrompt(8);

        assertThat(prompt).containsIgnoringCase("portada");
        assertThat(prompt).contains("teoría ya generada");
        assertThat(prompt).containsIgnoringCase("Katedra");
    }

    @Test
    void shouldNotMentionFormatOrJsonInDiapositivasPrompt() {
        String prompt = PromptTemplates.buildDiapositivasSystemPrompt(5);

        assertThat(prompt).doesNotContainIgnoringCase("json");
        assertThat(prompt).doesNotContain("```");
    }
}
