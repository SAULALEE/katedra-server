package Katedra.Server.service.export;

import Katedra.Server.service.export.BloqueMarkdown.Fragmento;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;

import java.awt.Color;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal flow-layout engine over PDFBox: keeps a cursor, wraps text across mixed fonts, and
 * starts a new page when the cursor runs past the bottom margin.
 *
 * <p>Page breaks are decided per line, never mid-line, so text is never clipped or split.
 * Header and footer are NOT drawn here — {@link PdfBranding} stamps them in a second pass once the
 * total page count is known.
 */
class PdfLayout implements Closeable {

    /**
     * One word bound to the face and colour it must be drawn with.
     *
     * <p>{@code espacioAntes} records whether the source text actually had whitespace before this
     * word. Without it, styled runs would gain spaces that were never written: markdown like
     * {@code **Vectores**: conjuntos} would render as "Vectores : conjuntos".
     */
    private record Token(String texto, PDFont fuente, float tam, Color color, boolean espacioAntes) {}

    private final PDDocument documento;
    private final PDRectangle tamanoPagina;
    private final PdfFuentes.Juego fuentes;
    private final float margen;
    private final float yInicial;
    private final float yMinimo;

    private PDPageContentStream cs;
    private float y;

    PdfLayout(PDDocument documento, PDRectangle tamanoPagina, PdfFuentes.Juego fuentes,
              float margen, float topeSuperior, float topeInferior) throws IOException {
        this.documento = documento;
        this.tamanoPagina = tamanoPagina;
        this.fuentes = fuentes;
        this.margen = margen;
        this.yInicial = tamanoPagina.getHeight() - topeSuperior;
        this.yMinimo = topeInferior;
        nuevaPagina();
    }

    float getY() {
        return y;
    }

    void setY(float nuevaY) {
        this.y = nuevaY;
    }

    float anchoDisponible(float sangria) {
        return tamanoPagina.getWidth() - (2 * margen) - sangria;
    }

    final void nuevaPagina() throws IOException {
        if (cs != null) {
            cs.close();
        }
        PDPage pagina = new PDPage(tamanoPagina);
        documento.addPage(pagina);
        cs = new PDPageContentStream(documento, pagina);
        y = yInicial;
    }

    /** Starts a new page when {@code alto} would not fit above the bottom margin. */
    void asegurarEspacio(float alto) throws IOException {
        if (y - alto < yMinimo) {
            nuevaPagina();
        }
    }

    void espacio(float alto) {
        y -= alto;
    }

    /** Draws wrapped text. Returns the height consumed. */
    float parrafo(List<Fragmento> fragmentos, float tam, float interlineado, float sangria, Color color)
            throws IOException {
        return escribir(fragmentos, tam, interlineado, sangria, sangria, color, null, false, -1f);
    }

    /**
     * Draws a heading/eyebrow/title in an explicit face, bypassing the per-fragment style
     * resolution — a heading is a different font FAMILY in the app (Inter), not just a bold weight
     * of the body font, so it cannot go through {@code fuentes.para(...)}.
     */
    float parrafoConFuente(List<Fragmento> fragmentos, PDFont fuente, float tam, float interlineado, Color color)
            throws IOException {
        return parrafoConFuente(fragmentos, fuente, tam, interlineado, 0f, color);
    }

    float parrafoConFuente(List<Fragmento> fragmentos, PDFont fuente, float tam, float interlineado, float sangria, Color color)
            throws IOException {
        return escribir(fragmentos, tam, interlineado, sangria, sangria, color, fuente, false, -1f);
    }

    void textoConFuente(String contenido, PDFont fuente, float tam, float interlineado, float sangria, Color color)
            throws IOException {
        escribir(List.of(Fragmento.plano(contenido)), tam, interlineado, sangria, sangria, color, fuente, false, -1f);
    }

    /**
     * Same as {@link #parrafoConFuente(List, PDFont, float, float, float, Color)} but with an
     * explicit wrap width instead of "to the page edge" — needed for multi-column slide layouts
     * (e.g. a sidebar), where {@code margen} is 0 for the whole document and the default wrap
     * width would let text bleed across column boundaries into the next column.
     */
    float parrafoConFuente(List<Fragmento> fragmentos, PDFont fuente, float tam, float interlineado,
                           float sangria, float anchoMaximo, Color color) throws IOException {
        return escribir(fragmentos, tam, interlineado, sangria, sangria, color, fuente, false, anchoMaximo);
    }

    void textoConFuente(String contenido, PDFont fuente, float tam, float interlineado, float sangria,
                        float anchoMaximo, Color color) throws IOException {
        escribir(List.of(Fragmento.plano(contenido)), tam, interlineado, sangria, sangria, color, fuente, false, anchoMaximo);
    }

