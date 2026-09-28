-- =====================================================================
-- terminal_pos: donde esta la maquina, y cual es
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La tabla no tiene sucursal_id. Con 24 sucursales y un proveedor que entrega 30 maquinas, no
-- hay forma de saber que aparato esta en que local. Y tampoco hay donde guardar el
-- identificador propio de la maquina --el que viene de fabrica y el que el propio cupon
-- imprime--.
--
-- `codigo` NO sirve para eso: es la etiqueta interna que el negocio le pega al aparato para
-- que el cajero la escanee con el lector (scan-terminal-pos-dialog), y hoy esta vacia en las
-- dos terminales que existen.
--
-- Caso de uso: la maquina JF798SJJ del proveedor X esta en la sucursal Y, cobra a la cuenta Z,
-- y su cupon se lee con el formato W.
--
-- POR QUE IMPORTA QUE `serie` EXISTA
--
-- El `mapeo` del formato ya declara `terminal` como campo canonico, o sea que el cupon ya
-- imprime el identificador del aparato. Hoy no hay contra que cotejarlo. Con `serie` cargada,
-- el cupon dice solo de que maquina salio --y si esa maquina esta registrada en otra sucursal,
-- el sistema lo puede cantar--.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL
--
-- financiero.terminal_pos es MAIN_TO_ALL y **ya esta viva replicando**, con publicacion de
-- fila completa: en central, pg_publication_rel.prattrs IS NULL para esta tabla. Eso significa
-- que cada UPDATE manda la tupla entera, columnas nuevas incluidas.
--
-- Si central agrega las columnas primero, la primera escritura sobre terminal_pos --por
-- ejemplo el SQL de asignacion de formato-- manda dos columnas que esta filial no tiene, y el
-- apply worker SE DETIENE. Es el mecanismo del incidente de tipo_dispositivo del 2026-08-20,
-- no el caso blando de una tabla nueva (ahi el REFRESH falla entero y no rompe nada).
--
-- Regla corta: columna nueva sobre tabla MAIN_TO_ALL que ya se replica => filial primero,
-- sin excepcion.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Sin FK a sucursal, sin indices unicos sobre serie, sin CHECK: esta filial no escribe esta
-- tabla, solo recibe. Las restricciones viven en central, que es el publisher (V224.5). Poner
-- un unique aca solo agregaria una forma de cortar la replicacion si central acepta algo que
-- la filial rechaza.
-- =====================================================================
ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL;

ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS serie VARCHAR(60) NULL;

COMMENT ON COLUMN financiero.terminal_pos.sucursal_id IS
    'En que sucursal esta fisicamente el aparato. NULL en las filas viejas: no se puede adivinar, se completa a mano. La replicacion NO se filtra por esta columna; el caso de uso es de listado, no de aislamiento.';

COMMENT ON COLUMN financiero.terminal_pos.serie IS
    'Identificador propio de la maquina, el que viene de fabrica y el que el cupon imprime. Distinto de `codigo`, que es la etiqueta interna que el cajero escanea.';
