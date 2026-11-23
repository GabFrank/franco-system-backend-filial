-- Flyway:executeInTransaction=false

-- #265: indices de claves que la filial busca en cada venta y no tenian indice: cada busqueda leia la
-- tabla entera. Medido en general6 (copia de la sucursal 24) con EXPLAIN ANALYZE, en caliente / en
-- frio -> con indice:
--
-- 1. movimiento_stock por (tipo_movimiento, referencia): VentaItemService.save/saveAndSend lo busca
--    por CADA item al vender y al cancelar. 8 ms / 25 ms -> 0,04 ms (197 mil filas; la filial mas
--    grande tiene ~930 mil). Sin WHERE estado = true (el central lo tiene parcial): la consulta de la
--    filial no filtra por estado y un indice parcial no se usaria.
-- 2. venta_item por venta: ver venta, cancelar, deliverys. 11-14 ms / 780 ms -> 0,03 ms.
-- 3. factura_legal por venta: facturar o cancelar una venta. 5 ms / 320 ms -> 0,015 ms.
-- 4. factura_legal_item por factura: generar el DE, reimprimir. 7 ms / 206 ms -> 0,016 ms.
-- 5. movimiento_caja por caja: 7-9 ms / 245 ms -> 0,02 ms.
--
-- Solo indices: no cambian datos ni lo que se replica (la replicacion logica no lleva DDL).
-- CONCURRENTLY porque la filial se autoactualiza y migra al arrancar, a cualquier hora: no bloquea
-- las ventas mientras se crea. IF NOT EXISTS: si un CONCURRENTLY fallara quedaria un indice INVALID
-- que hay que borrar a mano (DROP INDEX CONCURRENTLY) antes de reintentar.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_movimiento_stock_tipo_referencia
    ON operaciones.movimiento_stock (tipo_movimiento, referencia);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_venta_item_venta
    ON operaciones.venta_item (venta_id, sucursal_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_legal_venta
    ON financiero.factura_legal (venta_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_factura_legal_item_factura
    ON financiero.factura_legal_item (factura_legal_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_movimiento_caja_caja
    ON financiero.movimiento_caja (caja_id);