    /**
     * Centers each wrapped line independently across the full page width — the PDF equivalent of
     * CSS {@code text-align:center}, used by the slide cover (portada), which is centered both
     * ways in the app.
     */
    float parrafoConFuenteCentrado(List<Fragmento> fragmentos, PDFont fuente, float tam, float interlineado, Color color)
            throws IOException {
        return escribir(fragmentos, tam, interlineado, 0f, 0f, color, fuente, true, -1f);
    }

    void textoConFuenteCentrado(String contenido, PDFont fuente, float tam, float interlineado, Color color)
            throws IOException {
        escribir(List.of(Fragmento.plano(contenido)), tam, interlineado, 0f, 0f, color, fuente, true, -1f);
    }

    /** Draws a bullet/checkmark at {@code sangria} and hangs the wrapped text to its right. */
    void elemento(String marcador, List<Fragmento> fragmentos, float tam, float interlineado,
                  float sangria, Color color) throws IOException {
        float anchoMarcador = 20f;

        // Reserve the first line up front, then draw the marker at the current cursor: the text's
        // first line lands on that same baseline because escribir() finds the space already free.
        asegurarEspacio(interlineado);
        String marcadorSeguro = codificable(fuentes.regular(), TextoSeguro.normalizar(marcador));
        dibujarLinea(List.of(new Token(marcadorSeguro, fuentes.regular(), tam, color, false)), margen + sangria, y);

        escribir(fragmentos, tam, interlineado, sangria + anchoMarcador, sangria + anchoMarcador, color, null, false, -1f);
    }

    /**
     * A list item whose marker is a small filled square drawn as a shape, not a text glyph — used
     * for slide bullets, where the app shows a coloured check icon that no text font reliably has
     * a matching glyph for. Guarantees the marker can never fail to render regardless of font coverage.
     */
    void elementoConMarcadorVectorial(List<Fragmento> fragmentos, float tam, float interlineado,
                                       float sangria, Color colorTexto, Color colorMarcador) throws IOException {
        elementoConMarcadorVectorial(fragmentos, tam, interlineado, sangria, -1f, colorTexto, colorMarcador);
    }

    /** Same as above but confined to an explicit column width, for multi-column slide layouts. */
    void elementoConMarcadorVectorial(List<Fragmento> fragmentos, float tam, float interlineado,
                                       float sangria, float anchoMaximo, Color colorTexto, Color colorMarcador)
            throws IOException {
        float lado = tam * 0.4f;
        float anchoMarcador = 22f;

        asegurarEspacio(interlineado);
        // Centered on the text's x-height band (roughly baseline + 0.5*tam/2), not the baseline itself.
        rectangulo(margen + sangria, y + (tam * 0.15f), lado, lado, colorMarcador);

        float anchoColumna = anchoMaximo > 0f ? anchoMaximo - anchoMarcador : -1f;
        escribir(fragmentos, tam, interlineado, sangria + anchoMarcador, sangria + anchoMarcador, colorTexto, null,
                false, anchoColumna);
    }

    void texto(String contenido, float tam, float interlineado, float sangria, Color color,
               boolean negrita, boolean cursiva) throws IOException {
        escribir(List.of(new Fragmento(contenido, negrita, cursiva, false)),
                tam, interlineado, sangria, sangria, color, null, false, -1f);
    }

    /** Horizontal rule across the content width. */
    void regla(Color color, float grosor) throws IOException {
        asegurarEspacio(grosor + 2);
        cs.setStrokingColor(color);
        cs.setLineWidth(grosor);
        cs.moveTo(margen, y);
        cs.lineTo(tamanoPagina.getWidth() - margen, y);
        cs.stroke();
        y -= grosor + 2;
    }

