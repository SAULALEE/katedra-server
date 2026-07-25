package Katedra.Server.service.export;

import Katedra.Server.service.export.BloqueMarkdown.Cita;
import Katedra.Server.service.export.BloqueMarkdown.Codigo;
import Katedra.Server.service.export.BloqueMarkdown.Elemento;
import Katedra.Server.service.export.BloqueMarkdown.Fragmento;
import Katedra.Server.service.export.BloqueMarkdown.Parrafo;
import Katedra.Server.service.export.BloqueMarkdown.Regla;
import Katedra.Server.service.export.BloqueMarkdown.Tabla;
import Katedra.Server.service.export.BloqueMarkdown.Titulo;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the AI's markdown into {@link BloqueMarkdown} blocks.
 *
 * <p>The only class that touches commonmark. Raw HTML nodes are skipped rather than interpreted:
 * the input is model output, so it is treated as untrusted text.
 */
public final class MarkdownParser {

    /** Deeper nesting stops adding indentation, so a runaway list can't push text off the page. */
    private static final int NIVEL_MAXIMO = 3;

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private MarkdownParser() {
    }

    public static List<BloqueMarkdown> analizar(String markdown) {
        List<BloqueMarkdown> bloques = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return bloques;
        }
        recorrerBloques(PARSER.parse(markdown), bloques, 0);
        return bloques;
    }

    private static void recorrerBloques(Node padre, List<BloqueMarkdown> salida, int nivelLista) {
        for (Node nodo = padre.getFirstChild(); nodo != null; nodo = nodo.getNext()) {
            switch (nodo) {
                case Heading heading -> salida.add(new Titulo(heading.getLevel(), inline(heading)));
                case Paragraph parrafo -> agregarSiNoVacio(salida, new Parrafo(inline(parrafo)));
                case BulletList lista -> recorrerLista(lista, salida, nivelLista, false);
                case OrderedList lista -> recorrerLista(lista, salida, nivelLista, true);
                case FencedCodeBlock codigo -> salida.add(new Codigo(codigo.getLiteral()));
                case IndentedCodeBlock codigo -> salida.add(new Codigo(codigo.getLiteral()));
                case BlockQuote cita -> recorrerCita(cita, salida);
                case ThematicBreak ignored -> salida.add(new Regla());
                case TableBlock tabla -> agregarTabla(tabla, salida);
                case HtmlBlock ignored -> { /* never interpret raw HTML from the model */ }
                default -> recorrerBloques(nodo, salida, nivelLista);
            }
        }
    }

    private static void recorrerLista(Node lista, List<BloqueMarkdown> salida, int nivel, boolean ordenada) {
        int indice = 1;
        for (Node item = lista.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            String marcador = ordenada ? (indice++) + "." : "•";
            List<Fragmento> texto = new ArrayList<>();
            List<BloqueMarkdown> anidados = new ArrayList<>();

            for (Node hijo = item.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
                if (hijo instanceof Paragraph parrafo && texto.isEmpty()) {
                    texto.addAll(inline(parrafo));
                } else if (hijo instanceof BulletList sublista) {
                    recorrerLista(sublista, anidados, nivel + 1, false);
                } else if (hijo instanceof OrderedList sublista) {
                    recorrerLista(sublista, anidados, nivel + 1, true);
                } else {
                    recorrerBloques(hijo.getParent() == item ? item : hijo, anidados, nivel + 1);
                    break;
                }
            }

            if (!texto.isEmpty()) {
                salida.add(new Elemento(Math.min(nivel, NIVEL_MAXIMO), marcador, texto));
            }
            salida.addAll(anidados);
        }
    }

    private static void recorrerCita(Node cita, List<BloqueMarkdown> salida) {
        for (Node hijo = cita.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            List<Fragmento> fragmentos = inline(hijo);
            if (!fragmentos.isEmpty()) {
                salida.add(new Cita(fragmentos));
            }
        }
    }

    private static void agregarTabla(TableBlock tabla, List<BloqueMarkdown> salida) {
        List<List<String>> filas = new ArrayList<>();
        recolectarFilas(tabla, filas);
        if (!filas.isEmpty()) {
            salida.add(new Tabla(filas));
        }
    }

    private static void recolectarFilas(Node padre, List<List<String>> filas) {
        for (Node nodo = padre.getFirstChild(); nodo != null; nodo = nodo.getNext()) {
            if (nodo instanceof TableRow fila) {
                List<String> celdas = new ArrayList<>();
                for (Node celda = fila.getFirstChild(); celda != null; celda = celda.getNext()) {
                    if (celda instanceof TableCell) {
                        celdas.add(textoPlano(inline(celda)));
                    }
                }
                if (!celdas.isEmpty()) {
                    filas.add(celdas);
                }
            } else {
                recolectarFilas(nodo, filas);
            }
        }
    }

    private static void agregarSiNoVacio(List<BloqueMarkdown> salida, Parrafo parrafo) {
        if (!parrafo.fragmentos().isEmpty()) {
            salida.add(parrafo);
        }
    }

    /** Flattens a block's inline tree into styled fragments. */
    public static List<Fragmento> inline(Node padre) {
        List<Fragmento> fragmentos = new ArrayList<>();
        recorrerInline(padre, fragmentos, false, false);
        return fragmentos;
    }

    private static void recorrerInline(Node padre, List<Fragmento> salida, boolean negrita, boolean cursiva) {
        for (Node nodo = padre.getFirstChild(); nodo != null; nodo = nodo.getNext()) {
            switch (nodo) {
                case Text texto -> agregar(salida, texto.getLiteral(), negrita, cursiva, false);
                case StrongEmphasis fuerte -> recorrerInline(fuerte, salida, true, cursiva);
                case Emphasis enfasis -> recorrerInline(enfasis, salida, negrita, true);
                case Code codigo -> agregar(salida, codigo.getLiteral(), negrita, cursiva, true);
                case SoftLineBreak ignored -> agregar(salida, " ", negrita, cursiva, false);
                case HardLineBreak ignored -> agregar(salida, " ", negrita, cursiva, false);
                case HtmlInline ignored -> { /* never interpret raw HTML from the model */ }
                default -> recorrerInline(nodo, salida, negrita, cursiva);
            }
        }
    }

    private static void agregar(List<Fragmento> salida, String texto, boolean negrita, boolean cursiva, boolean codigo) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        salida.add(new Fragmento(texto, negrita, cursiva, codigo));
    }

    public static String textoPlano(List<Fragmento> fragmentos) {
        StringBuilder sb = new StringBuilder();
        fragmentos.forEach(f -> sb.append(f.texto()));
        return sb.toString().strip();
    }
}
