-- =====================================================================
-- Espejo de precio_por_sucursal.moneda_id y rastro de conversion en venta_item (central V260.5)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Los precios de venta pasan a tener moneda (NULL = la de la empresa). Cuando el precio esta en
-- otra moneda que la que cobra el PDV, el escritorio convierte y venta_item guarda el rastro:
-- moneda del precio de lista, precio de lista y cotizacion usada. precio sigue en la moneda en que
-- se cobro, asi lucro y reportes no cambian.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA DESPUES DEL CENTRAL (al reves que los espejos de siempre)
--
-- - venta_item SUBE al central (filial<N>_pub, sin lista de columnas): en cuanto esta filial tenga
--   las columnas las publica, y un central sin ellas deja la suscripcion en crash-loop ("missing
--   replicated column"). El central (V260.5) tiene que estar desplegado ANTES que este JAR.
-- - precio_por_sucursal BAJA por central_pub, que publica con lista de columnas: moneda_id no
--   llega hasta que se agrega a la lista, y eso se hace recien con toda la flota en esta version.
--
-- LADO SUBSCRIBER de precio_por_sucursal: sin FK (vive en el central). La filial escribe venta_item.
-- =====================================================================
ALTER TABLE productos.precio_por_sucursal ADD COLUMN IF NOT EXISTS moneda_id BIGINT;

COMMENT ON COLUMN productos.precio_por_sucursal.moneda_id IS
    'Moneda del precio. NULL = la moneda de la empresa (principal). Llega del central.';

ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_moneda_id BIGINT;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_lista NUMERIC;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_cotizacion NUMERIC;

COMMENT ON COLUMN operaciones.venta_item.precio_moneda_id IS
    'Moneda del precio de lista cuando difiere de la que se cobro. NULL = sin conversion.';
COMMENT ON COLUMN operaciones.venta_item.precio_lista IS
    'Precio de lista en precio_moneda_id. precio sigue en la moneda en que se cobro.';
COMMENT ON COLUMN operaciones.venta_item.precio_cotizacion IS
    'Unidades de la moneda de cobro por unidad de precio_moneda_id con que se convirtio el precio.';
