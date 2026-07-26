package Katedra.Server.service.export;

import Katedra.Server.model.PiezaMaterial;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Builds the download filename for an export.
 *
 * <p>Everything goes through {@link #slug(String)}, which reduces arbitrary user text to
 * {@code [a-z0-9-]}. That is not only cosmetic: it makes CRLF injection into the
 * {@code Content-Disposition} header impossible by construction.
 */
public final class NombreArchivo {

    /** Longest slug a single segment (materia, temario) may contribute. */
    private static final int MAX_SEGMENTO = 60;

    /** Used when the source text carries no usable character at all (e.g. "¿¡···!?"). */
    private static final String RELLENO = "material";

    private NombreArchivo() {
    }

    /**
     * Normalizes text into a lowercase, ASCII-only, hyphen-separated slug.
     * Accents are stripped rather than dropped, so "Ingeniería" stays readable as "ingenieria".
     */
    public static String slug(String texto) {
        if (texto == null || texto.isBlank()) {
            return RELLENO;
        }

        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String slug = sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            return RELLENO;
        }

        if (slug.length() > MAX_SEGMENTO) {
            slug = slug.substring(0, MAX_SEGMENTO).replaceAll("-+$", "");
        }
        return slug;
    }

    /** {@code matematicas-matematicas-para-la-ingenieria-teoria.pdf} */
    public static String construir(String materia, String temario, PiezaMaterial pieza, String extension) {
        return slug(materia) + "-" + slug(temario) + "-" + pieza.getValor() + "." + extension;
    }
}
