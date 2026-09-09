CREATE TABLE usuario (
    id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    nombre VARCHAR(255) NOT NULL,
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_user_id VARCHAR(255),
    rol VARCHAR(50) NOT NULL DEFAULT 'ROLE_PROFESOR',
    plan VARCHAR(50) NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    ai_generation_count BIGINT NOT NULL DEFAULT 0,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    email_activo VARCHAR(255) GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN LOWER(email) ELSE NULL END
    ) STORED,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uq_usuario_provider_user_id UNIQUE (auth_provider, provider_user_id),
    CONSTRAINT uq_usuario_email_activo UNIQUE (email_activo)
);

CREATE TABLE asignatura (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(1000),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_asignatura PRIMARY KEY (id),
    CONSTRAINT fk_asignatura_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE INDEX idx_asignatura_usuario ON asignatura (usuario_id);

CREATE TABLE temario (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    asignatura_id VARCHAR(36) NOT NULL,
    favorito BOOLEAN NOT NULL DEFAULT FALSE,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    grado_academico VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_temario PRIMARY KEY (id),
    CONSTRAINT fk_temario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_temario_asignatura FOREIGN KEY (asignatura_id) REFERENCES asignatura(id)
);

CREATE INDEX idx_temario_usuario ON temario (usuario_id);
CREATE INDEX idx_temario_asignatura ON temario (asignatura_id);

CREATE TABLE contenido_temario (
    id VARCHAR(36) NOT NULL,
    temario_id VARCHAR(36) NOT NULL,
    estructura TEXT,
    teoria TEXT,
    contenido_fuente TEXT,
    evaluacion JSON,
    diapositivas JSON,
    modelo VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contenido_temario PRIMARY KEY (id),
    CONSTRAINT uk_temario UNIQUE (temario_id),
    CONSTRAINT fk_contenido_temario FOREIGN KEY (temario_id)
        REFERENCES temario(id) ON DELETE CASCADE
);

CREATE TABLE historial_evento (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    temario_id VARCHAR(36),
    temario_titulo VARCHAR(255) NOT NULL,
    asignatura_nombre VARCHAR(255),
    tipo VARCHAR(30) NOT NULL,
    detalle VARCHAR(500),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_historial_evento PRIMARY KEY (id),
    CONSTRAINT fk_historial_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE INDEX idx_historial_evento_usuario_created
    ON historial_evento (usuario_id, created_at DESC);

CREATE TABLE suscripcion (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    plan VARCHAR(50) NOT NULL,
    ciclo VARCHAR(50) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL,
    stripe_price_id VARCHAR(255) NOT NULL,
    periodo_inicio TIMESTAMP WITHOUT TIME ZONE,
    periodo_fin TIMESTAMP WITHOUT TIME ZONE,
    cancela_al_final BOOLEAN NOT NULL DEFAULT FALSE,
    cancelada_en TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_suscripcion PRIMARY KEY (id),
    CONSTRAINT fk_suscripcion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_suscripcion_stripe_subscription UNIQUE (stripe_subscription_id)
);

CREATE INDEX idx_suscripcion_usuario_created
    ON suscripcion (usuario_id, created_at DESC);

CREATE TABLE uso_diario (
    id VARCHAR(36) NOT NULL,
    usuario_id VARCHAR(36) NOT NULL,
    fecha DATE NOT NULL,
    generaciones INTEGER NOT NULL DEFAULT 0,
    exportaciones INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_uso_diario PRIMARY KEY (id),
    CONSTRAINT fk_uso_diario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_uso_diario_usuario_fecha UNIQUE (usuario_id, fecha)
);

CREATE INDEX idx_uso_diario_fecha ON uso_diario (fecha);

CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD
            AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_usuario_updated_at
BEFORE UPDATE ON usuario
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_asignatura_updated_at
BEFORE UPDATE ON asignatura
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_temario_updated_at
BEFORE UPDATE ON temario
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_contenido_temario_updated_at
BEFORE UPDATE ON contenido_temario
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
