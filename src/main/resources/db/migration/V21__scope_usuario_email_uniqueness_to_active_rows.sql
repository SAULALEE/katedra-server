ALTER TABLE usuario DROP INDEX email;
ALTER TABLE usuario ADD COLUMN email_activo VARCHAR(255)
    GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) STORED;
ALTER TABLE usuario ADD UNIQUE INDEX uq_usuario_email_activo (email_activo);
