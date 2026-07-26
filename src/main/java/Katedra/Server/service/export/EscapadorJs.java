package Katedra.Server.service.export;

/**
 * Escapes text into JavaScript literals for the generated Apps Script.
 *
 * <p>Everything is emitted as ASCII: the script may be pasted into an editor or clipboard that is
 * not UTF-8, and U+2028/U+2029 — legal in JSON, illegal as raw JS line terminators in older
 * engines — would otherwise turn into a syntax error the teacher cannot diagnose.
 */
public final class EscapadorJs {

    private EscapadorJs() {
    }

    /** Wraps the text in single quotes, ready to drop into the script. */
    public static String aLiteral(String texto) {
        return "'" + escaparContenido(texto == null ? "" : texto) + "'";
    }

    /** Same escaping without the surrounding quotes, for text inside comments or larger literals. */
    public static String aAscii(String texto) {
        return escaparContenido(texto == null ? "" : texto);
    }

    private static String escaparContenido(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 16);

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\x3C"); // defensive: never close a host <script> tag
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
