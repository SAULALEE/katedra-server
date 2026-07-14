package Katedra.Server.model;

/**
 * Preset correction actions for the theory-editing assistant. Each carries a canned Spanish
 * instruction fragment injected into the correction user prompt (mirrors how {@link ModeloIA}
 * carries its style strings as enum fields — the prompt scaffolding still lives in
 * {@code resources/prompts/*.st}). A free-form {@code message} may be appended to refine the
 * action (e.g. {@code ACORTAR} + "a 4 párrafos"). {@link #FREE_CHAT} has no canned instruction:
 * the user's {@code message} IS the instruction, and it is the only action subject to the
 * off-topic pre-filter.
 */
public enum AssistantQuickAction {

    ACORTAR("Acorta la teoría manteniendo las ideas centrales, sin eliminar información esencial."),
    EXTENDER("Añade un párrafo más de desarrollo, manteniendo la misma información, el mismo tema y el mismo nivel académico."),
    SIMPLIFICAR("Simplifica el lenguaje y la redacción sin perder información ni rigor conceptual."),
    AGREGAR_EJEMPLO("Añade un ejemplo resuelto, contextualizado a la asignatura y explicado paso a paso."),
    CORREGIR_REDACCION("Corrige la redacción, la ortografía y la coherencia del texto sin cambiar su contenido ni su significado."),
    FREE_CHAT(null);

    private final String instruccion;

    AssistantQuickAction(String instruccion) {
        this.instruccion = instruccion;
    }

    /** Canned correction instruction; {@code null} for {@link #FREE_CHAT}. */
    public String getInstruccion() {
        return instruccion;
    }
}
