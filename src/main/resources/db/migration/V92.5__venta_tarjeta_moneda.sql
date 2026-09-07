-- =====================================================================
-- venta_tarjeta: guardar la moneda del cobro
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- `monto` y `monto_escaneado` se guardan SIN unidad. La lista los muestra con la moneda de la
-- TERMINAL, que es configuracion mutable: al cambiar la moneda de una terminal, todo su
-- historico cambia de significado sin que ningun dato se haya tocado. Verificado el 2026-09-04:
-- pasar TPOS-BCD-01 a R$ hizo que cinco ventas hechas en guaranies se mostraran como reales.
--
-- Peor todavia, sin unidad la conciliacion miente en el sentido mas peligroso: un cupon de
-- 8.000 R$ contra un cobro de 8.000 Gs da diferencia CERO.
--
-- La moneda que corresponde es la del COBRO (cobro_detalle.moneda_id), no la de la terminal: es
-- el cobro el que se esta pagando.
--
-- ORDEN DE DESPLIEGUE
--
-- financiero.venta_tarjeta es BRANCH_TO_MAIN (ver V150.1): la filial publica y el central se
-- suscribe. Si el publisher manda una columna que el subscriber no tiene, la replicacion SE
-- CORTA. Por eso esta migracion (filial = publisher) va DESPUES que la del central.
--
-- Aditiva y nullable: las filas existentes quedan en NULL y el cliente cae al comportamiento
-- anterior. No hay backfill porque la funcionalidad todavia no se usa en produccion.
-- =====================================================================
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS moneda_id BIGINT;

COMMENT ON COLUMN financiero.venta_tarjeta.moneda_id IS
    'Moneda del cobro que este registro respalda. Sin esto, monto y monto_escaneado no tienen unidad y la conciliacion puede cuadrar entre monedas distintas.';
