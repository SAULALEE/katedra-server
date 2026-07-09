-- teoria/ejercicios hold generated markdown, not structured JSON documents (unlike
-- evaluacion/diapositivas, which are real JSON arrays of DTOs). Declaring them JSON
-- forced MySQL to run cast(? as json) on plain markdown text, which fails validation
-- with "Invalid JSON text" as soon as real AI content is written.
ALTER TABLE contenido_temario
    MODIFY COLUMN teoria LONGTEXT,
    MODIFY COLUMN ejercicios LONGTEXT;
