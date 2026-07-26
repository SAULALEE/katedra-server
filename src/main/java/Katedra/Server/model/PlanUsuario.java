package Katedra.Server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Billing tier of a user. Orthogonal to {@link RolUsuario}: the role answers "what may
 * this account administer", the plan answers "what has this account paid for". Never
 * conflate them — a ROLE_ADMIN on FREE is a perfectly valid combination.
 *
 * <p>Each tier carries its own limits, mirroring how {@link ModeloIA} carries its own
 * generation ranges. Keeping the numbers here (and not scattered across the guard
 * service) makes them one-line tunable and directly unit-testable.
 *
 * <p>The daily caps are deliberately per-day rather than lifetime: Katedra is sold to
 * individual teachers, so a permanently-useful free tier drives return visits, whereas a
 * lifetime cap produces churn the moment it is hit. The upgrade pressure is meant to come
 * from the capability locks below (Catedrático, slides, file/URL ingest), not from
 * starving the quota.
 */
public enum PlanUsuario {
    /** ~2-3 complete syllabi per day, manual entry only, Tutor model only. */
    FREE("free", "Gratis", 10, 5, false, false, false, false, false),
    /**
     * Everything unlocked. 100 pieces/day is not a paywall, it is an abuse ceiling:
     * every piece is a real OpenAI call, and an uncapped reasoning model at this price
     * point is a margin risk. At ~4 pieces per syllabus this is ~25 syllabi a day, far
     * beyond any real teaching workload.
     */
    PRO("pro", "Pro", 100, 100, true, true, true, true, true);

    private final String valor;
    private final String etiqueta;
    private final int generacionesPorDia;
    private final int exportacionesPorDia;
    private final boolean permiteModeloPro;
    private final boolean permiteDiapositivas;
    private final boolean permiteCargaArchivo;
    private final boolean permiteCargaUrl;
    private final boolean permiteExportacionAvanzada;

    PlanUsuario(String valor, String etiqueta, int generacionesPorDia, int exportacionesPorDia,
                boolean permiteModeloPro, boolean permiteDiapositivas,
                boolean permiteCargaArchivo, boolean permiteCargaUrl,
                boolean permiteExportacionAvanzada) {
        this.valor = valor;
        this.etiqueta = etiqueta;
        this.generacionesPorDia = generacionesPorDia;
        this.exportacionesPorDia = exportacionesPorDia;
        this.permiteModeloPro = permiteModeloPro;
        this.permiteDiapositivas = permiteDiapositivas;
        this.permiteCargaArchivo = permiteCargaArchivo;
        this.permiteCargaUrl = permiteCargaUrl;
        this.permiteExportacionAvanzada = permiteExportacionAvanzada;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    /** Human-readable name shown in the UI badge. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Max AI pieces (estructura/teoria/evaluacion/diapositivas) generatable per day. */
    public int getGeneracionesPorDia() {
        return generacionesPorDia;
    }

    /** Max material exports per day. Purely anti-abuse: exports cost no LLM call. */
    public int getExportacionesPorDia() {
        return exportacionesPorDia;
    }

    /** Whether {@link ModeloIA#PRO} (Catedrático) may be requested. */
    public boolean permiteModeloPro() {
        return permiteModeloPro;
    }

    /** Whether {@link PiezaMaterial#DIAPOSITIVAS} may be generated. */
    public boolean permiteDiapositivas() {
        return permiteDiapositivas;
    }

    /** Whether a syllabus may be created from an uploaded pdf/doc/docx/md. */
    public boolean permiteCargaArchivo() {
        return permiteCargaArchivo;
    }

    /** Whether a syllabus may be created from a web URL. */
    public boolean permiteCargaUrl() {
        return permiteCargaUrl;
    }

    /** Whether {@link FormatoExportacion#MARKDOWN} and {@link FormatoExportacion#APPS_SCRIPT} may be exported. */
    public boolean permiteExportacionAvanzada() {
        return permiteExportacionAvanzada;
    }

    @JsonCreator
    public static PlanUsuario fromValor(String valor) {
        for (PlanUsuario plan : values()) {
            if (plan.valor.equalsIgnoreCase(valor) || plan.name().equalsIgnoreCase(valor)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Plan no permitido: " + valor);
    }
}
