package Katedra.Server.service.export;

import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Plain markdown export: the branded header, the piece itself, and the copyright line.
 *
 * <p>The evaluación body deliberately mirrors the "Markdown" tab of the material view
 * (ContentViewer's {@code getEvaluationMarkdown}), so what the teacher previews and what they
 * download are the same text.
 */
@Component
public class MarkdownExportador implements ExportadorMaterial {

    private static final char PRIMERA_LETRA = 'A';

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.MARKDOWN;
    }

    @Override
    public byte[] exportar(MaterialExportableDTO material) {
        String cuerpo = switch (material.pieza()) {
            case TEORIA -> material.teoria() == null ? "" : material.teoria();
            case EVALUACION -> cuerpoEvaluacion(material.evaluacion());
            default -> throw new IllegalArgumentException(
                    "Markdown no está disponible para " + material.pieza().getValor());
        };

        String documento = encabezado(material) + cuerpo.strip() + "\n\n---\n\n" + MarcaDocumento.PIE_PAGINA + "\n";
        return documento.getBytes(StandardCharsets.UTF_8);
    }

    private String encabezado(MaterialExportableDTO material) {
        StringBuilder sb = new StringBuilder()
                .append("# ").append(material.materia()).append('\n')
                .append("## ").append(material.temarioTitulo()).append("\n\n")
                .append("> Material generado con Katedra");

        if (material.gradoAcademico() != null && !material.gradoAcademico().isBlank()) {
            sb.append(" · Nivel: ").append(capitalizar(material.gradoAcademico()));
        }
        return sb.append("\n\n---\n\n").toString();
    }

    private String cuerpoEvaluacion(List<EvaluacionPreguntaDTO> preguntas) {
        if (preguntas == null || preguntas.isEmpty()) {
            return "";
        }
        return IntStream.range(0, preguntas.size())
                .mapToObj(i -> bloquePregunta(i, preguntas.get(i)))
                .collect(Collectors.joining("\n\n"));
    }

    private String bloquePregunta(int indice, EvaluacionPreguntaDTO pregunta) {
        List<String> opciones = pregunta.opciones() == null ? List.of() : pregunta.opciones();
        String opcionesTexto = IntStream.range(0, opciones.size())
                .mapToObj(i -> ((char) (PRIMERA_LETRA + i)) + ") " + opciones.get(i))
                .collect(Collectors.joining("\n"));

        return "### Pregunta " + (indice + 1) + "\n" + pregunta.pregunta() + "\n\n"
                + opcionesTexto + "\n\n"
                + "*Respuesta Correcta: " + letraCorrecta(pregunta) + "*\n\n"
                + "Explicación: " + (pregunta.explicacion() == null ? "" : pregunta.explicacion());
    }

    private String letraCorrecta(EvaluacionPreguntaDTO pregunta) {
        Integer indice = pregunta.opcionCorrectaIndex();
        return indice == null ? "-" : String.valueOf((char) (PRIMERA_LETRA + indice));
    }

    private String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}
