-- UTC timestamp of the last password reset; JWTs issued before it are rejected.
ALTER TABLE usuario ADD COLUMN password_changed_at TIMESTAMP WITHOUT TIME ZONE;
