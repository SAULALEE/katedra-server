-- EjercicioDTO gained titulo, tiempoEstimado, conceptosClave, and pistas (replacing the
-- single pista string) to make exercise cards interactive. Rows generated before this
-- change store the old 5-field shape (tipo, dificultad, enunciado, pista, solucion),
-- which Jackson cannot deserialize into the new record (unrecognized "pista" field,
-- missing titulo/tiempoEstimado/conceptosClave), causing a 500 on GET .../contenido.
-- As with V10, the old shape cannot be losslessly mapped into the new one, so it is
-- discarded.
UPDATE contenido_temario SET ejercicios = NULL WHERE ejercicios IS NOT NULL;
