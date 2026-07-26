package Katedra.Server.service.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.awt.Color;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Stamps the Katedra branding onto a finished document and locks it against editing.
 *
 * <p>Runs as a second pass over every page: only once all content exists is the page count known,
 * and only then is it certain that pages created mid-paragraph also get their header and footer.
 */
final class PdfBranding {

    private static final Color GRIS = new Color(0x64, 0x74, 0x8B);
    private static final Color TINTA = new Color(0x0F, 0x17, 0x2A);
    private static final Color LINEA = new Color(0xE2, 0xE8, 0xF0);

    private static final float TAM_MATERIA = 9f;
    private static final float TAM_TEMARIO = 12.5f;
    private static final float TAM_PIE = 8.5f;

    private PdfBranding() {
    }

    /**
     * Draws the two-line header (materia over temario) and the copyright footer on every page.
     *
     * @param compacto true for landscape slide pages, where the header sits tighter to the edge
     */
    static void estampar(PDDocument documento, String materia, String temarioTitulo,
                         PdfFuentes.Juego fuentes, boolean compacto) throws IOException {
        int total = documento.getNumberOfPages();
        String lineaMateria = TextoSeguro.normalizar(MarcaDocumento.lineaMateria(materia));
        String lineaTemario = TextoSeguro.normalizar(MarcaDocumento.lineaTemario(temarioTitulo));

        for (int i = 0; i < total; i++) {
            PDPage pagina = documento.getPage(i);
            float ancho = pagina.getMediaBox().getWidth();
            float alto = pagina.getMediaBox().getHeight();
            float margen = compacto ? 44f : 56f;

            try (PDPageContentStream cs = new PDPageContentStream(
                    documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {

                if (!compacto) {
                    escribir(cs, lineaMateria, fuentes.interBold(), TAM_MATERIA, GRIS, margen, alto - 52f);
                    escribir(cs, lineaTemario, fuentes.interSemibold(), TAM_TEMARIO, TINTA, margen, alto - 68f);
                    cs.setStrokingColor(LINEA);
                    cs.setLineWidth(0.6f);
                    cs.moveTo(margen, alto - 78f);
                    cs.lineTo(ancho - margen, alto - 78f);
                    cs.stroke();
                }

                String pie = MarcaDocumento.PIE_PAGINA;
                float anchoPie = anchoTexto(fuentes.regular(), pie, TAM_PIE);
                escribir(cs, pie, fuentes.regular(), TAM_PIE, GRIS, (ancho - anchoPie) / 2f, compacto ? 22f : 40f);

                String numeracion = (i + 1) + " / " + total;
                float anchoNumeracion = anchoTexto(fuentes.regular(), numeracion, TAM_PIE);
                escribir(cs, numeracion, fuentes.regular(), TAM_PIE, GRIS,
                        ancho - margen - anchoNumeracion, compacto ? 22f : 40f);
            }
        }
    }

    /** Fills the PDF info dictionary so the provenance travels with the file. */
    static void describir(PDDocument documento, String materia, String temarioTitulo) {
        PDDocumentInformation info = documento.getDocumentInformation();
        info.setTitle(MarcaDocumento.titulo(materia, temarioTitulo));
        info.setAuthor(MarcaDocumento.AUTOR);
        info.setCreator(MarcaDocumento.AUTOR);
        info.setProducer(MarcaDocumento.AUTOR);
        info.setKeywords(MarcaDocumento.PIE_PAGINA);
    }

    /**
     * Encrypts with a random owner password and no user password: anyone can open and print the
     * file, nobody can edit it or strip the footer in a PDF editor. The owner password is never
     * stored, so not even Katedra can unlock it afterwards.
     *
     * <p>Must be the last call before saving.
     */
    static void proteger(PDDocument documento) throws IOException {
        AccessPermission permisos = new AccessPermission();
        permisos.setCanModify(false);
        permisos.setCanModifyAnnotations(false);
        permisos.setCanAssembleDocument(false);
        permisos.setCanFillInForm(false);
        permisos.setCanPrint(true);
        permisos.setCanExtractContent(true);
        permisos.setCanExtractForAccessibility(true);

        byte[] semilla = new byte[24];
        new SecureRandom().nextBytes(semilla);
        String propietario = Base64.getUrlEncoder().withoutPadding().encodeToString(semilla);

        StandardProtectionPolicy politica = new StandardProtectionPolicy(propietario, "", permisos);
        politica.setEncryptionKeyLength(256);
        politica.setPreferAES(true);
        documento.protect(politica);
    }

    private static void escribir(PDPageContentStream cs, String texto, PDFont fuente, float tam,
                                 Color color, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(fuente, tam);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(texto);
        cs.endText();
    }

    private static float anchoTexto(PDFont fuente, String texto, float tam) throws IOException {
        return fuente.getStringWidth(texto) / 1000f * tam;
    }
}
