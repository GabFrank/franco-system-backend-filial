-- =====================================================================
-- sorteos: alta de operaciones.sorteo_participacion en la publicacion de subida de la filial
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- sorteo_participacion es BRANCH_TO_MAIN. Hasta ahora entraba a la publicacion de la filial solo
-- si el ReplicationPublicationSyncScheduler del central llegaba a la filial por JDBC. Si no llega,
-- las participaciones se quedan en la filial y el central no las ve (listado, "Validar codigo").
-- Aca la publicacion se arma en la misma migracion.
--
-- ORDEN: va antes que el central (V285.3). Esta parte no depende del central.
--
-- REFRESH: no se puede dentro de la transaccion de Flyway. Lo hace el central al arrancar
-- (syncPublicationsWithReplicationTable, paso 4): refresca su filialX_sub para tomar esta tabla y
-- las suscripciones de esta filial a central_pub para tomar sorteo / sorteo_sucursal /
-- sorteo_premio. Toda participacion insertada antes de ese REFRESH no sube (copy_data=false).
--
-- La publicacion de subida se reconoce porque publica operaciones.venta y no empieza con
-- 'central_' (los nombres no siguen un patron fijo: bodega_filial24_pub, filial1_pub,
-- filial_farmacia_5_pub...). IDEMPOTENTE: pregunta antes de agregar; sin publicacion no hace nada.
-- =====================================================================
DO $$
DECLARE
    v_pub TEXT;
BEGIN
    FOR v_pub IN
        SELECT DISTINCT pt.pubname
        FROM pg_publication_tables pt
        WHERE pt.schemaname = 'operaciones'
          AND pt.tablename  = 'venta'
          AND pt.pubname NOT LIKE 'central\_%'
        ORDER BY pt.pubname
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_publication_tables x
            WHERE x.pubname = v_pub AND x.schemaname = 'operaciones' AND x.tablename = 'sorteo_participacion'
        ) THEN
            RAISE NOTICE 'operaciones.sorteo_participacion ya esta en %', v_pub;
        ELSE
            EXECUTE format('ALTER PUBLICATION %I ADD TABLE operaciones.sorteo_participacion', v_pub);
            RAISE NOTICE 'Agregada operaciones.sorteo_participacion a %', v_pub;
        END IF;
    END LOOP;

    IF v_pub IS NULL THEN
        RAISE NOTICE 'No se encontro publicacion de subida - se omite (replicacion no configurada todavia)';
    END IF;
END $$;