    void rectangulo(float x, float yInferior, float ancho, float alto, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, yInferior, ancho, alto);
        cs.fill();
    }

    /**
     * A real left-to-right axial gradient fill (PDF shading, not two flat rectangles) — the app's
     * slide accent bars use CSS {@code linear-gradient}, and this is its PDF equivalent.
     */
    void barraGradiente(float x, float yInferior, float ancho, float alto, Color inicio, Color fin) throws IOException {
        COSDictionary funcionCos = new COSDictionary();
        funcionCos.setInt(COSName.FUNCTION_TYPE, 2);
        funcionCos.setItem(COSName.DOMAIN, arreglo(0f, 1f));
        funcionCos.setItem(COSName.C0, colorArreglo(inicio));
        funcionCos.setItem(COSName.C1, colorArreglo(fin));
        funcionCos.setFloat(COSName.N, 1f);

        PDShadingType2 shading = new PDShadingType2(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE2);
        shading.setColorSpace(PDDeviceRGB.INSTANCE);
        shading.setCoords(arreglo(x, yInferior + (alto / 2f), x + ancho, yInferior + (alto / 2f)));
        shading.setFunction(new PDFunctionType2(funcionCos));

        cs.saveGraphicsState();
        cs.addRect(x, yInferior, ancho, alto);
        cs.clip();
        cs.shadingFill(shading);
        cs.restoreGraphicsState();
    }

    /**
     * Top-to-bottom axial gradient fill — the PDF equivalent of CSS {@code linear-gradient(180deg, ...)},
     * used for the slide sidebar's colour wash.
     */
    void barraGradienteVertical(float x, float yInferior, float ancho, float alto, Color arriba, Color abajo)
            throws IOException {
        COSDictionary funcionCos = new COSDictionary();
        funcionCos.setInt(COSName.FUNCTION_TYPE, 2);
        funcionCos.setItem(COSName.DOMAIN, arreglo(0f, 1f));
        funcionCos.setItem(COSName.C0, colorArreglo(abajo));
        funcionCos.setItem(COSName.C1, colorArreglo(arriba));
        funcionCos.setFloat(COSName.N, 1f);

        PDShadingType2 shading = new PDShadingType2(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE2);
        shading.setColorSpace(PDDeviceRGB.INSTANCE);
        shading.setCoords(arreglo(x + (ancho / 2f), yInferior, x + (ancho / 2f), yInferior + alto));
        shading.setFunction(new PDFunctionType2(funcionCos));

        cs.saveGraphicsState();
        cs.addRect(x, yInferior, ancho, alto);
        cs.clip();
        cs.shadingFill(shading);
        cs.restoreGraphicsState();
    }

    private static COSArray arreglo(float... valores) {
        COSArray array = new COSArray();
        for (float valor : valores) {
            array.add(new COSFloat(valor));
        }
        return array;
    }

    private static COSArray colorArreglo(Color color) {
        return arreglo(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
    }

    float margen() {
        return margen;
    }

    float anchoPagina() {
        return tamanoPagina.getWidth();
    }

    private float escribir(List<Fragmento> fragmentos, float tam, float interlineado,
                           float sangriaPrimera, float sangria, Color color, PDFont fuenteForzada, boolean centrado,
                           float anchoMaximo)
            throws IOException {
        List<Token> tokens = tokenizar(fragmentos, tam, color, fuenteForzada);
        if (tokens.isEmpty()) {
            return 0f;
        }

        List<List<Token>> lineas = agruparEnLineas(tokens, sangriaPrimera, sangria, anchoMaximo);

        float consumido = 0f;
        for (int i = 0; i < lineas.size(); i++) {
            List<Token> linea = lineas.get(i);
            asegurarEspacio(interlineado);
            float x = centrado
                    ? (tamanoPagina.getWidth() - anchoLinea(linea)) / 2f
                    : margen + (i == 0 ? sangriaPrimera : sangria);
            dibujarLinea(linea, x, y);
            y -= interlineado;
            consumido += interlineado;
        }
        return consumido;
    }

    /** Word-wraps tokens into lines without drawing them, so a centered line's width is known upfront. */
    private List<List<Token>> agruparEnLineas(List<Token> tokens, float sangriaPrimera, float sangria,
                                               float anchoMaximo) {
        List<List<Token>> lineas = new ArrayList<>();
        List<Token> lineaActual = new ArrayList<>();
        float anchoLineaActual = 0f;
        float sangriaActual = sangriaPrimera;

        for (Token token : tokens) {
            float anchoToken = ancho(token);
            float anchoEspacio = (lineaActual.isEmpty() || !token.espacioAntes())
                    ? 0f
                    : ancho(lineaActual.get(lineaActual.size() - 1).fuente(), " ", token.tam());
            float anchoDisponible = anchoMaximo > 0f ? anchoMaximo : anchoDisponible(sangriaActual);

            if (!lineaActual.isEmpty() && anchoLineaActual + anchoEspacio + anchoToken > anchoDisponible) {
                lineas.add(lineaActual);
                lineaActual = new ArrayList<>();
                anchoLineaActual = 0f;
                sangriaActual = sangria;
                anchoEspacio = 0f;
            }
            lineaActual.add(token);
            anchoLineaActual += anchoEspacio + anchoToken;
        }
        if (!lineaActual.isEmpty()) {
            lineas.add(lineaActual);
        }
        return lineas;
    }

    private float anchoLinea(List<Token> linea) {
        float total = 0f;
        for (int i = 0; i < linea.size(); i++) {
            Token token = linea.get(i);
            if (i > 0 && token.espacioAntes()) {
                total += ancho(linea.get(i - 1).fuente(), " ", token.tam());
            }
            total += ancho(token);
        }
        return total;
    }

    private void dibujarLinea(List<Token> linea, float x, float yLinea) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, yLinea);
        for (int i = 0; i < linea.size(); i++) {
            Token token = linea.get(i);
            if (i > 0 && token.espacioAntes()) {
                // Draw the separator in the previous word's face: a space measured in the mono
                // font next to proportional text reads as a double space.
                Token anterior = linea.get(i - 1);
                cs.setFont(anterior.fuente(), anterior.tam());
                cs.setNonStrokingColor(anterior.color());
                cs.showText(" ");
            }
            cs.setFont(token.fuente(), token.tam());
            cs.setNonStrokingColor(token.color());
            mostrarTextoSinLigaduras(token.texto());
        }
        cs.endText();
    }

    /**
     * Draws one character per {@code Tj} operator instead of the whole word.
     *
     * <p>PDFBox 3 auto-applies GSUB ligature substitution (e.g. "fi" -> a single ligature glyph)
     * when a font's table declares it, which both Inter and Manrope do. That substitution has a
     * real correctness bug: the ligature glyph's ToUnicode entry comes out wrong, so copying or
     * searching the PDF silently corrupts every word with "fi", "fl", etc. (verified: "Definición"
     * extracts back as "Deznición"). Ligatures need at least two adjacent glyphs in the same
     * showText call to trigger, so drawing one character at a time defeats it entirely. The only
     * cost is kerning between letters, which is not worth a correctness bug to keep.
     */
    private void mostrarTextoSinLigaduras(String texto) throws IOException {
        texto.codePoints().forEach(punto -> {
            try {
                cs.showText(new String(Character.toChars(punto)));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private List<Token> tokenizar(List<Fragmento> fragmentos, float tam, Color color, PDFont fuenteForzada) {
        List<Token> tokens = new ArrayList<>();
        boolean espacioPendiente = false;

        for (Fragmento fragmento : fragmentos) {
            PDFont fuente = fuenteForzada != null
                    ? fuenteForzada
                    : fuentes.para(fragmento.negrita(), fragmento.cursiva(), fragmento.codigo());
            String limpio = codificable(fuente, TextoSeguro.normalizar(fragmento.texto()));
            if (limpio.isEmpty()) {
                continue;
            }
            if (limpio.isBlank()) {
                espacioPendiente = true;
                continue;
            }

            boolean abreConEspacio = Character.isWhitespace(limpio.charAt(0));
            boolean cierraConEspacio = Character.isWhitespace(limpio.charAt(limpio.length() - 1));

            String[] palabras = limpio.strip().split("\\s+");
            for (int i = 0; i < palabras.length; i++) {
                if (palabras[i].isEmpty()) {
                    continue;
                }
                boolean espacioAntes = i > 0 || espacioPendiente || abreConEspacio;
                tokens.addAll(partirSiNoCabe(new Token(palabras[i], fuente, tam, color, espacioAntes)));
            }
            espacioPendiente = cierraConEspacio;
        }
        return tokens;
    }

    /** Splits a token longer than the content width (URLs, formulas) so it never overflows. */
    private List<Token> partirSiNoCabe(Token token) {
        float maximo = anchoDisponible(0);
        if (ancho(token) <= maximo || token.texto().length() < 2) {
            return List.of(token);
        }
        List<Token> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (char c : token.texto().toCharArray()) {
            String candidato = actual.toString() + c;
            if (ancho(token.fuente(), candidato, token.tam()) > maximo && !actual.isEmpty()) {
                partes.add(new Token(actual.toString(), token.fuente(), token.tam(), token.color(),
                        partes.isEmpty() && token.espacioAntes()));
                actual = new StringBuilder();
            }
            actual.append(c);
        }
        if (!actual.isEmpty()) {
            partes.add(new Token(actual.toString(), token.fuente(), token.tam(), token.color(),
                    partes.isEmpty() && token.espacioAntes()));
        }
        return partes;
    }

    private float ancho(Token token) {
        return ancho(token.fuente(), token.texto(), token.tam());
    }

    private float ancho(PDFont fuente, String texto, float tam) {
        try {
            return fuente.getStringWidth(texto) / 1000f * tam;
        } catch (IOException | IllegalArgumentException e) {
            return texto.length() * tam * 0.6f;
        }
    }

    /**
     * Last line of defence: drops any character the embedded font cannot encode, so a stray glyph
     * (emoji, rare math) degrades to "?" instead of failing the whole export.
     */
    private String codificable(PDFont fuente, String texto) {
        try {
            fuente.getStringWidth(texto);
            return texto;
        } catch (IOException | IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder(texto.length());
            texto.codePoints().forEach(punto -> {
                String caracter = new String(Character.toChars(punto));
                try {
                    fuente.getStringWidth(caracter);
                    sb.append(caracter);
                } catch (IOException | IllegalArgumentException ignored) {
                    sb.append('?');
                }
            });
            return sb.toString();
        }
    }

    @Override
    public void close() throws IOException {
        if (cs != null) {
            cs.close();
            cs = null;
        }
    }
}
