DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'financiero'
          AND table_name = 'tipo_gasto'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'financiero'
              AND table_name = 'tipo_gasto'
              AND column_name = 'activo_en_sucursales'
        ) THEN
            ALTER TABLE financiero.tipo_gasto ADD COLUMN activo_en_sucursales BOOLEAN;
        END IF;

        ALTER TABLE financiero.tipo_gasto ALTER COLUMN activo_en_sucursales SET DEFAULT TRUE;
        UPDATE financiero.tipo_gasto SET activo_en_sucursales = TRUE WHERE activo_en_sucursales IS NULL;
        ALTER TABLE financiero.tipo_gasto ALTER COLUMN activo_en_sucursales SET NOT NULL;
    END IF;
END $$;
