-- V7 used `NOT IN (...)` to detect already-normalized rows, but MySQL's default
-- utf8mb4_unicode_ci collation compares case-insensitively, so lowercase values
-- (e.g. 'universitario') matched the uppercase list and were skipped, leaving
-- them unreadable by Hibernate's case-sensitive Enum.valueOf(). Re-run the
-- normalization using a BINARY (case-sensitive) comparison.
UPDATE temario
SET grado_academico = CASE
    WHEN LOWER(grado_academico) LIKE '%primaria%' OR LOWER(grado_academico) LIKE '%elemental%' THEN 'PRIMARIA'
    WHEN LOWER(grado_academico) LIKE '%secundaria%' THEN 'SECUNDARIA'
    WHEN LOWER(grado_academico) LIKE '%bachiller%' OR LOWER(grado_academico) LIKE '%preparatoria%' THEN 'BACHILLERATO'
    WHEN LOWER(grado_academico) LIKE '%posgrado%' OR LOWER(grado_academico) LIKE '%postgrado%'
        OR LOWER(grado_academico) LIKE '%maestr%' OR LOWER(grado_academico) LIKE '%doctora%' THEN 'POSGRADO'
    ELSE 'UNIVERSITARIO'
END
WHERE BINARY grado_academico NOT IN ('PRIMARIA', 'SECUNDARIA', 'BACHILLERATO', 'UNIVERSITARIO', 'POSGRADO');
