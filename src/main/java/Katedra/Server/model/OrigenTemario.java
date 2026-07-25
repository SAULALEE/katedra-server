package Katedra.Server.model;

/**
 * How a syllabus was created. Only used to gate the paid ingestion paths — FREE may type
 * a syllabus by hand, but extracting one from a PDF or scraping it off a web page is a
 * PRO capability.
 */
public enum OrigenTemario {
    MANUAL("manual"),
    ARCHIVO("archivo"),
    URL("enlace web");

    private final String etiqueta;

    OrigenTemario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Spanish label used to build the user-facing rejection message. */
    public String getEtiqueta() {
        return etiqueta;
    }
}
