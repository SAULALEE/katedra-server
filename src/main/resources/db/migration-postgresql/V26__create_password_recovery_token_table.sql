CREATE TABLE token_recuperacion_contrasena (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITHOUT TIME ZONE,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_token_recuperacion_contrasena PRIMARY KEY (id),
    CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_token_recuperacion_hash UNIQUE (token_hash)
);

CREATE INDEX idx_token_recuperacion_usuario_expira
    ON token_recuperacion_contrasena (usuario_id, expires_at);
