-- =====================================================================
-- venta_tarjeta: datos adicionales del cupon, por proveedor
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La tabla tiene columnas fijas: codigo_autorizacion, numero_boleta, monto. Cada proveedor
-- nuevo con un campo propio obligaba a una migracion, un release y la propagacion a 24
-- filiales.
--
-- El 2026-09-09 aparecio el quinto formato, PlugPay, y trajo el caso que lo vuelve concreto:
-- imprime DOS montos en DOS monedas en el mismo ticket (USD113.24 y PYG661.309). Cual es el
-- de la venta no es una constante del sistema: es configuracion del proveedor.
--
-- COMO SE USA
--
-- El `mapeo` del formato declara que valor extraido va a cada campo canonico (monto,
-- codigo_autorizacion, numero_boleta, terminal). Todo lo demas cae aca como clave-valor. Un
-- proveedor nuevo con campos propios --STONEID, COD.TRANS., lo que aparezca-- se resuelve
-- desde el ABM: sin codigo, sin migracion, sin propagar nada.
--
-- Es la misma mecanica que permite que FRCP1 sume `terminal` sin release.
--
-- ORDEN DE DESPLIEGUE
--
-- financiero.venta_tarjeta es BRANCH_TO_MAIN: la filial publica y el central se
-- suscribe. Si el publisher manda una columna que el subscriber no tiene, la replicacion SE
-- CORTA. Por eso el espejo de central (V220.5) va ANTES que esta. Esta es la del publisher.
--
-- Aditiva y nullable: las filas existentes quedan en NULL y el codigo anterior las ignora, asi
-- que el rollback al JAR previo sigue funcionando contra este esquema.
-- =====================================================================
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS datos_extra JSONB;

COMMENT ON COLUMN financiero.venta_tarjeta.datos_extra IS
    'Campos del cupon que no son canonicos, como clave-valor. El mapeo del formato decide cual valor va a cada columna fija y todo lo demas queda aca, para que un proveedor nuevo no cueste una migracion.';
