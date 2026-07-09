-- Normalize free-text grado_academico into the fixed NivelAcademico enum bands.
UPDATE temario
SET grado_academico = CASE
    WHEN LOWER(grado_academico) LIKE '%primaria%' OR LOWER(grado_academico) LIKE '%elemental%' THEN 'PRIMARIA'
    WHEN LOWER(grado_academico) LIKE '%secundaria%' THEN 'SECUNDARIA'
    WHEN LOWER(grado_academico) LIKE '%bachiller%' OR LOWER(grado_academico) LIKE '%preparatoria%' THEN 'BACHILLERATO'
    WHEN LOWER(grado_academico) LIKE '%posgrado%' OR LOWER(grado_academico) LIKE '%postgrado%'
        OR LOWER(grado_academico) LIKE '%maestr%' OR LOWER(grado_academico) LIKE '%doctora%' THEN 'POSGRADO'
    ELSE 'UNIVERSITARIO'
END
WHERE grado_academico IS NULL OR grado_academico NOT IN ('PRIMARIA', 'SECUNDARIA', 'BACHILLERATO', 'UNIVERSITARIO', 'POSGRADO');

ALTER TABLE temario MODIFY COLUMN grado_academico VARCHAR(20) NOT NULL;

-- Replace stored raw OpenAI model ids with the branded Katedra tier key so the
-- persisted contract never leaks a provider model id.
UPDATE contenido_temario
SET modelo = CASE
    WHEN modelo IN ('gpt-4o-mini', 'gpt-4.1-mini') THEN 'flash'
    WHEN modelo = 'gpt-4o' THEN 'pro'
    ELSE 'flash'
END
WHERE modelo IS NOT NULL;
