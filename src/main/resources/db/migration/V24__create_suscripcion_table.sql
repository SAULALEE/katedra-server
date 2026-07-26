-- Billing history. One row per Stripe subscription; a user who cancels and resubscribes
-- accumulates rows. Never soft-deleted: cancellation is a state, not a removal.
--
-- uq_suscripcion_stripe_subscription is load-bearing, not just hygiene: it is the upsert
-- key that keeps syncing from Stripe idempotent when the webhook and the client-confirm
-- path both fire for the same payment.
CREATE TABLE suscripcion (
    id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    usuario_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    plan VARCHAR(50) NOT NULL,
    ciclo VARCHAR(50) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL,
    stripe_price_id VARCHAR(255) NOT NULL,
    periodo_inicio TIMESTAMP NULL DEFAULT NULL,
    periodo_fin TIMESTAMP NULL DEFAULT NULL,
    cancela_al_final BOOLEAN NOT NULL DEFAULT FALSE,
    cancelada_en TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_suscripcion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT uq_suscripcion_stripe_subscription UNIQUE (stripe_subscription_id),
    INDEX idx_suscripcion_usuario_created (usuario_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
