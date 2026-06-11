ALTER TABLE financiero.tipo_gasto
    ADD COLUMN IF NOT EXISTS modulo_padre VARCHAR(30);
