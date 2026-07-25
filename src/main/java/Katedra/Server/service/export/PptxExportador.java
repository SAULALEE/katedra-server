package Katedra.Server.service.export;

import Katedra.Server.dto.DiapositivaDTO;
import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;
import Katedra.Server.model.PiezaMaterial;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGradientFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGradientStop;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGradientStopList;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * PowerPoint export built with POI's XSLF API.
 *
 * <p>The slide layout is a deliberate 1:1 port of the carousel rendered in {@code Generator.jsx}
 * (and, after this change, {@code ContentViewer.jsx}) — same gradient accent bar, same colours,
 * same font roles — so the exported deck reads as the same design the teacher already previewed
 * on screen.
 *
 * <p>POI exposes no way to write a modify-password for a presentation, so the deck ships editable
 * on purpose — teachers adapt slides. What travels with it is the "© Katedra, 2026" footer on every
 * slide plus authorship in the file properties; the protected artifact for handouts is the slide PDF.
 */
@Component
public class PptxExportador implements ExportadorMaterial {

    /** 960x540 pt = 13.33in x 7.5in, PowerPoint's true 16:9. */
    private static final Dimension TAMANO = new Dimension(960, 540);

    private static final String FUENTE_TITULO = "Inter";
    private static final String FUENTE_CUERPO = "Manrope";

    private static final Color TINTA = new Color(0x0F, 0x17, 0x2A);
    private static final Color TEXTO = new Color(0x33, 0x41, 0x55);
    private static final Color BLANCO = new Color(0xFF, 0xFF, 0xFF);

    // Slide palette — a 1:1 port of the per-subject sidebar carousel in ContentViewer.jsx /
    // Generator.jsx (asignaturaVisual.js's SUBJECT_COLORS, cycled by slide index).
    private static final Color[] SUBJECT_COLORS = {
            new Color(0x10, 0xB9, 0x81), new Color(0x25, 0x63, 0xEB), new Color(0xF5, 0x9E, 0x0B),
            new Color(0xF4, 0x3F, 0x5E), new Color(0x06, 0xB6, 0xD4), new Color(0x7C, 0x3A, 0xED),
            new Color(0xEA, 0x58, 0x0C), new Color(0x2B, 0x6C, 0xB0), new Color(0xDC, 0x26, 0x26),
            new Color(0x0D, 0x94, 0x88),
    };
    private static final Color FONDO_OSCURO = TINTA;
    private static final Color TEXTO_DARK = new Color(0xE2, 0xE8, 0xF0);
    private static final Color TITULO_DARK = new Color(0xF8, 0xFA, 0xFC);
    private static final Color MUTED_DARK = new Color(0x94, 0xA3, 0xB8);
    private static final Color MUTED_SIDEBAR_LIGHT = new Color(0xF1, 0xF5, 0xF9); // ~= rgba(255,255,255,.8) on the sidebar wash

