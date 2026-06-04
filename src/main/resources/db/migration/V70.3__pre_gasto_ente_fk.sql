-- Integridad referencial: pre_gasto.ente_id -> activos.ente(id)

DO $$
BEGIN
    IF to_regclass('financiero.pre_gasto') IS NOT NULL THEN
        UPDATE financiero.pre_gasto pg
        SET ente_id = NULL
        WHERE ente_id IS NOT NULL
          AND NOT EXISTS (
            SELECT 1 FROM activos.ente e WHERE e.id = pg.ente_id
          );

        CREATE INDEX IF NOT EXISTS idx_pre_gasto_ente_id
            ON financiero.pre_gasto (ente_id);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'pre_gasto_ente_id_fkey'
        ) THEN
            ALTER TABLE financiero.pre_gasto
                ADD CONSTRAINT pre_gasto_ente_id_fkey
                FOREIGN KEY (ente_id) REFERENCES activos.ente (id);
        END IF;
    END IF;
END$$;
