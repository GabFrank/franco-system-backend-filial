-- =====================================================================
-- terminal_pos: configuracion por aparato
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La configuracion general del modulo (V96.5) vale para toda la empresa: si el registro es
-- obligatorio, cuanta diferencia de monto se tolera, cuanto vive el QR de captura. Lo que no
-- cubre es que un aparato puntual necesite otra cosa.
--
-- Dos casos concretos:
--
-- 1. `carga_manual_permitida`: hay terminales donde tipear el cupon a mano es aceptable y otras
--    donde no --porque el cajero tiene el lector al lado y tipear es la puerta de entrada al
--    error--. Es por aparato, no por empresa.
--
-- 2. `campos_obligatorios`: que campos no se pueden dejar vacios al registrar la venta de ESTE
--    aparato. Hoy eso se deduce del `mapeo` del formato, que es del modelo; esto permite
--    apretarlo en una terminal puntual sin tocar el formato que comparten las demas.
--
-- ⚠️ EL INTERRUPTOR NO PUEDE APAGAR EL ULTIMO CAMINO
--
-- Restriccion que viene de §5.3.d del plan. El tipo del formato ya cierra caminos: WEB no
-- ofrece camara, MAQUINA no ofrece lector. Si ademas se apaga la carga manual en una terminal
-- cuyo unico camino restante ya esta cerrado, esa caja queda sin poder cobrar con tarjeta y
-- nadie avisa. La validacion vive en central (publisher), que es quien escribe.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL
--
-- Mismo caso que V98.5: son columnas nuevas sobre financiero.terminal_pos, que es MAIN_TO_ALL
-- y ya esta viva replicando con publicacion de fila completa (prattrs IS NULL). Si central va
-- primero, la siguiente escritura sobre terminal_pos manda columnas que esta filial no tiene y
-- el apply worker se detiene.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Nullable y sin CHECK. El default de `carga_manual_permitida` es NULL y no `true` a proposito:
-- NULL significa "lo que diga la configuracion general", y asi una fila vieja que todavia no
-- bajo de central no cambia de comportamiento sola.
-- =====================================================================
ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS carga_manual_permitida BOOLEAN NULL;

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS campos_obligatorios TEXT NULL;

COMMENT ON COLUMN financiero.terminal_pos.carga_manual_permitida IS
    'NULL = hereda la configuracion general. true/false = decidido para este aparato. No puede dejar a la terminal sin ningun camino para cobrar: lo valida central.';

COMMENT ON COLUMN financiero.terminal_pos.campos_obligatorios IS
    'JSON con la lista de campos que no se pueden dejar vacios al registrar la venta de este aparato. NULL = se deduce del mapeo del formato.';
