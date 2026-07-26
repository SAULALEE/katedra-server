package Katedra.Server.service.export;

import Katedra.Server.dto.EvaluacionPreguntaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.service.export.BloqueMarkdown.Cita;
import Katedra.Server.service.export.BloqueMarkdown.Codigo;
import Katedra.Server.service.export.BloqueMarkdown.Elemento;
import Katedra.Server.service.export.BloqueMarkdown.Fragmento;
import Katedra.Server.service.export.BloqueMarkdown.Parrafo;
import Katedra.Server.service.export.BloqueMarkdown.Regla;
import Katedra.Server.service.export.BloqueMarkdown.Tabla;
import Katedra.Server.service.export.BloqueMarkdown.Titulo;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Word export built with POI's XWPF API.
 *
 * <p>A DEFAULT header/footer repeats on every page by itself, so unlike the PDF there is no second
 * stamping pass. The document is then locked with read-only enforcement: Word and LibreOffice
 * refuse edits, which stops the footer from being removed casually. That is not encryption — the
 * hard-protected artifact is the PDF.
 */
@Component
public class DocxExportador implements ExportadorMaterial {

    // Inter is used for headings, body copy and labels; JetBrains Mono remains dedicated to code.
    private static final String FUENTE_TITULO = "Inter";
    private static final String FUENTE_CUERPO = "Inter";
    private static final String FUENTE_MONO = "JetBrains Mono";
    private static final String TINTA = "0F172A";
    private static final String GRIS = "64748B";
    private static final String FONDO_CODIGO = "F1F5F9";

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.DOCX;
    }

    @Override
    public byte[] exportar(MaterialExportableDTO material) throws IOException {
        try (XWPFDocument documento = new XWPFDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            construirEncabezado(documento, material);
            construirPie(documento);

            switch (material.pieza()) {
                case TEORIA -> renderTeoria(documento, material);
                case EVALUACION -> renderEvaluacion(documento, material);
                default -> throw new IllegalArgumentException(
                        "DOCX no está disponible para " + material.pieza().getValor());
            }

            describir(documento, material);
            proteger(documento);

            documento.write(salida);
            return salida.toByteArray();
        }
    }

    // ------------------------------------------------------------- branding

    private void construirEncabezado(XWPFDocument documento, MaterialExportableDTO material) {
        XWPFHeader encabezado = documento.createHeader(HeaderFooterType.DEFAULT);

        XWPFParagraph materia = encabezado.getParagraphArray(0) != null
                ? encabezado.getParagraphArray(0)
                : encabezado.createParagraph();
        XWPFRun runMateria = materia.createRun();
        runMateria.setText(MarcaDocumento.lineaMateria(material.materia()));
        runMateria.setBold(true);
        runMateria.setFontSize(9);
        runMateria.setColor(GRIS);
        runMateria.setFontFamily(FUENTE_CUERPO);

        XWPFParagraph temario = encabezado.createParagraph();
        temario.setBorderBottom(Borders.SINGLE);
        XWPFRun runTemario = temario.createRun();
        runTemario.setText(MarcaDocumento.lineaTemario(material.temarioTitulo()));
        runTemario.setFontSize(12);
        runTemario.setColor(TINTA);
        runTemario.setFontFamily(FUENTE_TITULO);
    }

    private void construirPie(XWPFDocument documento) {
        XWPFFooter pie = documento.createFooter(HeaderFooterType.DEFAULT);

        XWPFParagraph parrafo = pie.getParagraphArray(0) != null
                ? pie.getParagraphArray(0)
                : pie.createParagraph();
        parrafo.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = parrafo.createRun();
        run.setText(MarcaDocumento.PIE_PAGINA);
        run.setFontSize(9);
        run.setColor(GRIS);
        run.setFontFamily(FUENTE_CUERPO);
    }

    private void describir(XWPFDocument documento, MaterialExportableDTO material) {
        POIXMLProperties.CoreProperties propiedades = documento.getProperties().getCoreProperties();
        propiedades.setTitle(MarcaDocumento.titulo(material.materia(), material.temarioTitulo()));
        propiedades.setCreator(MarcaDocumento.AUTOR);
        propiedades.setDescription(MarcaDocumento.PIE_PAGINA);
    }

    /** Random password, never stored: the lock exists to protect the branding, not to be reopened. */
    private void proteger(XWPFDocument documento) {
        byte[] semilla = new byte[24];
        new SecureRandom().nextBytes(semilla);
        documento.enforceReadonlyProtection(
                Base64.getUrlEncoder().withoutPadding().encodeToString(semilla), HashAlgorithm.sha512);
    }

    // --------------------------------------------------------------- teoría

    private void renderTeoria(XWPFDocument documento, MaterialExportableDTO material) {
        List<BloqueMarkdown> bloques = MarkdownParser.analizar(material.teoria());
        if (bloques.isEmpty()) {
            throw new IllegalArgumentException("No hay teoría para exportar");
        }
        bloques.forEach(bloque -> renderBloque(documento, bloque));
    }

    private void renderBloque(XWPFDocument documento, BloqueMarkdown bloque) {
        switch (bloque) {
            case Titulo titulo -> renderTitulo(documento, titulo);
            case Parrafo parrafo -> {
                XWPFParagraph p = nuevoParrafo(documento, 0, 120);
                escribirFragmentos(p, parrafo.fragmentos(), 11, TINTA);
            }
            case Elemento elemento -> {
                XWPFParagraph p = nuevoParrafo(documento, 360 * (elemento.nivel() + 1), 40);
                XWPFRun marcador = run(p, 11, TINTA);
                marcador.setText(elemento.marcador() + "  ");
                escribirFragmentos(p, elemento.fragmentos(), 11, TINTA);
            }
            case Codigo codigo -> renderCodigo(documento, codigo);
            case Cita cita -> {
                XWPFParagraph p = nuevoParrafo(documento, 360, 120);
                p.setBorderLeft(Borders.SINGLE);
                escribirFragmentos(p, enCursiva(cita.fragmentos()), 10.5f, GRIS);
            }
            case Regla ignored -> nuevoParrafo(documento, 0, 60).setBorderBottom(Borders.SINGLE);
            case Tabla tabla -> renderTabla(documento, tabla);
        }
    }

    private void renderTitulo(XWPFDocument documento, Titulo titulo) {
        int tam = switch (titulo.nivel()) {
            case 1 -> 18;
            case 2 -> 15;
            default -> 13;
        };
        XWPFParagraph parrafo = nuevoParrafo(documento, 0, 120);
        parrafo.setSpacingBefore(240);
        if (titulo.nivel() <= 2) {
            parrafo.setBorderBottom(Borders.SINGLE);
        }
        escribirFragmentos(parrafo, enNegrita(titulo.fragmentos()), tam, TINTA, FUENTE_TITULO);
    }

    private void renderCodigo(XWPFDocument documento, Codigo codigo) {
        for (String linea : codigo.texto().stripTrailing().split("\n")) {
            XWPFParagraph parrafo = nuevoParrafo(documento, 180, 0);
            parrafo.getCTP().addNewPPr().addNewShd().setFill(FONDO_CODIGO);
            XWPFRun run = run(parrafo, 9.5f, TINTA);
            run.setFontFamily(FUENTE_MONO);
            run.setText(TextoSeguro.normalizar(linea));
        }
    }

    private void renderTabla(XWPFDocument documento, Tabla tabla) {
        List<List<String>> filas = tabla.filas();
        int columnas = filas.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable tablaWord = documento.createTable(filas.size(), columnas);
        tablaWord.setWidth("100%");

        for (int f = 0; f < filas.size(); f++) {
            XWPFTableRow fila = tablaWord.getRow(f);
            for (int c = 0; c < columnas; c++) {
                String valor = c < filas.get(f).size() ? filas.get(f).get(c) : "";
                XWPFParagraph parrafo = fila.getCell(c).getParagraphs().get(0);
                XWPFRun run = run(parrafo, 10, TINTA);
                run.setBold(f == 0);
                run.setText(TextoSeguro.normalizar(valor));
                if (f == 0) {
                    fila.getCell(c).setColor(FONDO_CODIGO);
                }
            }
        }
        nuevoParrafo(documento, 0, 120);
    }

    // ----------------------------------------------------------- evaluación

    private void renderEvaluacion(XWPFDocument documento, MaterialExportableDTO material) {
        List<EvaluacionPreguntaDTO> preguntas = material.evaluacion();
        if (preguntas == null || preguntas.isEmpty()) {
            throw new IllegalArgumentException("No hay evaluación para exportar");
        }

        renderTitulo(documento, new Titulo(1, List.of(Fragmento.plano("Evaluación"))));
        XWPFParagraph intro = nuevoParrafo(documento, 0, 200);
        run(intro, 10, GRIS).setText(preguntas.size() + " preguntas de opción múltiple");

        for (int i = 0; i < preguntas.size(); i++) {
            renderPregunta(documento, i, preguntas.get(i));
        }

        // The answer key starts on its own page so the questions can be handed out on their own.
        XWPFParagraph saltoPagina = documento.createParagraph();
        saltoPagina.createRun().addBreak(BreakType.PAGE);

        renderTitulo(documento, new Titulo(1, List.of(Fragmento.plano("Clave de respuestas"))));
        for (int i = 0; i < preguntas.size(); i++) {
            renderRespuesta(documento, i, preguntas.get(i));
        }
    }

    private void renderPregunta(XWPFDocument documento, int indice, EvaluacionPreguntaDTO pregunta) {
        XWPFParagraph etiqueta = nuevoParrafo(documento, 0, 40);
        XWPFRun runEtiqueta = run(etiqueta, 9, TINTA);
        runEtiqueta.setBold(true);
        runEtiqueta.setText("Pregunta " + (indice + 1));

        XWPFParagraph enunciado = nuevoParrafo(documento, 0, 60);
        run(enunciado, 11, TINTA).setText(TextoSeguro.normalizar(pregunta.pregunta()));

        List<String> opciones = pregunta.opciones() == null ? List.of() : pregunta.opciones();
        for (int i = 0; i < opciones.size(); i++) {
            XWPFParagraph opcion = nuevoParrafo(documento, 360, 20);
            run(opcion, 10.5f, TINTA).setText(letra(i) + ") " + TextoSeguro.normalizar(opciones.get(i)));
        }
        nuevoParrafo(documento, 0, 160);
    }

    private void renderRespuesta(XWPFDocument documento, int indice, EvaluacionPreguntaDTO pregunta) {
        XWPFParagraph respuesta = nuevoParrafo(documento, 0, 20);
        XWPFRun run = run(respuesta, 11, TINTA);
        run.setBold(true);
        run.setText((indice + 1) + ". " + letraCorrecta(pregunta));

        if (pregunta.explicacion() != null && !pregunta.explicacion().isBlank()) {
            XWPFParagraph explicacion = nuevoParrafo(documento, 360, 120);
            XWPFRun runExplicacion = run(explicacion, 10, GRIS);
            runExplicacion.setItalic(true);
            runExplicacion.setText(TextoSeguro.normalizar(pregunta.explicacion()));
        }
    }

    // ----------------------------------------------------------- utilidades

    private XWPFParagraph nuevoParrafo(XWPFDocument documento, int sangria, int espacioDespues) {
        XWPFParagraph parrafo = documento.createParagraph();
        parrafo.setIndentationLeft(sangria);
        parrafo.setSpacingAfter(espacioDespues);
        return parrafo;
    }

    private XWPFRun run(XWPFParagraph parrafo, float tam, String color) {
        return run(parrafo, tam, color, FUENTE_CUERPO);
    }

    private XWPFRun run(XWPFParagraph parrafo, float tam, String color, String fuente) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily(fuente);
        run.setFontSize(tam);
        run.setColor(color);
        return run;
    }

    /** One Word run per styled fragment, so bold/italic/code survive the markdown round trip. */
    private void escribirFragmentos(XWPFParagraph parrafo, List<Fragmento> fragmentos, float tam, String color) {
        escribirFragmentos(parrafo, fragmentos, tam, color, FUENTE_CUERPO);
    }

    private void escribirFragmentos(XWPFParagraph parrafo, List<Fragmento> fragmentos, float tam, String color, String fuente) {
        for (Fragmento fragmento : fragmentos) {
            XWPFRun run = run(parrafo, tam, color, fuente);
            run.setBold(fragmento.negrita());
            run.setItalic(fragmento.cursiva());
            if (fragmento.codigo()) {
                run.setFontFamily(FUENTE_MONO);
            }
            run.setText(TextoSeguro.normalizar(fragmento.texto()));
        }
    }

    private static List<Fragmento> enNegrita(List<Fragmento> fragmentos) {
        return fragmentos.stream()
                .map(f -> new Fragmento(f.texto(), true, f.cursiva(), f.codigo()))
                .toList();
    }

    private static List<Fragmento> enCursiva(List<Fragmento> fragmentos) {
        return fragmentos.stream()
                .map(f -> new Fragmento(f.texto(), f.negrita(), true, f.codigo()))
                .toList();
    }

    private static String letra(int indice) {
        return String.valueOf((char) ('A' + indice));
    }

    private static String letraCorrecta(EvaluacionPreguntaDTO pregunta) {
        Integer indice = pregunta.opcionCorrectaIndex();
        if (indice == null || pregunta.opciones() == null || indice >= pregunta.opciones().size()) {
            return "-";
        }
        return letra(indice) + ") " + pregunta.opciones().get(indice);
    }
}
