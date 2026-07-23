package Katedra.Server.config;

import Katedra.Server.model.AssistantQuickAction;
import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.NivelAcademico;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    @Test
    void shouldDefineDistinctBasicAndAdvancedTemarioProfiles() {
        String basico = PromptTemplates.buildTeoriaSystemPrompt(
                ModeloIA.BASICO, NivelAcademico.UNIVERSITARIO);
        String avanzado = PromptTemplates.buildTeoriaSystemPrompt(
                ModeloIA.AVANZADO, NivelAcademico.UNIVERSITARIO);

        assertThat(basico)
                .containsIgnoringCase("breve")
                .containsIgnoringCase("información académica esencial");
        assertThat(avanzado)
                .containsIgnoringCase("desarrollo completo")
                .containsIgnoringCase("conceptos")
                .containsIgnoringCase("ejemplos")
                .containsIgnoringCase("actividades")
                .containsIgnoringCase("recursos");
        assertThat(basico).isNotEqualTo(avanzado);
    }

    @Test
    void shouldForbidConversationalAndMetaAiTextInGeneratedTemario() {
        String basico = PromptTemplates.buildTeoriaSystemPrompt(
                ModeloIA.BASICO, NivelAcademico.SECUNDARIA);
        String avanzado = PromptTemplates.buildTeoriaSystemPrompt(
                ModeloIA.AVANZADO, NivelAcademico.SECUNDARIA);

        assertThat(basico).contains(
                "Nunca respondas como un asistente conversacional",
                "No incluyas saludos",
                "despedidas",
                "Aquí tienes",
                "explicaciones sobre la IA",
                "EXCLUSIVAMENTE contenido académico");
        assertThat(avanzado).contains(
                "Nunca respondas como un asistente conversacional",
                "No incluyas saludos",
                "despedidas",
                "Aquí tienes",
                "explicaciones sobre la IA",
                "EXCLUSIVAMENTE contenido académico");
    }

    @Test
    void shouldUseProvidedSourceAsPrimaryAndOnlyComplementWhenInsufficient() {
        String systemPrompt = PromptTemplates.buildTeoriaSystemPrompt(
                ModeloIA.AVANZADO, NivelAcademico.UNIVERSITARIO);
        String userPrompt = PromptTemplates.buildUserPrompt(
                "Programación", "Pilas", "Universitario", "Texto extraído de la fuente");

        assertThat(systemPrompt)
                .containsIgnoringCase("fuente principal")
                .containsIgnoringCase("solo complementa")
                .containsIgnoringCase("insuficiente");
        assertThat(userPrompt)
                .contains("Texto extraído de la fuente")
                .containsIgnoringCase("referencia principal");
    }

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
        // All three tiers currently define a real range (min < max): "entre N y M párrafos".
        String flash = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO);
        String pro = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);
        String max = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.MAX, NivelAcademico.UNIVERSITARIO);

        assertThat(flash).contains(ModeloIA.FLASH.getMinParrafosTeoria() + " y " + ModeloIA.FLASH.getMaxParrafosTeoria());
        assertThat(pro).contains(ModeloIA.PRO.getMinParrafosTeoria() + " y " + ModeloIA.PRO.getMaxParrafosTeoria());
        assertThat(max).contains(ModeloIA.MAX.getMinParrafosTeoria() + " y " + ModeloIA.MAX.getMaxParrafosTeoria());
    }

    @Test
    void shouldEmbedDistinctTierStyleInTeoriaSystemPrompt() {
        String tutor = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO);
        String maestro = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);
        String catedratico = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.MAX, NivelAcademico.UNIVERSITARIO);

        assertThat(tutor).contains(ModeloIA.FLASH.getEstiloTeoria());
        assertThat(maestro).contains(ModeloIA.PRO.getEstiloTeoria());
        assertThat(catedratico).contains(ModeloIA.MAX.getEstiloTeoria());
        assertThat(tutor).isNotEqualTo(maestro).isNotEqualTo(catedratico);

        // The mandatory intro/development/conclusion structure applies to every tier.
        assertThat(tutor).contains("INTRODUCCIÓN").contains("DESARROLLO").contains("CONCLUSIÓN");
    }

    @Test
    void shouldForbidCodeFencesAroundTeoriaContent() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.SECUNDARIA);

        assertThat(prompt).doesNotContain("```");
    }

    @Test
    void shouldRequireProgressiveSourceGroundedTheoryWithoutRepetition() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);

        assertThat(prompt)
            .contains("mapa mental")
            .contains("una sola vez")
            .contains("información nueva")
            .contains("contenido fuente")
            .containsIgnoringCase("introducción")
            .containsIgnoringCase("desarrollo")
            .containsIgnoringCase("conclusión");
    }

    @Test
    void shouldRequireFinalSemanticDeduplicationReview() {
        String prompt = PromptTemplates.buildTeoriaSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);

        assertThat(prompt)
            .contains("revisión final")
            .contains("elimina o fusiona")
            .contains("aporte exclusivo")
            .contains("detalles concretos")
            .contains("contenido fuente");
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
        String prompt = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO);

        assertThat(prompt).contains(ModeloIA.FLASH.getMinPreguntas() + " y " + ModeloIA.FLASH.getMaxPreguntas());
        assertThat(prompt).containsIgnoringCase("recordar o comprender");
        assertThat(prompt).containsIgnoringCase("aplicar");
        assertThat(prompt).containsIgnoringCase("analizar");
        assertThat(prompt).contains("distractores");
        assertThat(prompt).contains("todas las anteriores");
    }

    @Test
    void shouldEmbedTierQuestionCountInEvaluacionPrompt() {
        String catedratico = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.MAX, NivelAcademico.UNIVERSITARIO);

        assertThat(catedratico).contains(ModeloIA.MAX.getMinPreguntas() + " y " + ModeloIA.MAX.getMaxPreguntas());
    }

    @Test
    void shouldEmbedDistinctTierStyleInEvaluacionPrompt() {
        String tutor = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.FLASH, NivelAcademico.UNIVERSITARIO);
        String maestro = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);
        String catedratico = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.MAX, NivelAcademico.UNIVERSITARIO);

        assertThat(tutor).contains(ModeloIA.FLASH.getEstiloEvaluacion());
        assertThat(maestro).contains(ModeloIA.PRO.getEstiloEvaluacion());
        assertThat(catedratico).contains(ModeloIA.MAX.getEstiloEvaluacion());
        assertThat(tutor).isNotEqualTo(maestro).isNotEqualTo(catedratico);
    }

    @Test
    void shouldNotMentionFormatOrJsonInEvaluacionPrompt() {
        String prompt = PromptTemplates.buildEvaluacionSystemPrompt(ModeloIA.PRO, NivelAcademico.UNIVERSITARIO);

        assertThat(prompt).doesNotContainIgnoringCase("json");
        assertThat(prompt).doesNotContain("```");
    }

    @Test
    void shouldForbidOffTopicRequestsInCorreccionSystemPrompt() {
        String prompt = PromptTemplates.buildCorreccionSystemPrompt(NivelAcademico.UNIVERSITARIO);

        assertThat(prompt).containsIgnoringCase("Katedra");
        assertThat(prompt).contains("Solo puedo ayudarte a corregir o mejorar la teoría de este tema");
        assertThat(prompt).contains(NivelAcademico.UNIVERSITARIO.getRubrica());
    }

    @Test
    void shouldBuildCorreccionUserPromptWithQuickActionInstruccion() {
        String prompt = PromptTemplates.buildCorreccionUserPrompt("## Teoría actual", AssistantQuickAction.ACORTAR, "a 4 párrafos");

        assertThat(prompt).contains(AssistantQuickAction.ACORTAR.getInstruccion());
        assertThat(prompt).contains("a 4 párrafos");
        assertThat(prompt).contains("## Teoría actual");
    }

    @Test
    void shouldBuildCorreccionUserPromptWithFreeChatMessageAsInstruccion() {
        String prompt = PromptTemplates.buildCorreccionUserPrompt("## Teoría actual", AssistantQuickAction.FREE_CHAT, "simplifica el lenguaje");

        assertThat(prompt).contains("simplifica el lenguaje");
        assertThat(prompt).contains("## Teoría actual");
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
