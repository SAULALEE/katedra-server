package Katedra.Server.service.export;

import Katedra.Server.dto.MaterialExportableDTO;
import Katedra.Server.model.FormatoExportacion;

import java.io.IOException;

/**
 * Renders a piece of generated material into one concrete file format.
 *
 * <p>Implementations are Spring beans; {@code ExportacionMaterialService} collects them all and
 * indexes them by {@link #formato()}, so adding a format is a one-class change.
 */
public interface ExportadorMaterial {

    /** The format this exporter produces. Must be unique across implementations. */
    FormatoExportacion formato();

    /**
     * @param material read-only snapshot; only the field matching {@code material.pieza()} is populated
     * @return the file bytes
     * @throws IllegalArgumentException if the piece is not supported by this format
     */
    byte[] exportar(MaterialExportableDTO material) throws IOException;
}
