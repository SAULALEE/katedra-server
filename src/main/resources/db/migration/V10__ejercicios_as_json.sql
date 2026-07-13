-- Ejercicios switches from freeform markdown (V9) to a structured JSON array of
-- exercise cards (tipo, dificultad, enunciado, pista, solucion), matching evaluacion's
-- structured-output pattern so the frontend can render each exercise as its own
-- interactive card instead of parsing markdown headings. Existing markdown content is
-- discarded (NULL) since it cannot be losslessly mapped into the new card shape.
UPDATE contenido_temario SET ejercicios = NULL;

ALTER TABLE contenido_temario
    MODIFY COLUMN ejercicios JSON;
