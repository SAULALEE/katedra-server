package Katedra.Server.dto;

import Katedra.Server.model.ModeloIA;
import Katedra.Server.model.PiezaMaterial;

import java.util.Set;

/**
 * Selective generation request.
 *
 * @param piezas          pieces to generate; required, non-empty
 * @param modelo          model tier; null defaults to {@link ModeloIA#SENCILLO}
 * @param regenerarPiezas subset of {@code piezas} allowed to overwrite existing content;
 *                        null/empty means existing pieces are never regenerated (credit-safe default)
 */
public record GenerarMaterialRequestDTO(
    Set<PiezaMaterial> piezas,
    ModeloIA modelo,
    Set<PiezaMaterial> regenerarPiezas
) {}
