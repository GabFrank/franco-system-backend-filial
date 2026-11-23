-- =====================================================================
-- configuracion_cobro: tope del descuento del cobro (espejo de V272.3 del central)
-- =====================================================================
-- Igual que el tope del aumento (V106.1), ahora el descuento del cobro (F9 y delivery) tiene un tope
-- configurable desde el central: con tope (porcentaje del total) o sin tope.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DEL CENTRAL (V272.3)
--
-- La tabla ya esta en la publicacion (MAIN_TO_ALL). Si el central publica una fila con columnas que
-- esta filial no tiene, el apply falla ("missing replicated columns") y la suscripcion se traba.
-- Agregar columnas NO requiere REFRESH de la suscripcion.
--
-- ESTE ES EL LADO SUBSCRIBER: columnas nullable, sin DEFAULT ni CHECK. NULL se lee como
-- "sin tope" (ConfiguracionCobro.getDescuentoConTope), que es lo que pasaba antes.
-- =====================================================================
ALTER TABLE financiero.configuracion_cobro ADD COLUMN IF NOT EXISTS descuento_con_tope BOOLEAN;
ALTER TABLE financiero.configuracion_cobro ADD COLUMN IF NOT EXISTS descuento_tope_porcentaje NUMERIC(6, 2);

COMMENT ON COLUMN financiero.configuracion_cobro.descuento_con_tope IS
    'true = el descuento del cobro no puede pasar descuento_tope_porcentaje del total; false = sin tope. NULL = false.';

COMMENT ON COLUMN financiero.configuracion_cobro.descuento_tope_porcentaje IS
    'Tope del descuento, en porcentaje del total del cobro. NULL = 15.';