    /** Same 0.78 multiplier as asignaturaVisual.js's darkenHex, so the gradient matches on screen. */
    private static Color oscurecer(Color color) {
        return new Color(
                Math.max(0, Math.round(color.getRed() * 0.78f)),
                Math.max(0, Math.round(color.getGreen() * 0.78f)),
                Math.max(0, Math.round(color.getBlue() * 0.78f)));
    }

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.PPTX;
    }

    @Override
    public byte[] exportar(MaterialExportableDTO material) throws IOException {
        if (material.pieza() != PiezaMaterial.DIAPOSITIVAS) {
            throw new IllegalArgumentException(
                    "PPTX no está disponible para " + material.pieza().getValor());
        }
        List<DiapositivaDTO> diapositivas = material.diapositivas();
        if (diapositivas == null || diapositivas.isEmpty()) {
            throw new IllegalArgumentException("No hay diapositivas para exportar");
        }

        boolean oscuro = "dark".equalsIgnoreCase(material.theme());
        Color fondo = oscuro ? FONDO_OSCURO : BLANCO;
        Color colorTituloContenido = oscuro ? TITULO_DARK : TINTA;
        Color colorTextoContenido = oscuro ? TEXTO_DARK : TEXTO;

        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            ppt.setPageSize(TAMANO);

            for (int i = 0; i < diapositivas.size(); i++) {
                XSLFSlide slide = ppt.createSlide();
                Color colorTema = SUBJECT_COLORS[i % SUBJECT_COLORS.length];
                Color colorOscuro = oscurecer(colorTema);

                XSLFAutoShape fondoForma = slide.createAutoShape();
                fondoForma.setAnchor(new Rectangle(0, 0, TAMANO.width, TAMANO.height));
                fondoForma.setFillColor(fondo);
                fondoForma.setLineColor(fondo);

                if (i == 0) {
                    portada(slide, material, diapositivas.get(0), colorTema, oscuro);
                } else {
                    lamina(slide, material, i, diapositivas.get(i), colorTema, colorOscuro, oscuro,
                            colorTituloContenido, colorTextoContenido);
                }
                Color colorMarcaAgua = oscuro ? new Color(0x64, 0x74, 0x8B) : new Color(0x94, 0xA3, 0xB8);
                pie(slide, colorMarcaAgua);
            }

            describir(ppt, material);
            ppt.write(salida);
            return salida.toByteArray();
        }
    }

    /** Left-aligned, vertically centered cover slide — mirrors ContentViewer.jsx's {@code idx === 0} branch. */
    private void portada(XSLFSlide slide, MaterialExportableDTO material, DiapositivaDTO portada,
                         Color colorTema, boolean oscuro) {
        boolean tieneSubtitulo = portada.titulo() != null && !portada.titulo().equals(material.temarioTitulo());
        List<String> puntos = portada.puntos() == null ? List.of() : portada.puntos();
        int margenX = 96; // 10% of the 960pt slide width, matching the app's `padding:'8% 10%'`
        int ancho = TAMANO.width - (2 * margenX);

        Color colorTitulo = oscuro ? BLANCO : TINTA;
        Color colorSubtitulo = oscuro ? MUTED_DARK : new Color(0x47, 0x55, 0x69);

        XSLFAutoShape barra = slide.createAutoShape();
        barra.setAnchor(new Rectangle(margenX, 190, 80, 6));
        barra.setFillColor(colorTema);
        barra.setLineColor(colorTema);

        caja(slide, new Rectangle(margenX, 212, ancho, 26),
                MarcaDocumento.lineaMateria(material.materia()), FUENTE_CUERPO, 15, true, colorTema);

        caja(slide, new Rectangle(margenX, 244, ancho, 110),
                material.temarioTitulo(), FUENTE_TITULO, 44, true, colorTitulo);

        int y = 352;
        if (tieneSubtitulo) {
            caja(slide, new Rectangle(margenX, y, ancho, 40), portada.titulo(), FUENTE_TITULO, 22, false, colorSubtitulo);
            y += 40;
        }
        if (!puntos.isEmpty()) {
            caja(slide, new Rectangle(margenX, y, ancho, 30),
                    String.join("  ·  ", puntos.subList(0, Math.min(2, puntos.size()))),
                    FUENTE_CUERPO, 14, true, colorSubtitulo);
        }
    }

    /**
     * Two-column content slide — mirrors ContentViewer.jsx's {@code idx > 0} branch: a 32%-wide
     * coloured sidebar (slide number + materia + temario) and a content column (title + bullets).
     */
    private void lamina(XSLFSlide slide, MaterialExportableDTO material, int indice, DiapositivaDTO diapositiva,
                        Color colorTema, Color colorOscuro, boolean oscuro, Color colorTitulo, Color colorTexto) {
        int anchoSidebar = Math.round(TAMANO.width * 0.32f);   // 307pt
        int padSidebarX = Math.round(TAMANO.width * 0.04f);    // 38pt
        int padSidebarY = Math.round(TAMANO.height * 0.06f);   // 32pt
        int padContenidoTop = Math.round(TAMANO.height * 0.06f); // 32pt
        int padContenidoLeft = Math.round(TAMANO.width * 0.05f); // 48pt
        int padContenidoRight = Math.round(TAMANO.width * 0.06f); // 58pt
        int xContenido = anchoSidebar + padContenidoLeft;
        int anchoContenido = TAMANO.width - xContenido - padContenidoRight;
        int anchoSidebarTexto = anchoSidebar - (2 * padSidebarX);

        XSLFAutoShape sidebar = slide.createAutoShape();
        sidebar.setAnchor(new Rectangle(0, 0, anchoSidebar, TAMANO.height));
        Color arriba = oscuro ? colorOscuro : colorTema;
        Color abajo = oscuro ? FONDO_OSCURO : colorOscuro;
        aplicarGradienteVertical(sidebar, arriba, abajo);

        String numero = String.format("%02d", indice + 1);
        Color colorNumero = oscuro ? colorTema : BLANCO;
        caja(slide, new Rectangle(padSidebarX, padSidebarY, anchoSidebarTexto, 54),
                numero, FUENTE_TITULO, 42, true, colorNumero);

        caja(slide, new Rectangle(padSidebarX, padSidebarY + 66, anchoSidebarTexto, 22),
                MarcaDocumento.lineaMateria(material.materia()), FUENTE_CUERPO, 12, true, BLANCO);

        Color colorTemarioSidebar = oscuro ? MUTED_DARK : MUTED_SIDEBAR_LIGHT;
        caja(slide, new Rectangle(padSidebarX, padSidebarY + 92, anchoSidebarTexto, 80),
                material.temarioTitulo(), FUENTE_CUERPO, 14, true, colorTemarioSidebar);

        caja(slide, new Rectangle(xContenido, padContenidoTop, anchoContenido, 70),
                diapositiva.titulo(), FUENTE_TITULO, 34, true, colorTitulo);

        List<String> puntos = diapositiva.puntos() == null ? List.of() : diapositiva.puntos();
        if (puntos.isEmpty()) {
            return;
        }

        XSLFTextBox cuerpo = slide.createTextBox();
        cuerpo.setAnchor(new Rectangle(xContenido, padContenidoTop + 90, anchoContenido,
                TAMANO.height - padContenidoTop - 90 - 40));
        cuerpo.setWordWrap(true);
        cuerpo.setTextAutofit(TextShape.TextAutofit.NORMAL);
        cuerpo.clearText();

        // Denser decks step the type down so long bullets keep fitting on the slide.
        double tam = puntos.size() > 6 ? 15 : 17;
        for (String punto : puntos) {
            XSLFTextParagraph parrafo = cuerpo.addNewTextParagraph();
            parrafo.setBullet(true);
            parrafo.setBulletFontColor(colorTema);
            parrafo.setBulletCharacter("✓");
            parrafo.setSpaceAfter(130d);
            XSLFTextRun run = parrafo.addNewTextRun();
            run.setFontFamily(FUENTE_CUERPO);
            run.setText(TextoSeguro.normalizar(punto));
            run.setFontSize(tam);
            run.setFontColor(colorTexto);
        }
    }

    // The exact required copyright line — not the web preview's "Creado por Katedra" watermark
    // text, but styled the same way (Manrope, muted, centered) so it reads as part of the design.
    private void pie(XSLFSlide slide, Color colorMuted) {
        cajaCentrada(slide, new Rectangle(0, 500, TAMANO.width, 24), MarcaDocumento.PIE_PAGINA, FUENTE_CUERPO, 10, true, colorMuted);
    }

    private void caja(XSLFSlide slide, Rectangle posicion, String texto, String fuente, double tam, boolean negrita, Color color) {
        caja(slide, posicion, texto, fuente, tam, negrita, color, TextParagraph.TextAlign.LEFT);
    }

    private void cajaCentrada(XSLFSlide slide, Rectangle posicion, String texto, String fuente, double tam, boolean negrita, Color color) {
        caja(slide, posicion, texto, fuente, tam, negrita, color, TextParagraph.TextAlign.CENTER);
    }

    private void caja(XSLFSlide slide, Rectangle posicion, String texto, String fuente, double tam, boolean negrita,
                      Color color, TextParagraph.TextAlign alineacion) {
        XSLFTextBox caja = slide.createTextBox();
        caja.setAnchor(posicion);
        caja.setWordWrap(true);
        caja.clearText();

        XSLFTextParagraph parrafo = caja.addNewTextParagraph();
        parrafo.setTextAlign(alineacion);
        XSLFTextRun run = parrafo.addNewTextRun();
        run.setFontFamily(fuente);
        run.setText(TextoSeguro.normalizar(texto == null ? "" : texto));
        run.setFontSize(tam);
        run.setBold(negrita);
        run.setFontColor(color);
    }

    /**
     * A real top-to-bottom gradient fill via raw DrawingML — POI's high-level XSLF API has no
     * gradient setter, but the OOXML schema classes it already bundles do. Mirrors the app's
     * sidebar {@code linear-gradient(180deg, ...)}: 5400000 (90° in 1/60000ths) is PowerPoint's
     * "linear down" angle.
     */
    private void aplicarGradienteVertical(XSLFAutoShape forma, Color arriba, Color abajo) {
        CTShape ctShape = (CTShape) forma.getXmlObject();
        CTShapeProperties spPr = ctShape.getSpPr();
        if (spPr.isSetSolidFill()) {
            spPr.unsetSolidFill();
        }

        CTGradientFillProperties gradiente = spPr.addNewGradFill();
        CTGradientStopList paradas = gradiente.addNewGsLst();
        agregarParada(paradas, 0, arriba);
        agregarParada(paradas, 100000, abajo);
        gradiente.addNewLin().setAng(5400000);
    }

    private void agregarParada(CTGradientStopList paradas, int posicion, Color color) {
        CTGradientStop parada = paradas.addNewGs();
        parada.setPos(posicion);
        parada.addNewSrgbClr().setVal(new byte[] {(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()});
    }

    private void describir(XMLSlideShow ppt, MaterialExportableDTO material) {
        ppt.getProperties().getCoreProperties()
                .setTitle(MarcaDocumento.titulo(material.materia(), material.temarioTitulo()));
        ppt.getProperties().getCoreProperties().setCreator(MarcaDocumento.AUTOR);
        ppt.getProperties().getCoreProperties().setDescription(MarcaDocumento.PIE_PAGINA);
        ppt.getProperties().getCustomProperties().addProperty("Katedra-Copyright", MarcaDocumento.PIE_PAGINA);
    }
}
