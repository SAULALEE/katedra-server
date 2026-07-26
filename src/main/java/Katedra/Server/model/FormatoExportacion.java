package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.EnumSet;
import java.util.Set;

/**
 * File formats the generated material can be exported to.
 * JSON values are lowercase to match the frontend contract, mirroring {@link PiezaMaterial}.
 *
 * <p>Not every format applies to every piece: slides make no sense as markdown, and only a
 * multiple-choice evaluation can become a Google Forms script. {@link #soporta(PiezaMaterial)}
 * is the single source of truth for those combinations.
 */
public enum FormatoExportacion {

    DOCX("docx", "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            EnumSet.of(PiezaMaterial.TEORIA, PiezaMaterial.EVALUACION)),

    PDF("pdf", "pdf",
            "application/pdf",
            EnumSet.of(PiezaMaterial.TEORIA, PiezaMaterial.EVALUACION, PiezaMaterial.DIAPOSITIVAS)),

    MARKDOWN("md", "md",
            "text/markdown;charset=UTF-8",
            EnumSet.of(PiezaMaterial.TEORIA, PiezaMaterial.EVALUACION)),

    PPTX("pptx", "pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            EnumSet.of(PiezaMaterial.DIAPOSITIVAS)),

    APPS_SCRIPT("gs", "gs",
            "text/javascript;charset=UTF-8",
            EnumSet.of(PiezaMaterial.EVALUACION));

    private final String valor;
    private final String extension;
    private final String mediaType;
    private final Set<PiezaMaterial> piezasSoportadas;

    FormatoExportacion(String valor, String extension, String mediaType, Set<PiezaMaterial> piezasSoportadas) {
        this.valor = valor;
        this.extension = extension;
        this.mediaType = mediaType;
        this.piezasSoportadas = piezasSoportadas;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    public String getExtension() {
        return extension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public boolean soporta(PiezaMaterial pieza) {
        return piezasSoportadas.contains(pieza);
    }

    @JsonCreator
    public static FormatoExportacion fromValor(String valor) {
        for (FormatoExportacion formato : values()) {
            if (formato.valor.equalsIgnoreCase(valor)) {
                return formato;
            }
        }
        throw new IllegalArgumentException("Formato de exportación desconocido: " + valor);
    }
}
