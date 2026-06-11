DO $$
BEGIN
    IF to_regclass('financiero.pre_gasto') IS NOT NULL THEN
        ALTER TABLE financiero.pre_gasto
            ADD COLUMN IF NOT EXISTS caja_id BIGINT;
    END IF;
END$$;

DO $$
BEGIN
    IF to_regclass('financiero.pre_gasto') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_pre_gasto_caja_id
            ON financiero.pre_gasto(caja_id);
    END IF;
END$$;
