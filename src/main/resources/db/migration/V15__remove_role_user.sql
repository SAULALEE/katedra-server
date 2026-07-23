-- Migrar cualquier usuario con ROLE_USER a ROLE_PROFESOR
UPDATE usuario SET rol = 'ROLE_PROFESOR' WHERE rol = 'ROLE_USER';

-- Corregir el DEFAULT incorrecto de la migración V1
ALTER TABLE usuario MODIFY COLUMN rol VARCHAR(50) NOT NULL DEFAULT 'ROLE_PROFESOR';
