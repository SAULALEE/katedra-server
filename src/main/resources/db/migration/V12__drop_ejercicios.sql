-- Exercises were removed from the product to cut generation latency and token
-- cost (each generar-material call spent one full model round-trip on them).
-- V10/V11 already ran in dev environments, so the column is dropped here
-- instead of editing those applied migrations.
ALTER TABLE contenido_temario DROP COLUMN ejercicios;
