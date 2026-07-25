-- Per-day quota accounting, one row per user per active day.
--
-- Chosen over a (fecha, contador) column pair on usuario because the day rollover becomes
-- implicit: a new date is a new row, so there is no read-compare-reset that could race at
-- midnight. It also keeps usuario lean, a table the JWT filter reads on every request.
--
-- uq_uso_diario_usuario_fecha is what lets the row be created with a blind INSERT IGNORE:
-- concurrent first-requests of the day race harmlessly.
CREATE TABLE uso_diario (
    id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    usuario_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    fecha DATE NOT NULL,
    generaciones INT NOT NULL DEFAULT 0,
    exportaciones INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_uso_diario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_uso_diario_usuario_fecha UNIQUE (usuario_id, fecha),
    INDEX idx_uso_diario_fecha (fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
