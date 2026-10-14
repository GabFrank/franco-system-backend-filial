-- =====================================================================
-- Espejo de central V221.3 (gasto.solicitud_pago_id) y V181.5 (venta_credito_cuota.monto_cobrado,
-- estado_cobro)
-- =====================================================================
-- El central de farmacia publica estas columnas a cada filial en central_filial{N}_pub (filtro
-- sucursal_id = N) y la filial no las tenia. Bodega hoy no publica ninguna de las dos tablas.
-- Con la primera fila que central toca, el apply worker se detiene:
--
--   logical replication target relation "financiero.gasto" is missing replicated column: "solicitud_pago_id"
--
-- Paso en farmacia filial 4 el 2026-10-14 (gasto 30, finalizado por central): todo lo que central
-- publica solo para esa sucursal quedo trabado. Se destrabo con este mismo ALTER a mano en las
-- 4 filiales de farmacia; por eso IF NOT EXISTS. venta_credito_cuota todavia no fallo porque CPC no
-- cobro ninguna cuota de filial, pero es el mismo mecanismo.
--
-- La filial no lee ni escribe estas columnas: existen para que el apply worker pueda aplicar lo que
-- manda central. Mismos tipos y defaults que central; sin el indice unico uk_gasto_solicitud_pago de
-- V221.3 (lo mantiene central, donde nacen esos gastos).
--
-- Invariante: la filial no actualiza venta_credito_cuota (solo la inserta al crear la venta). Su
-- filial{N}_pub no tiene lista de columnas, asi que un UPDATE nacido aca mandaria monto_cobrado y
-- estado_cobro a central y pisaria un cobro hecho alla.
--
-- lock_timeout: ADD COLUMN toma ACCESS EXCLUSIVE. Mejor fallar el arranque que colgar el health
-- check de check-update detras de una transaccion larga: PG revierte la migracion entera y el
-- proximo reinicio del servicio la vuelve a intentar.
-- =====================================================================
SET LOCAL lock_timeout = '10s';

ALTER TABLE financiero.gasto
    ADD COLUMN IF NOT EXISTS solicitud_pago_id BIGINT;

ALTER TABLE financiero.venta_credito_cuota
    ADD COLUMN IF NOT EXISTS monto_cobrado NUMERIC(18,4) DEFAULT 0;

ALTER TABLE financiero.venta_credito_cuota
    ADD COLUMN IF NOT EXISTS estado_cobro VARCHAR(20);
