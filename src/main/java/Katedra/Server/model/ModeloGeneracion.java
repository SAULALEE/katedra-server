package Katedra.Server.model;

/**
 * Generation model options exposed by the temario creation forms. These are the
 * ingestion-facing labels; each maps onto one of Katedra's two branded tiers, so the
 * upload forms keep their BASICO/AVANZADO contract without duplicating tier config.
 */
public enum ModeloGeneracion {
    BASICO(ModeloIA.FLASH),
    AVANZADO(ModeloIA.PRO);

    private final ModeloIA modeloIA;

    ModeloGeneracion(ModeloIA modeloIA) {
        this.modeloIA = modeloIA;
    }

    public ModeloIA toModeloIA() {
        return modeloIA;
    }
}
