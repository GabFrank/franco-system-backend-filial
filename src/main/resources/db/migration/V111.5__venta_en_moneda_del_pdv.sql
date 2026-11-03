-- =====================================================================
-- Venta en la moneda del PDV (entrega 4 de la moneda por modulo). Par del central V267.5.
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El PDV puede operar en otra moneda que la de la empresa (modulo PDV, por sucursal). Las columnas
-- de siempre (venta.total_gs, venta_item.precio, cobro.total_gs) siguen en la moneda de la empresa
-- (la principal de financiero): son el equivalente, y con ellas lucro, caja, reportes y el bot no
-- cambian. Las nuevas guardan la venta en su propia moneda:
--
--   venta.moneda_id            moneda en que opero el PDV. NULL = la de la empresa (ventas viejas).
--   venta.cotizacion           unidades de la moneda de la empresa por unidad de venta.moneda_id.
--   venta.total_moneda         total de la venta en venta.moneda_id.
--   venta_item.precio_en_moneda   precio unitario cobrado, en venta.moneda_id.
--   cobro.total_moneda         total del cobro en la moneda de la venta.
--   cobro_detalle.cotizacion   cotizacion precisa de la linea (moneda de la empresa por unidad de
--                              cobro_detalle.moneda_id); cambio sigue grabandose como siempre.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL CENTRAL (V267.5) VA ANTES
--
-- Estas cuatro tablas suben de la filial al central por filial<N>_pub, SIN lista de columnas: en
-- cuanto esta migracion corre, la filial publica las columnas nuevas, y un central que no las tenga
-- deja la suscripcion en crash-loop ("missing replicated column"). Verificar 267.5 en el
-- flyway_schema_history del central del canal antes de liberar este JAR.
--
-- Lo que baja del central por central_filial<N>_pub lleva lista de columnas: las nuevas no viajan
-- hasta ampliarla, y no hace falta para operar.
-- =====================================================================
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS moneda_id BIGINT;
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS cotizacion NUMERIC;
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS total_moneda NUMERIC;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_en_moneda NUMERIC;
ALTER TABLE operaciones.cobro ADD COLUMN IF NOT EXISTS total_moneda NUMERIC;
ALTER TABLE operaciones.cobro_detalle ADD COLUMN IF NOT EXISTS cotizacion NUMERIC;

COMMENT ON COLUMN operaciones.venta.moneda_id IS
    'Moneda en que opero el PDV. NULL = la moneda de la empresa. total_gs sigue en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.venta.cotizacion IS
    'Unidades de la moneda de la empresa por unidad de moneda_id al cerrar la venta.';
COMMENT ON COLUMN operaciones.venta.total_moneda IS
    'Total de la venta en moneda_id. total_gs es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.venta_item.precio_en_moneda IS
    'Precio unitario cobrado en la moneda de la venta. precio es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.cobro.total_moneda IS
    'Total del cobro en la moneda de la venta. total_gs es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.cobro_detalle.cotizacion IS
    'Unidades de la moneda de la empresa por unidad de moneda_id, con decimales. cambio sigue como siempre (entero).';
