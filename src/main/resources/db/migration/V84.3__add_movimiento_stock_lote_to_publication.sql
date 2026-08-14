-- Control de lotes - Fase 2 (Ventas). Habilita el canal filial -> central del ledger por lote.
--
-- V81.3 creo operaciones.movimiento_stock_lote en esta base, pero NUNCA la agrego a la publicacion
-- de la filial. Verificado en general6: bodega_filial24_pub publica operaciones.movimiento_stock
-- pero no su hija movimiento_stock_lote.
--
-- Hasta Fase 1 eso no molestaba: la filial solo RECIBIA filas (las entradas por compra bajan por
-- central_filialX_pub, que es otra publicacion y si la incluye). Pero en Fase 2 la filial empieza a
-- ESCRIBIR el desglose de cada venta. Sin esta migracion el movimiento agregado sube y su desglose
-- no, asi que el central veria la venta en movimiento_stock y seguiria contando ese stock como
-- disponible en v_stock_lote. Los dos numeros se separan en silencio desde la primera venta.
--
-- No hace falta tocar configuraciones.replication_table: esa tabla solo existe en el central y ahi
-- operaciones.movimiento_stock_lote ya esta registrada como BRANCH_TO_MAIN. Lo que faltaba es el
-- lado publicador, que en la filial se maneja por migracion (mismo patron que V50 y V62.7).
--
-- El nombre de la publicacion es dinamico (bodega_filial24_pub, beta_filial2_pub, ...), asi que se
-- descubre. Se excluyen las 'central_%' porque esas son las de bajada, no las de subida.
--
-- Despues de aplicar, el suscriptor del central necesita REFRESH PUBLICATION para tomar la tabla.
-- Lo hace solo ReplicationRefreshScheduler (copy_data = false); tambien se puede forzar con la
-- mutation refreshSubscription. Al ir con copy_data = false las filas previas no se copian: es
-- irrelevante porque en esta base la tabla arranca sin movimientos propios.
DO $$
DECLARE
    pub_name TEXT;
BEGIN
    SELECT pubname INTO pub_name
    FROM pg_publication
    WHERE pubname LIKE '%_filial%_pub'
      AND pubname NOT LIKE 'central_%'
    LIMIT 1;

    IF pub_name IS NULL THEN
        RAISE NOTICE 'No se encontro publicacion de filial - se omite (replicacion no configurada todavia)';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = pub_name
          AND schemaname = 'operaciones'
          AND tablename = 'movimiento_stock_lote'
    ) THEN
        RAISE NOTICE 'operaciones.movimiento_stock_lote ya esta en la publicacion %', pub_name;
        RETURN;
    END IF;

    EXECUTE format('ALTER PUBLICATION %I ADD TABLE operaciones.movimiento_stock_lote', pub_name);
    RAISE NOTICE 'Agregada operaciones.movimiento_stock_lote a la publicacion %', pub_name;
END $$;
