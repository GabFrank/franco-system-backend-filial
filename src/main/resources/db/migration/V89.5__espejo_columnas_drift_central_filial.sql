-- Espejo de columnas/enum agregados en CENTRAL que la replicación lógica NO propaga
-- (la DDL no se replica; solo el DML). Sin estas columnas en el filial, el apply worker
-- central→filial crashea al recibir un UPDATE/INSERT de la tabla afectada:
--   ERROR: logical replication target relation "..." is missing replicated columns
-- → crash-loop cada 5s, pid de worker vacío, slot inactivo con WAL retenido creciendo.
--
-- Auditoría 2026-08-12 sobre las 70 tablas publicadas central→filial (central_pub +
-- central_alpha_filial2_pub, 739 columnas): 5 columnas faltantes + 1 enum. Verificado además
-- que NO hay drift en la dirección inversa (filial→central) ni value-drift en enums publicados.
--
-- Todo ADITIVO, IF NOT EXISTS, subscriber-safe: SIN FKs ni índices únicos, porque las tablas
-- referenciadas (financiero.caja_virtual, movimiento_caja_virtual) son central-only y pueden
-- no existir en el filial — una FK haría fallar el apply cuando la fila referenciada no está.
-- Definiciones espejo de central: V112.1 (enum tipo_local + sucursal), V114.1/V179.5 (retiro),
-- V0 (configuraciones.actualizacion.sucursal_id).

-- 1) enum empresarial.tipo_local (espejo de central V112.1) — CREATE TYPE no soporta IF NOT EXISTS
DO $$ BEGIN
    CREATE TYPE empresarial.tipo_local AS ENUM ('VENTA', 'DEPOSITO', 'ADMINISTRATIVO', 'VIRTUAL');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 2) empresarial.sucursal: tipo_local + manejo_stock (columnas del error original)
ALTER TABLE empresarial.sucursal
    ADD COLUMN IF NOT EXISTS tipo_local   empresarial.tipo_local DEFAULT 'VENTA',
    ADD COLUMN IF NOT EXISTS manejo_stock boolean               DEFAULT true;

-- 3) financiero.retiro: columnas del puente Retiro→caja mayor (las setea el poller en central).
--    Sin FK a financiero.caja_virtual/movimiento_caja_virtual a propósito (central-only).
ALTER TABLE financiero.retiro
    ADD COLUMN IF NOT EXISTS caja_virtual_id            bigint,
    ADD COLUMN IF NOT EXISTS movimiento_caja_virtual_id bigint;

-- 4) configuraciones.actualizacion: sucursal_id (espejo de central V0, NOT NULL DEFAULT 0)
ALTER TABLE configuraciones.actualizacion
    ADD COLUMN IF NOT EXISTS sucursal_id bigint NOT NULL DEFAULT 0;
