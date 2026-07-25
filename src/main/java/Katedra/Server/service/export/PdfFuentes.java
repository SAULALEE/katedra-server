package Katedra.Server.service.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Supplies the embedded PDF fonts — the exact family/weight pairs the app itself uses, so an
 * export reads as a continuation of the product rather than a generic document:
 *
 * <ul>
 *   <li>Inter — headings and titles ({@code .markdown-body h1-h4}, slide titles, page titles)</li>
 *   <li>Manrope — body copy, labels and eyebrows ({@code .markdown-body p,li}, badges, footers)</li>
 *   <li>JetBrains Mono — code blocks ({@code .markdown-body code})</li>
 * </ul>
 *
 * <p>These are Latin-subset static instances, so PDFBox never has to instance a variable font
 * (which it cannot do). Coverage is narrower than a general-purpose font like DejaVu — Greek
 * letters or rare math symbols fall back to {@code ?} via {@link PdfLayout}'s safety net — but
 * matching the product's exact typography was the explicit requirement here.
 *
 * <p>A {@link PDFont} belongs to the {@link PDDocument} that loaded it, so only the raw bytes are
 * cached here and {@link #cargar(PDDocument)} re-loads them per document.
 */
@Component
public class PdfFuentes {

    private static final String RUTA = "fonts/";

    private final byte[] interRegular = leer("Inter-Regular.ttf");
    private final byte[] interSemibold = leer("Inter-SemiBold.ttf");
    private final byte[] interBold = leer("Inter-Bold.ttf");
    private final byte[] interItalic = leer("Inter-Italic.ttf");
    private final byte[] manropeMedium = leer("Manrope-Medium.ttf");
    private final byte[] manropeBold = leer("Manrope-Bold.ttf");
    private final byte[] manropeExtraBold = leer("Manrope-ExtraBold.ttf");
    private final byte[] jetBrainsMono = leer("JetBrainsMono-Regular.ttf");

    /**
     * The document-bound faces. {@code para(...)} resolves body-text styling (paragraphs, list
     * items, quotes, table cells) exactly like before; heading-weight text goes through the
     * dedicated {@code titulo*} accessors instead, since a heading in the app is a different font
     * family (Inter), not just a bold weight of the body font.
     */
    public record Juego(
            PDFont interRegular, PDFont interSemibold, PDFont interBold, PDFont interItalic,
            PDFont manropeMedium, PDFont manropeBold, PDFont manropeExtraBold, PDFont mono) {

        /** Body text face for a styled fragment; bold wins over italic when both are set. */
        public PDFont para(boolean esNegrita, boolean esCursiva, boolean esCodigo) {
            if (esCodigo) {
                return mono;
            }
            if (esNegrita) {
                return manropeBold;
            }
            return esCursiva ? interItalic : manropeMedium;
        }

        /** Regular body face — used as the default/marker font wherever no styling applies. */
        public PDFont regular() {
            return manropeMedium;
        }

        /** Heading face for the given markdown level (H1 gets more weight than H3+). */
        public PDFont titulo(int nivel) {
            return nivel <= 1 ? interBold : interSemibold;
        }
    }

    public Juego cargar(PDDocument documento) throws IOException {
        return new Juego(
                cargarFuente(documento, interRegular),
                cargarFuente(documento, interSemibold),
                cargarFuente(documento, interBold),
                cargarFuente(documento, interItalic),
                cargarFuente(documento, manropeMedium),
                cargarFuente(documento, manropeBold),
                cargarFuente(documento, manropeExtraBold),
                cargarFuente(documento, jetBrainsMono));
    }

    private PDFont cargarFuente(PDDocument documento, byte[] bytes) throws IOException {
        return PDType0Font.load(documento, new ByteArrayInputStream(bytes), true);
    }

    private static byte[] leer(String nombre) {
        try (InputStream in = new ClassPathResource(RUTA + nombre).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la fuente " + nombre, e);
        }
    }
}
