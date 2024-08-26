ALTER TABLE financiero.pdv_caja DROP CONSTRAINT pdv_caja_maletin_id_fkey;
ALTER TABLE financiero.pdv_caja ADD CONSTRAINT pdv_caja_maletin_id_fkey FOREIGN KEY (maletin_id) REFERENCES financiero.maletin(id) ON DELETE SET NULL ON UPDATE CASCADE;
DELETE FROM financiero.maletin;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'financiero'
        AND table_name = 'maletin'
        AND column_name = 'sucursal_id'
    ) THEN
        ALTER TABLE financiero.maletin ADD COLUMN sucursal_id int8 NOT NULL;
    END IF;
END $$;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'financiero'
        AND table_name = 'maletin'
        AND constraint_name = 'maletin_sucursal_fk'
    ) THEN
        ALTER TABLE financiero.maletin
        ADD CONSTRAINT maletin_sucursal_fk
        FOREIGN KEY (sucursal_id)
        REFERENCES empresarial.sucursal(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;
    END IF;
END $$;
