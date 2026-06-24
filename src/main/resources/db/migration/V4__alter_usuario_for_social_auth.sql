ALTER TABLE usuario
    MODIFY password VARCHAR(255) NULL;

ALTER TABLE usuario
    ADD COLUMN auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL' AFTER nombre,
    ADD COLUMN provider_user_id VARCHAR(255) NULL AFTER auth_provider;

UPDATE usuario
SET auth_provider = 'LOCAL'
WHERE auth_provider IS NULL;

ALTER TABLE usuario
    ADD CONSTRAINT uq_usuario_provider_user_id UNIQUE (auth_provider, provider_user_id);
