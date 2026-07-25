package Katedra.Server.service.export;

import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates a Google Apps Script the teacher pastes into script.google.com to build the quiz as a
 * real Google Form — questions, options, answer key and per-question feedback.
 *
 * <p>Chosen over the Forms API because it needs no Google Cloud project, no OAuth consent screen
 * and no stored refresh tokens: the teacher authorizes the script in their own account.
 */
@Component
public class AppsScriptExportador implements ExportadorMaterial {

    /** Points awarded per question; Google Forms needs a number to auto-grade. */
    private static final int PUNTOS_POR_PREGUNTA = 1;

    /**
     * Built here rather than injected: the application context exposes Jackson 3's mapper, and this
     * needs a dedicated ASCII-only configuration anyway — battle-tested escaping for quotes,
     * backslashes, control characters and U+2028/U+2029, which are valid JSON but break JavaScript.
     */
    private final ObjectMapper mapperAscii = JsonMapper.builder()
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
            .build();

    /** Shape consumed by the generated script; kept flat so the JSON stays readable. */
    private record PreguntaScript(String texto, List<String> opciones, int correcta, String explicacion, int puntos) {}

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.APPS_SCRIPT;
    }

    @Override
    public byte[] exportar(MaterialExportableDTO material) throws IOException {
        if (material.pieza() != PiezaMaterial.EVALUACION) {
            throw new IllegalArgumentException(
                    "Google Forms sólo está disponible para la evaluación, no para " + material.pieza().getValor());
        }
        List<EvaluacionPreguntaDTO> preguntas = material.evaluacion();
        if (preguntas == null || preguntas.isEmpty()) {
            throw new IllegalArgumentException("No hay evaluación para exportar");
        }

        String json = mapperAscii.writeValueAsString(preguntas.stream().map(this::aScript).toList());
        return construirScript(material, json, preguntas.size()).getBytes(StandardCharsets.UTF_8);
    }

    private PreguntaScript aScript(EvaluacionPreguntaDTO pregunta) {
        List<String> opciones = pregunta.opciones() == null ? List.of() : pregunta.opciones();
        int correcta = pregunta.opcionCorrectaIndex() == null ? 0 : pregunta.opcionCorrectaIndex();
        return new PreguntaScript(
                pregunta.pregunta(),
                opciones,
                Math.min(Math.max(correcta, 0), Math.max(opciones.size() - 1, 0)),
                pregunta.explicacion() == null ? "" : pregunta.explicacion(),
                PUNTOS_POR_PREGUNTA);
    }

    private String construirScript(MaterialExportableDTO material, String preguntasJson, int total) {
        String materia = EscapadorJs.aAscii(material.materia());
        String temario = EscapadorJs.aAscii(material.temarioTitulo());
        String copyright = EscapadorJs.aAscii(MarcaDocumento.PIE_PAGINA);

        String titulo = EscapadorJs.aLiteral(material.temarioTitulo() + " — Evaluación");
        String descripcion = EscapadorJs.aLiteral(
                "Materia: " + material.materia() + "\nTemario: " + material.temarioTitulo()
                        + "\n" + MarcaDocumento.PIE_PAGINA);

        return """
                /**
                 * Katedra - Generador de Formulario de Google
                 *
                 * Materia:  %s
                 * Temario:  %s
                 * Preguntas: %d
                 * %s
                 *
                 * INSTRUCCIONES
                 * 1. Abre https://script.google.com y crea un proyecto nuevo.
                 * 2. Borra todo el contenido de Code.gs y pega este archivo completo.
                 * 3. Guarda (Ctrl+S) y pulsa "Ejecutar" con la funcion crearFormulario seleccionada.
                 * 4. Autoriza el acceso cuando Google lo solicite.
                 * 5. El enlace del formulario aparecera en el registro de ejecucion (Ver > Registros).
                 *
                 * NOTA: el formulario se crea en la raiz de tu Google Drive. Con muchas preguntas
                 * la ejecucion puede acercarse al limite de 6 minutos de Apps Script.
                 */

                var PREGUNTAS = %s;

                function crearFormulario() {
                  var form = FormApp.create(%s);
                  form.setDescription(%s);

                  // setIsQuiz debe ir antes de setPoints() y de la retroalimentacion,
                  // o Apps Script lanza un error en tiempo de ejecucion.
                  form.setIsQuiz(true);
                  form.setCollectEmail(true);
                  form.setShuffleQuestions(false);

                  PREGUNTAS.forEach(function (p, i) {
                    var item = form.addMultipleChoiceItem();
                    item.setTitle((i + 1) + '. ' + p.texto);
                    item.setRequired(true);
                    item.setPoints(p.puntos);
                    item.setChoices(p.opciones.map(function (texto, idx) {
                      return item.createChoice(texto, idx === p.correcta);
                    }));

                    if (p.explicacion) {
                      var feedback = FormApp.createFeedback().setText(p.explicacion).build();
                      item.setFeedbackForCorrect(feedback);
                      item.setFeedbackForIncorrect(feedback);
                    }
                  });

                  Logger.log('Formulario creado: ' + form.getPublishedUrl());
                  Logger.log('Editar: ' + form.getEditUrl());
                }
                """.formatted(materia, temario, total, copyright, preguntasJson, titulo, descripcion);
    }
}
