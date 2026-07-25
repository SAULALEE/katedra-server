package Katedra.Server.service.export;

import java.util.Locale;

/**
 * Branding every exported document carries: the two-line header (materia over temario) and the
 * copyright footer, which must appear on every page and which the downloader cannot remove.
 */
public final class MarcaDocumento {

    /** Exact footer text required on every page/slide of every export. */
    public static final String PIE_PAGINA = "© Katedra, 2026";

    public static final String AUTOR = "Katedra";

    private MarcaDocumento() {
    }

    /** Top line of the header: the subject, in caps so it reads as an eyebrow above the title. */
    public static String lineaMateria(String materia) {
        return (materia == null ? "" : materia).toUpperCase(Locale.ROOT);
    }

    /** Bottom line of the header: the syllabus title. */
    public static String lineaTemario(String temarioTitulo) {
        return temarioTitulo == null ? "" : temarioTitulo;
    }

    /** Document title used in file metadata (PDF info dictionary, OOXML core properties). */
    public static String titulo(String materia, String temarioTitulo) {
        return materia + " — " + temarioTitulo;
    }
}
