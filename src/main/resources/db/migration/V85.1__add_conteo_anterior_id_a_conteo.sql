-- Versionado de conteos de apertura/cierre de caja.
-- Al editar un conteo no se sobreescriben los montos: se inserta un conteo nuevo
-- y se enlaza con el que reemplaza, de modo que quede el historial completo
-- (quien edito y cuando salen de usuario_id y creado_en del conteo nuevo).
-- Sin FK a proposito: la tabla se replica y una self-FK depende del orden de apply.
ALTER TABLE financiero.conteo ADD COLUMN IF NOT EXISTS conteo_anterior_id bigint;

CREATE INDEX IF NOT EXISTS conteo_conteo_anterior_id_idx
    ON financiero.conteo (conteo_anterior_id, sucursal_id);

COMMENT ON COLUMN financiero.conteo.conteo_anterior_id IS
    'Versionado: apunta al conteo que esta version reemplaza. NULL = version original.';
