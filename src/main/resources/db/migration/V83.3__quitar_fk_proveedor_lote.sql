-- Corrige un defecto de V82.3 que habria detenido la replicacion de esta filial.
--
-- V82.3 creo operaciones.lote con una FK a personas.proveedor copiada del central. El problema:
-- personas.proveedor NO se replica a las filiales. No esta registrada en
-- configuraciones.replication_table ni incluida en central_pub, y general6 tiene 0 proveedores.
--
-- Cuando el central crea un lote con proveedor (LoteService lo toma de la recepcion) y esa fila
-- baja por central_pub, el apply worker del subscriber falla con violacion de FK y LA REPLICACION
-- DE ESTA FILIAL SE DETIENE POR COMPLETO, no solo para esta tabla.
--
-- Es el mismo problema que ya se evito con movimiento_stock_lote.lote_id: en la filial no se
-- ponen FKs hacia tablas que llegan por otra publicacion o que directamente no se replican.
--
-- La columna proveedor_id se mantiene: el dato sigue bajando y es util para trazabilidad
-- (que proveedor entrego ese lote). Solo se pierde la validacion referencial local, que en un
-- subscriber no aporta: la integridad la garantiza el central, que es el unico que escribe.
--
-- Las otras FKs de la tabla se mantienen porque sus tablas si se replican:
--   - productos.producto  -> MAIN_TO_ALL, en central_pub
--   - personas.usuario    -> MAIN_TO_ALL, en central_pub (misma publicacion que lote, asi que
--                            PostgreSQL preserva el orden entre ambas)
ALTER TABLE operaciones.lote DROP CONSTRAINT IF EXISTS fk_lote_proveedor;

COMMENT ON COLUMN operaciones.lote.proveedor_id IS
    'Proveedor que entrego el lote. Sin FK en la filial: personas.proveedor no se replica (ver V83.3).';
