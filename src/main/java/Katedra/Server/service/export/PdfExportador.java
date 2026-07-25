package Katedra.Server.service.export;

import Katedra.Server.dto.DiapositivaDTO;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * PDF export built directly on PDFBox: content first, then {@link PdfBranding} stamps the header
 * and footer on every page and locks the document against modification.
 *
 * <p>The diapositivas layout is a deliberate 1:1 port of the slide carousel rendered in
 * {@code Generator.jsx} (and, after this change, {@code ContentViewer.jsx}) — same gradient
 * accent bar, same colours, same font roles — so the exported deck reads as the same design the
 * teacher already previewed on screen, not a reinterpretation of it.
 */
@Component
public class PdfExportador implements ExportadorMaterial {

    private static final float MARGEN = 56f;
    /** Leaves the header band (materia + temario + rule) clear of the first line of content. */
    private static final float TOPE_SUPERIOR = 106f;
    private static final float TOPE_INFERIOR = 72f;

    private static final Color TINTA = new Color(0x0F, 0x17, 0x2A);
    private static final Color TEXTO = new Color(0x33, 0x41, 0x55);
    private static final Color GRIS = new Color(0x64, 0x74, 0x8B);
    private static final Color LINEA = new Color(0xE2, 0xE8, 0xF0);
    private static final Color FONDO_CODIGO = new Color(0xF1, 0xF5, 0xF9);

    // Slide palette — a 1:1 port of the per-subject sidebar carousel in ContentViewer.jsx /
    // Generator.jsx (asignaturaVisual.js's SUBJECT_COLORS, cycled by slide index).
    private static final Color[] SUBJECT_COLORS = {
            new Color(0x10, 0xB9, 0x81), new Color(0x25, 0x63, 0xEB), new Color(0xF5, 0x9E, 0x0B),
            new Color(0xF4, 0x3F, 0x5E), new Color(0x06, 0xB6, 0xD4), new Color(0x7C, 0x3A, 0xED),
            new Color(0xEA, 0x58, 0x0C), new Color(0x2B, 0x6C, 0xB0), new Color(0xDC, 0x26, 0x26),
            new Color(0x0D, 0x94, 0x88),
    };
    private static final Color FONDO_OSCURO = new Color(0x0F, 0x17, 0x2A);
    private static final Color TEXTO_DARK = new Color(0xE2, 0xE8, 0xF0);
    private static final Color TITULO_DARK = new Color(0xF8, 0xFA, 0xFC);
    private static final Color MUTED_DARK = new Color(0x94, 0xA3, 0xB8);
    private static final Color MUTED_SIDEBAR_LIGHT = new Color(0xF1, 0xF5, 0xF9); // ~= rgba(255,255,255,.8) on the sidebar wash
    private static final Color BLANCO = Color.WHITE;

    /** Same 0.78 multiplier as asignaturaVisual.js's darkenHex, so the gradient matches on screen. */
    private static Color oscurecer(Color color) {
        return new Color(
                Math.max(0, Math.round(color.getRed() * 0.78f)),
                Math.max(0, Math.round(color.getGreen() * 0.78f)),
                Math.max(0, Math.round(color.getBlue() * 0.78f)));
    }

    /** Exact PowerPoint 16:9, so the PDF and PPTX slide exports match page for slide. */
    private static final PDRectangle DIAPOSITIVA = new PDRectangle(960f, 540f);

    private final PdfFuentes fuentes;

