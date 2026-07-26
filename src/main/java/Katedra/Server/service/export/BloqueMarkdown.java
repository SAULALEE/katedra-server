package Katedra.Server.service.export;

import java.util.List;

/**
 * Format-neutral markdown AST. {@link MarkdownParser} produces it once and both the PDF and the
 * DOCX exporter consume it, so the commonmark walk exists in a single place.
 */
public sealed interface BloqueMarkdown {

    /** A styled slice of text inside a block. */
    record Fragmento(String texto, boolean negrita, boolean cursiva, boolean codigo) {
        public static Fragmento plano(String texto) {
            return new Fragmento(texto, false, false, false);
        }
    }

    /** {@code nivel} is the markdown heading level, 1-6. */
    record Titulo(int nivel, List<Fragmento> fragmentos) implements BloqueMarkdown {}

    record Parrafo(List<Fragmento> fragmentos) implements BloqueMarkdown {}

    /** A list item. {@code nivel} is the nesting depth (0 = top level). */
    record Elemento(int nivel, String marcador, List<Fragmento> fragmentos) implements BloqueMarkdown {}

    record Codigo(String texto) implements BloqueMarkdown {}

    record Cita(List<Fragmento> fragmentos) implements BloqueMarkdown {}

    record Regla() implements BloqueMarkdown {}

    /** {@code filas.get(0)} is the header row. */
    record Tabla(List<List<String>> filas) implements BloqueMarkdown {}
}
