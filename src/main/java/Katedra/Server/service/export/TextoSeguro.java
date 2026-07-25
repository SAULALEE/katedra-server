package Katedra.Server.service.export;

import java.text.Normalizer;
import java.util.Map;

/**
 * Makes model-generated text safe to draw with an embedded PDF font.
 *
 * <p>Even a broad font like DejaVu does not cover everything an LLM can emit (emoji, rare math),
 * and PDFBox throws mid-render on a missing glyph — which would surface as a 500 on an otherwise
 * fine export. Two defences: normalize to NFC and fold the common typographic symbols to plain
 * equivalents here, then let {@code PdfLayout} drop anything still unmappable.
 */
public final class TextoSeguro {

    private static final Map<Character, String> EQUIVALENCIAS = Map.ofEntries(
            Map.entry('‘', "'"), Map.entry('’', "'"),
            Map.entry('“', "\""), Map.entry('”', "\""),
            Map.entry('–', "-"), Map.entry('—', "--"),
            Map.entry('…', "..."), Map.entry(' ', " "),
            Map.entry('→', "->"), Map.entry('←', "<-"),
            Map.entry('⇒', "=>"), Map.entry('≤', "<="),
            Map.entry('≥', ">="), Map.entry('≠', "!="),
            Map.entry('×', "x"), Map.entry('•', "-"),
            Map.entry('✓', "[ok]"), Map.entry('\t', "    "));

    private TextoSeguro() {
    }

    /** NFC-normalizes and folds typographic symbols; control characters are dropped. */
    public static String normalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(normalizado.length());

        for (int i = 0; i < normalizado.length(); i++) {
            char c = normalizado.charAt(i);
            String equivalente = EQUIVALENCIAS.get(c);
            if (equivalente != null) {
                sb.append(equivalente);
            } else if (c == '\n' || c == '\r' || c >= ' ') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
