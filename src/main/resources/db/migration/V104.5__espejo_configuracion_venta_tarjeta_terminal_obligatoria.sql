-- =====================================================================
-- Espejo de central V232.5: configuracion_venta_tarjeta.terminal_obligatoria
-- =====================================================================
-- Si el PDV deja cerrar una venta con tarjeta sin haber elegido la terminal. true (default) = no:
-- onFinalizar exige la terminal antes de cerrar. Con false se vuelve al comportamiento de antes.
--
-- Por que existe: en farmacia filial 1, del 2026-09-25 al 28, 116 de 631 venta_tarjeta quedaron
-- sin terminal --el lector escribia en el cobro de atras y su Enter finalizaba la venta--.
--
-- ⚠️ ORDEN DE DESPLIEGUE: la tabla es MAIN_TO_ALL (central publica). Esta migracion va ANTES que la
-- del central en toda la flota: si central manda la columna y el subscriber no la tiene, el apply
-- worker de esa filial se detiene (mismo criterio que V96.5).
--
-- Mismo patron que V96.5: DEFAULT y sin NOT NULL. El default true es el lado seguro mientras la
-- fila replicada no traiga el valor.
-- =====================================================================
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS terminal_obligatoria BOOLEAN DEFAULT true;
