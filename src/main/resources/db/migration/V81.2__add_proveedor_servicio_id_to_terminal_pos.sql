-- DDL espejo del central V152.1: columna nueva en financiero.terminal_pos.
--
-- CRITICO para la replicacion: financiero.terminal_pos esta replicada MAIN_TO_ALL.
-- Si el central publica una columna que el subscriber no tiene, el apply worker
-- falla con "logical replication target relation ... is missing replicated column"
-- y se detiene la suscripcion COMPLETA de esa filial, acumulando WAL en el central.
-- Por eso esta migracion se despliega ANTES que la del central.
--
-- Sin FK a proposito (ver V81.1): el orden del initial sync entre tablas no esta
-- garantizado. El filial no expone este campo por GraphQL — TerminalPos es read-only acá.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'financiero'
          AND table_name = 'terminal_pos'
          AND column_name = 'proveedor_servicio_id'
    ) THEN
        ALTER TABLE financiero.terminal_pos ADD COLUMN proveedor_servicio_id BIGINT;
    END IF;
END $$;
