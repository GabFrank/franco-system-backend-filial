DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'financiero' AND table_name = 'terminal_pos' AND column_name = 'moneda_id'
    ) THEN
        ALTER TABLE financiero.terminal_pos ADD COLUMN moneda_id BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_terminal_pos_moneda'
    ) THEN
        ALTER TABLE financiero.terminal_pos
            ADD CONSTRAINT fk_terminal_pos_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
    END IF;
END $$;
