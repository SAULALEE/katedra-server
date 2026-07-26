package Katedra.Server.model;

/**
 * Generation model options exposed by the temario creation forms. These are the
 * ingestion-facing labels for syllabus uploads (distinct from the content generation flow).
 * Each maps onto one of Katedra's two branded tiers, aligned with ModeloIA's display names.
 */
public enum ModeloGeneracion {
    TUTOR(ModeloIA.FLASH),
    CATEDRATICO(ModeloIA.PRO);

    private final ModeloIA modeloIA;

    ModeloGeneracion(ModeloIA modeloIA) {
        this.modeloIA = modeloIA;
    }

    public ModeloIA toModeloIA() {
        return modeloIA;
    }
}
