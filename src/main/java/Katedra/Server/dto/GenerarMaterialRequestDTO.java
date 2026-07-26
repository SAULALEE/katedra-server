package Katedra.Server.dto;

import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.PiezaMaterial;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

/**
 * Selective generation request. Every requested piece is always (re)generated,
 * overwriting any existing content for it.
 *
 * @param piezas             pieces to generate; required, non-empty
 * @param modelo             model tier; null defaults to {@link ModeloIA#FLASH}
 * @param regenerarPiezas    unused; kept so older clients that still send it don't fail
 *                           deserialization. Every requested piece regenerates regardless.
 * @param numeroDiapositivas requested slide count; null defaults to the tier's default.
 *                           Must fall within the selected model tier's [min, max] range.
 * @param numeroParrafos     requested theory paragraph count; null defaults to the tier's
 *                           default. Must fall within the selected model tier's [min, max] range.
 * @param numeroPreguntas    requested evaluation question count; null defaults to the
 *                           tier's default. Must fall within the selected model tier's
 *                           [min, max] range.
 * @param numeroModulos      requested outline módulo (unidad) count; null defaults to the
 *                           tier's default. Each tier only allows its own two discrete
 *                           values ({@link ModeloIA#getMinModulos()} or
 *                           {@link ModeloIA#getMaxModulos()}), never a continuous range.
 */
@JsonDeserialize(using = GenerarMaterialRequestDTODeserializer.class)
public record GenerarMaterialRequestDTO(
    Set<PiezaMaterial> piezas,
    ModeloIA modelo,
    Set<PiezaMaterial> regenerarPiezas,
    Integer numeroDiapositivas,
    Integer numeroParrafos,
    Integer numeroPreguntas,
    Integer numeroModulos
) {
    public GenerarMaterialRequestDTO(
            Set<PiezaMaterial> piezas,
            ModeloIA modelo,
            Set<PiezaMaterial> regenerarPiezas,
            Integer numeroDiapositivas,
            Integer numeroParrafos,
            Integer numeroPreguntas) {
        this(piezas, modelo, regenerarPiezas, numeroDiapositivas, numeroParrafos, numeroPreguntas, null);
    }
}
