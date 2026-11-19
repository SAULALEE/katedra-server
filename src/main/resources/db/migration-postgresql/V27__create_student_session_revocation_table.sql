CREATE TABLE student_session_revocation (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    session_key_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_student_session_revocation PRIMARY KEY (id),
    CONSTRAINT fk_student_session_revocation_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_student_session_revocation_hash UNIQUE (session_key_hash)
);

CREATE INDEX idx_student_session_revocation_expires_at
    ON student_session_revocation (expires_at);
