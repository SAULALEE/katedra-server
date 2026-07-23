CREATE TABLE asignatura (
    id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    usuario_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_asignatura_usuario (usuario_id),
    CONSTRAINT fk_asignatura_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELETE FROM contenido_temario;
DELETE FROM temario;

ALTER TABLE temario
    DROP COLUMN asignatura,
    ADD COLUMN asignatura_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL AFTER usuario_id,
    ADD INDEX idx_temario_asignatura (asignatura_id),
    ADD CONSTRAINT fk_temario_asignatura FOREIGN KEY (asignatura_id) REFERENCES asignatura(id);
