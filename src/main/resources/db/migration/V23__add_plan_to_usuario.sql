-- Billing tier, denormalized from the latest suscripcion row so the plan gates do not
-- need a join on every generation request. Existing users default to FREE.
ALTER TABLE usuario
    ADD COLUMN plan VARCHAR(50) NOT NULL DEFAULT 'FREE' AFTER rol;
