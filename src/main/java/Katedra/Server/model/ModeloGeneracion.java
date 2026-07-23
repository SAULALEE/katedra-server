package Katedra.Server.model;

/**
 * Generation model options exposed by the temario creation forms.
 */
public enum ModeloGeneracion {
    BASICO(ModeloIA.BASICO),
    AVANZADO(ModeloIA.AVANZADO);

    private final ModeloIA modeloIA;

    ModeloGeneracion(ModeloIA modeloIA) {
        this.modeloIA = modeloIA;
    }

    public ModeloIA toModeloIA() {
        return modeloIA;
    }
}