    public PdfExportador(PdfFuentes fuentes) {
        this.fuentes = fuentes;
    }

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.PDF;
    }

    @Override
    public byte[] exportar(MaterialExportableDTO material) throws IOException {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            PdfFuentes.Juego juego = fuentes.cargar(documento);
            boolean compacto = false;

            switch (material.pieza()) {
                case TEORIA -> renderTeoria(documento, juego, material);
                case EVALUACION -> renderEvaluacion(documento, juego, material);
                case DIAPOSITIVAS -> {
                    renderDiapositivas(documento, juego, material);
                    compacto = true;
                }
                default -> throw new IllegalArgumentException(
                        "PDF no está disponible para " + material.pieza().getValor());
            }

            PdfBranding.estampar(documento, material.materia(), material.temarioTitulo(), juego, compacto);
            PdfBranding.describir(documento, material.materia(), material.temarioTitulo());
            PdfBranding.proteger(documento);

            documento.save(salida);
            return salida.toByteArray();
        }
    }

    // ---------------------------------------------------------------- teoría

    private void renderTeoria(PDDocument documento, PdfFuentes.Juego juego, MaterialExportableDTO material)
            throws IOException {
        List<BloqueMarkdown> bloques = MarkdownParser.analizar(material.teoria());
        if (bloques.isEmpty()) {
            throw new IllegalArgumentException("No hay teoría para exportar");
        }

        try (PdfLayout layout = new PdfLayout(documento, PDRectangle.A4, juego, MARGEN, TOPE_SUPERIOR, TOPE_INFERIOR)) {
            for (BloqueMarkdown bloque : bloques) {
                renderBloque(layout, juego, bloque);
            }
        }
    }

    private void renderBloque(PdfLayout layout, PdfFuentes.Juego juego, BloqueMarkdown bloque) throws IOException {
        switch (bloque) {
            case Titulo titulo -> renderTitulo(layout, juego, titulo);
            case Parrafo parrafo -> {
                layout.parrafo(parrafo.fragmentos(), 11f, 15.5f, 0f, TEXTO);
                layout.espacio(9f);
            }
            case Elemento elemento -> {
                float sangria = 14f + (elemento.nivel() * 18f);
                layout.elemento(elemento.marcador(), elemento.fragmentos(), 11f, 15f, sangria, TEXTO);
                layout.espacio(3f);
            }
            case Codigo codigo -> renderCodigo(layout, codigo);
            case Cita cita -> {
                layout.espacio(6f);
                layout.parrafo(enCursiva(cita.fragmentos()), 10.5f, 15f, 18f, GRIS);
                layout.espacio(12f);
            }
            case Regla ignored -> {
                layout.espacio(6f);
                layout.regla(LINEA, 0.6f);
                layout.espacio(6f);
            }
            case Tabla tabla -> renderTabla(layout, tabla);
        }
    }

    private void renderTitulo(PdfLayout layout, PdfFuentes.Juego juego, Titulo titulo) throws IOException {
        float tam = switch (titulo.nivel()) {
            case 1 -> 20f;
            case 2 -> 16f;
            default -> 13.5f;
        };
        // Keep a heading with at least its first body line, so it never orphans at the page foot.
        layout.asegurarEspacio(tam * 3f);
        layout.espacio(titulo.nivel() <= 2 ? 14f : 10f);
        layout.parrafoConFuente(titulo.fragmentos(), juego.titulo(titulo.nivel()), tam, tam * 1.32f, TINTA);

        if (titulo.nivel() <= 2) {
            layout.espacio(2f);
            layout.regla(LINEA, 0.6f);
        }
        layout.espacio(8f);
    }

    private void renderCodigo(PdfLayout layout, Codigo codigo) throws IOException {
        String[] lineas = codigo.texto().stripTrailing().split("\n");
        float interlineado = 13f;
        float alto = (lineas.length * interlineado) + 12f;

        layout.asegurarEspacio(alto);
        layout.rectangulo(layout.margen() - 6f, layout.getY() - alto + interlineado,
                layout.anchoDisponible(0) + 12f, alto, FONDO_CODIGO);

        for (String linea : lineas) {
            layout.parrafo(List.of(new Fragmento(linea, false, false, true)), 9.5f, interlineado, 2f, TINTA);
        }
        layout.espacio(12f);
    }

    private void renderTabla(PdfLayout layout, Tabla tabla) throws IOException {
        int columnas = tabla.filas().stream().mapToInt(List::size).max().orElse(1);
        float anchoColumna = layout.anchoDisponible(0) / columnas;

        for (int f = 0; f < tabla.filas().size(); f++) {
            List<String> fila = tabla.filas().get(f);
            boolean cabecera = f == 0;
            layout.asegurarEspacio(15f);
            float yFila = layout.getY();

            for (int c = 0; c < fila.size(); c++) {
                layout.setY(yFila);
                layout.parrafo(List.of(new Fragmento(fila.get(c), cabecera, false, false)),
                        9.5f, 13f, c * anchoColumna, cabecera ? TINTA : TEXTO);
            }
            layout.setY(yFila - 15f);
            layout.regla(LINEA, 0.4f);
        }
        layout.espacio(10f);
    }

    // ------------------------------------------------------------ evaluación

    private void renderEvaluacion(PDDocument documento, PdfFuentes.Juego juego, MaterialExportableDTO material)
            throws IOException {
        List<EvaluacionPreguntaDTO> preguntas = material.evaluacion();
        if (preguntas == null || preguntas.isEmpty()) {
            throw new IllegalArgumentException("No hay evaluación para exportar");
        }

        try (PdfLayout layout = new PdfLayout(documento, PDRectangle.A4, juego, MARGEN, TOPE_SUPERIOR, TOPE_INFERIOR)) {
            layout.espacio(6f);
            layout.parrafoConFuente(planos("Evaluación"), juego.titulo(1), 20f, 26f, TINTA);
            layout.espacio(2f);
            layout.regla(LINEA, 0.6f);
            layout.espacio(6f);
            layout.texto(preguntas.size() + " preguntas de opción múltiple", 10f, 14f, 0f, GRIS, false, false);
            layout.espacio(14f);

            for (int i = 0; i < preguntas.size(); i++) {
                renderPregunta(layout, juego, i, preguntas.get(i));
            }

            layout.nuevaPagina();
            layout.espacio(6f);
            layout.parrafoConFuente(planos("Clave de respuestas"), juego.titulo(1), 20f, 26f, TINTA);
            layout.espacio(2f);
            layout.regla(LINEA, 0.6f);
            layout.espacio(10f);

            for (int i = 0; i < preguntas.size(); i++) {
                renderRespuesta(layout, i, preguntas.get(i));
            }
        }
    }

    private void renderPregunta(PdfLayout layout, PdfFuentes.Juego juego, int indice, EvaluacionPreguntaDTO pregunta)
            throws IOException {
        List<String> opciones = pregunta.opciones() == null ? List.of() : pregunta.opciones();
        // Reserve the whole question so it is never split across two pages.
        layout.asegurarEspacio(46f + (opciones.size() * 15f));

        layout.textoConFuente("Pregunta " + (indice + 1), juego.manropeExtraBold(), 9f, 12f, 0f, TINTA);
        layout.espacio(2f);
        layout.parrafo(planos(pregunta.pregunta()), 11f, 15f, 0f, TINTA);
        layout.espacio(4f);

        for (int i = 0; i < opciones.size(); i++) {
            layout.texto(letra(i) + ") " + opciones.get(i), 10.5f, 15f, 18f, TEXTO, false, false);
        }
        layout.espacio(16f);
    }

    private void renderRespuesta(PdfLayout layout, int indice, EvaluacionPreguntaDTO pregunta) throws IOException {
        layout.asegurarEspacio(40f);
        layout.texto((indice + 1) + ". " + letraCorrecta(pregunta), 11f, 15f, 0f, TINTA, true, false);

        if (pregunta.explicacion() != null && !pregunta.explicacion().isBlank()) {
            layout.parrafo(enCursiva(planos(pregunta.explicacion())), 10f, 14f, 18f, GRIS);
        }
        layout.espacio(8f);
    }

    // ---------------------------------------------------------- diapositivas

    private void renderDiapositivas(PDDocument documento, PdfFuentes.Juego juego, MaterialExportableDTO material)
            throws IOException {
        List<DiapositivaDTO> diapositivas = material.diapositivas();
        if (diapositivas == null || diapositivas.isEmpty()) {
            throw new IllegalArgumentException("No hay diapositivas para exportar");
        }

        boolean oscuro = "dark".equalsIgnoreCase(material.theme());
        Color colorTituloContenido = oscuro ? TITULO_DARK : TINTA;
        Color colorTextoContenido = oscuro ? TEXTO_DARK : TEXTO;

        try (PdfLayout layout = new PdfLayout(documento, DIAPOSITIVA, juego, 0f, 0f, 0f)) {
            for (int i = 0; i < diapositivas.size(); i++) {
                if (i > 0) {
                    layout.nuevaPagina();
                }

                Color colorTema = SUBJECT_COLORS[i % SUBJECT_COLORS.length];
                Color colorOscuro = oscurecer(colorTema);

                layout.rectangulo(0f, 0f, DIAPOSITIVA.getWidth(), DIAPOSITIVA.getHeight(), oscuro ? FONDO_OSCURO : BLANCO);

                if (i == 0) {
                    renderPortada(layout, juego, material, diapositivas.get(0), colorTema, oscuro);
                } else {
                    renderLamina(layout, juego, material, i, diapositivas.get(i), colorTema, colorOscuro, oscuro,
                            colorTituloContenido, colorTextoContenido);
                }
                // No footer draw here: PdfBranding's second pass already stamps "© Katedra, 2026"
                // (plus the page count) on every page, slides included — drawing it here too
                // duplicated the line.
            }
        }
    }

    /** Left-aligned, vertically centered cover slide — mirrors ContentViewer.jsx's {@code idx === 0} branch. */
    private void renderPortada(PdfLayout layout, PdfFuentes.Juego juego, MaterialExportableDTO material,
                               DiapositivaDTO portada, Color colorTema, boolean oscuro) throws IOException {
        List<String> puntos = portada.puntos() == null ? List.of() : portada.puntos();
        boolean tieneSubtitulo = portada.titulo() != null && !portada.titulo().equals(material.temarioTitulo());
        float margenX = 96f; // 10% of the 960pt slide width, matching the app's `padding:'8% 10%'`

        Color colorTitulo = oscuro ? BLANCO : TINTA;
        Color colorSubtitulo = oscuro ? MUTED_DARK : new Color(0x47, 0x55, 0x69);

        // Rough estimate of the block's height (accent bar + eyebrow + title + optional subtitle/points),
        // so the whole group sits vertically centered the way flex `justify-content:center` does.
        // Gaps between elements account for the NEXT element's ascent (PDF y advances by baseline,
        // not by visual line box), so a 48pt title needs a much bigger gap after a 15pt eyebrow
        // than the eyebrow's own line height would suggest.
        float alto = 30f + 20f + 34f + 50f + 22f
                + (tieneSubtitulo ? 32f + 18f : 0f)
                + (puntos.isEmpty() ? 0f : 20f);
        layout.setY((DIAPOSITIVA.getHeight() / 2f) + (alto / 2f));

        layout.rectangulo(margenX, layout.getY() - 6f, 80f, 6f, colorTema);
        layout.espacio(30f);

        layout.textoConFuente(MarcaDocumento.lineaMateria(material.materia()), juego.manropeExtraBold(),
                15f, 20f, margenX, colorTema);
        layout.espacio(34f);

        layout.parrafoConFuente(planos(material.temarioTitulo()), juego.titulo(1), 48f, 52f, margenX, colorTitulo);
        layout.espacio(22f);

        if (tieneSubtitulo) {
            layout.textoConFuente(portada.titulo(), juego.interSemibold(), 24f, 32f, margenX, colorSubtitulo);
            layout.espacio(18f);
        }

        if (!puntos.isEmpty()) {
            layout.textoConFuente(String.join("  ·  ", puntos.subList(0, Math.min(2, puntos.size()))),
                    juego.manropeBold(), 14f, 20f, margenX, colorSubtitulo);
        }
    }

    /**
     * Two-column content slide — mirrors ContentViewer.jsx's {@code idx > 0} branch: a 32%-wide
     * coloured sidebar (slide number + materia + temario) and a content column (title + bullets).
     */
    private void renderLamina(PdfLayout layout, PdfFuentes.Juego juego, MaterialExportableDTO material, int indice,
                              DiapositivaDTO diapositiva, Color colorTema, Color colorOscuro, boolean oscuro,
                              Color colorTitulo, Color colorTexto) throws IOException {
        float anchoSidebar = DIAPOSITIVA.getWidth() * 0.32f; // 307.2pt
        float padSidebarX = DIAPOSITIVA.getWidth() * 0.04f;  // 38.4pt
        float padSidebarY = DIAPOSITIVA.getHeight() * 0.06f; // 32.4pt
        float padContenidoTop = DIAPOSITIVA.getHeight() * 0.06f; // 32.4pt
        float padContenidoLeft = DIAPOSITIVA.getWidth() * 0.05f; // 48pt
        float padContenidoRight = DIAPOSITIVA.getWidth() * 0.06f; // 57.6pt
        float xContenido = anchoSidebar + padContenidoLeft;
        float anchoContenido = DIAPOSITIVA.getWidth() - xContenido - padContenidoRight;

        // Sidebar wash — top-to-bottom, same stop order as the app's `linear-gradient(180deg, ...)`.
        Color arriba = oscuro ? colorOscuro : colorTema;
        Color abajo = oscuro ? FONDO_OSCURO : colorOscuro;
        layout.barraGradienteVertical(0f, 0f, anchoSidebar, DIAPOSITIVA.getHeight(), arriba, abajo);

        // --- sidebar column ---
        // setY() places a BASELINE, not a box top: the padding must clear the glyph's cap-height
        // above that baseline too, or the text sits flush against the slide's top edge. ~0.78x the
        // font size approximates a bold sans-serif's cap-height across our embedded faces.
        layout.setY(DIAPOSITIVA.getHeight() - padSidebarY - (42f * 0.78f));
        Color colorNumero = oscuro ? colorTema : BLANCO;
        layout.textoConFuente(String.format("%02d", indice + 1), juego.titulo(1), 42f, 46f,
                padSidebarX, anchoSidebar - (2 * padSidebarX), colorNumero);
        layout.espacio(16f);
        layout.textoConFuente(MarcaDocumento.lineaMateria(material.materia()), juego.manropeBold(),
                12f, 18f, padSidebarX, anchoSidebar - (2 * padSidebarX), BLANCO);
        layout.espacio(12f);
        Color colorTemarioSidebar = oscuro ? MUTED_DARK : MUTED_SIDEBAR_LIGHT;
        layout.textoConFuente(material.temarioTitulo(), juego.manropeMedium(), 14f, 20f,
                padSidebarX, anchoSidebar - (2 * padSidebarX), colorTemarioSidebar);

        // --- content column ---
        layout.setY(DIAPOSITIVA.getHeight() - padContenidoTop - (34f * 0.78f));
        layout.parrafoConFuente(planos(diapositiva.titulo()), juego.titulo(2), 34f, 38f,
                xContenido, anchoContenido, colorTitulo);
        layout.espacio(20f);

        List<String> puntos = diapositiva.puntos() == null ? List.of() : diapositiva.puntos();
        for (String punto : puntos) {
            layout.elementoConMarcadorVectorial(planos(punto), 17f, 25f, xContenido, anchoContenido, colorTexto,
                    colorTema);
            layout.espacio(6f);
        }
    }

    // ------------------------------------------------------------- utilidades

    private static List<Fragmento> planos(String texto) {
        return List.of(Fragmento.plano(texto == null ? "" : texto));
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
