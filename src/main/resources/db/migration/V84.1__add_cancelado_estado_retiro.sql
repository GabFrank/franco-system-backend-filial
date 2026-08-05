-- Agrega el valor 'CANCELADO' al ENUM de estado de retiro, de forma idempotente.
-- La cancelacion se ejecuta en el central, pero financiero.retiro replica
-- central -> filial: si el enum local no conoce la etiqueta, el apply worker de la
-- suscripcion falla y la replicacion queda trabada acumulando WAL.
--
-- Por eso esta migracion tiene que estar aplicada en la filial ANTES de desplegar
-- el central con la mutation cancelarRetiro.
--
-- El tipo financiero.estado_retiro viene del dump base, no de una migracion de este
-- repo (la unica referencia previa es v32__identity_full.sql).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type t
        JOIN pg_enum e ON t.oid = e.enumtypid
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE t.typname = 'estado_retiro' AND n.nspname = 'financiero' AND e.enumlabel = 'CANCELADO'
    ) THEN
        ALTER TYPE financiero.estado_retiro ADD VALUE 'CANCELADO';
    END IF;
END $$;
