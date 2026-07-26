CREATE TABLE historial_evento (
    id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    usuario_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    temario_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    temario_titulo VARCHAR(255) NOT NULL,
    asignatura_nombre VARCHAR(255) NULL,
    tipo VARCHAR(30) NOT NULL,
    detalle VARCHAR(500) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_historial_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    INDEX idx_historial_evento_usuario_created (usuario_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
