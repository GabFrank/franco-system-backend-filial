-- Control de lotes - arregla el canal de SUBIDA que V84.3 creyo haber arreglado, y backfillea lo
-- que quedo del lado del central.
--
-- ============================================================================
-- PROBLEMA 1 - V84.3 es un no-op en farmacia por un patron LIKE mal escrito.
--
-- V84.3 buscaba la publicacion de esta base asi:
--
--     WHERE pubname LIKE '%_filial%_pub' AND pubname NOT LIKE 'central_%'
--
-- Ese patron exige un guion bajo ANTES de "filial". Se escribio mirando bodega_filial24_pub, que
-- lo cumple. Pero las publicaciones de farmacia se llaman filial1_pub, filial3_pub, filial4_pub,
-- filial_farmacia_5_pub y filial_farmacia_6_pub: NINGUNA empieza con algo seguido de "_filial".
--
-- Resultado: pub_name quedaba NULL, la migracion tomaba la rama del RAISE NOTICE y terminaba con
-- success = t sin haber tocado nada. Verificado en produccion el 2026-09-08: filial4_pub publicaba
-- operaciones.movimiento_stock pero no su hija movimiento_stock_lote, y el central no tenia ni una
-- fila del ledger con id par (los pares los genera la filial). O sea que ninguna venta por lote
-- subio jamas, y el central siguio contando como disponible el stock que esta boca ya habia
-- vendido. Es exactamente el desfase silencioso que la propia V84.3 describia como el motivo de
-- existir.
--
-- La leccion no es corregir el patron, es no depender del nombre. Aca la publicacion se descubre
-- por su CONTENIDO: la de subida es la que ya publica la tabla padre operaciones.movimiento_stock.
-- Si el ledger sube, su hija tambien tiene que subir, se llame como se llame la publicacion.
--
-- ============================================================================
-- PROBLEMA 2 - lo que ya existia en el central no baja solo.
--
-- Mismo razonamiento que V87.3: los REFRESH PUBLICATION van con copy_data = false, asi que
-- enganchan el stream de ahi en adelante y no copian nada de lo previo. Toda filial dada de alta
-- despues de que el central empezara a cargar lotes nace con el hueco.
--
-- V87.3 ya traia este backfill pero no llego a servir, por dos motivos que aca se corrigen:
--
--   a) Sacaba el sucursal_id de configuraciones.local, que en TODAS las filiales de farmacia esta
--      en 0. El filtro quedaba WHERE sucursal_id = 0 y el ledger no traia una sola fila.
--   b) Corria en agosto, antes de que existieran los lotes que hoy faltan.
--
-- El backfill va por dblink con ON CONFLICT DO NOTHING y NO por copy_data = true a proposito. Con
-- copia inicial nativa el conflicto de PK deja el tablesync reintentando el mismo LSN para
-- siempre; con ON CONFLICT la fila repetida simplemente se ignora. Verificado en produccion: un
-- REFRESH con copy_data = true en el canal de SUBIDA devolvia al central las filas que acababan de
-- bajar y trababa la suscripcion entera.
-- ============================================================================


-- ============================================================================
-- Parte 1 - publicar la hija junto con la madre.
-- ============================================================================
DO $$
DECLARE
    pub_name TEXT;
BEGIN
    -- Se descubre por contenido, no por nombre (ver PROBLEMA 1). Las 'central_%' son las de
    -- bajada: esas las publica el central, no esta base.
    SELECT pt.pubname INTO pub_name
    FROM pg_publication_tables pt
    WHERE pt.schemaname = 'operaciones'
      AND pt.tablename  = 'movimiento_stock'
      AND pt.pubname NOT LIKE 'central\_%'
    ORDER BY pt.pubname
    LIMIT 1;

    IF pub_name IS NULL THEN
        RAISE NOTICE 'No se encontro publicacion de subida - se omite (replicacion no configurada todavia)';
    ELSIF EXISTS (
        SELECT 1 FROM pg_publication_tables x
        WHERE x.pubname    = pub_name
          AND x.schemaname = 'operaciones'
          AND x.tablename  = 'movimiento_stock_lote'
    ) THEN
        RAISE NOTICE 'operaciones.movimiento_stock_lote ya esta en la publicacion %', pub_name;
    ELSE
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE operaciones.movimiento_stock_lote', pub_name);
        RAISE NOTICE 'Agregada operaciones.movimiento_stock_lote a la publicacion %', pub_name;
    END IF;
END $$;


-- ============================================================================
-- Parte 2 - backfill de lo que quedo del lado del central.
--
-- Todo el bloque falla suave, igual que V87.3: si no hay suscripcion, si falta dblink o si el
-- central esta caido, avisa y la migracion termina OK. Acoplar el arranque de la filial a que el
-- central responda seria peor que el hueco que arregla, y el bloque se puede reintentar tal cual
-- porque es idempotente.
--
-- Orden: primero el maestro y despues el ledger, para que lote_id resuelva apenas termina en vez
-- de dejar la vista degradada hasta el proximo refresh.
-- ============================================================================
DO $$
DECLARE
    v_conninfo      TEXT;
    v_sucursal      BIGINT;
    v_dblink_schema TEXT;
    v_lotes_ok      BIGINT := 0;
    v_ledger_ok     BIGINT := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'dblink') THEN
        CREATE EXTENSION dblink;
        RAISE NOTICE 'Extension dblink creada';
    END IF;

    -- Donde vive dblink() cambia por base (en 'general' quedo fuera de public), asi que se resuelve
    -- el esquema real y se califica cada llamada en vez de tocar el search_path.
    SELECT n.nspname INTO v_dblink_schema
    FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'dblink';

    -- central_pub es la publicacion MAIN_TO_ALL y solo el central la tiene, asi que la suscripcion
    -- que la consume es por definicion la que apunta al central. Su conninfo ya trae host, puerto,
    -- base y credenciales: no hace falta ningun parametro nuevo por entorno.
    SELECT subconninfo INTO v_conninfo
    FROM pg_subscription
    WHERE 'central_pub' = ANY(subpublications)
    ORDER BY subname
    LIMIT 1;

    IF v_conninfo IS NULL THEN
        RAISE WARNING 'Backfill de lotes omitido: esta base no tiene suscripcion contra central_pub.';
        RETURN;
    END IF;

    -- El sucursal_id NO se toma solo de configuraciones.local: en todas las filiales de farmacia
    -- esa columna quedo en 0, que fue lo que dejo sin efecto el backfill de V87.3. Se cae al
    -- sucursal_id que realmente usan los movimientos de esta base, que es el que escribe la app.
    SELECT COALESCE(
        (SELECT NULLIF(sucursal_id, 0) FROM configuraciones.local ORDER BY id LIMIT 1),
        (SELECT sucursal_id FROM operaciones.movimiento_stock
          WHERE sucursal_id IS NOT NULL AND sucursal_id <> 0
          GROUP BY sucursal_id ORDER BY count(*) DESC LIMIT 1)
    ) INTO v_sucursal;

    IF v_sucursal IS NULL THEN
        RAISE WARNING 'Backfill de lotes omitido: no se pudo determinar el sucursal_id de esta base.';
        RETURN;
    END IF;

    RAISE NOTICE 'Backfill de lotes para sucursal_id = % desde el central', v_sucursal;

    -- ------------------------------------------------------------------
    -- operaciones.lote - maestro completo, sin filtrar por sucursal: la tabla no tiene esa columna
    -- porque el numero de lote lo asigna el fabricante por producto, no por boca. Mismo alcance
    -- que central_pub.
    --
    -- ON CONFLICT DO NOTHING sin target cubre la PK y uq_lote_producto_numero a la vez.
    -- El WHERE saltea lotes cuyo producto o usuario todavia no bajaron: sin el, un solo huerfano
    -- aborta la migracion entera. proveedor_id no se valida porque V83.3 le saco la FK.
    -- ------------------------------------------------------------------
    EXECUTE format($sql$
        INSERT INTO operaciones.lote (
            id, producto_id, numero_lote, fecha_vencimiento, fecha_retiro, fecha_fabricacion,
            proveedor_id, estado, observacion, usuario_id, creado_en, actualizado_en)
        SELECT r.id, r.producto_id, r.numero_lote, r.fecha_vencimiento, r.fecha_retiro,
               r.fecha_fabricacion, r.proveedor_id, r.estado, r.observacion, r.usuario_id,
               r.creado_en, r.actualizado_en
        FROM %1$I.dblink(%2$L, $remoto$
            SELECT id, producto_id, numero_lote, fecha_vencimiento, fecha_retiro, fecha_fabricacion,
                   proveedor_id, estado, observacion, usuario_id, creado_en, actualizado_en
            FROM operaciones.lote
        $remoto$) AS r(
            id                BIGINT,
            producto_id       BIGINT,
            numero_lote       VARCHAR(100),
            fecha_vencimiento DATE,
            fecha_retiro      DATE,
            fecha_fabricacion DATE,
            proveedor_id      BIGINT,
            estado            VARCHAR(20),
            observacion       VARCHAR(500),
            usuario_id        BIGINT,
            creado_en         TIMESTAMP,
            actualizado_en    TIMESTAMP)
        WHERE EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
          AND (r.usuario_id IS NULL
               OR EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
        ON CONFLICT DO NOTHING
    $sql$, v_dblink_schema, v_conninfo);
    GET DIAGNOSTICS v_lotes_ok = ROW_COUNT;

    -- ------------------------------------------------------------------
    -- operaciones.movimiento_stock_lote - solo la sucursal propia, espejando el row filter de
    -- central_%_filial%_pub.
    --
    -- Se traen SOLO las filas generadas por el central. La filial genera ids pares y el central
    -- impares (V81.3), asi que el filtro por paridad devuelve exactamente las entradas por compra
    -- y nunca las ventas que esta base ya subio: sin el, el backfill se traeria de vuelta sus
    -- propias filas y chocarian contra la PK local.
    --
    -- movimiento_stock_id se copia tal cual aunque ese movimiento no exista localmente: es el caso
    -- que habilito V87.3 al quitar fk_msl_movimiento. Queda como referencia trazable al central.
    -- ------------------------------------------------------------------
    EXECUTE format($sql$
        INSERT INTO operaciones.movimiento_stock_lote (
            id, sucursal_id, movimiento_stock_id, producto_id, presentacion_id, numero_lote,
            fecha_vencimiento, cantidad, referencia, estado, usuario_id, creado_en, lote_id)
        SELECT r.id, r.sucursal_id, r.movimiento_stock_id, r.producto_id, r.presentacion_id,
               r.numero_lote, r.fecha_vencimiento, r.cantidad, r.referencia, r.estado,
               r.usuario_id, r.creado_en, r.lote_id
        FROM %1$I.dblink(%2$L, format($remoto$
            SELECT id, sucursal_id, movimiento_stock_id, producto_id, presentacion_id, numero_lote,
                   fecha_vencimiento, cantidad, referencia, estado, usuario_id, creado_en, lote_id
            FROM operaciones.movimiento_stock_lote
            WHERE sucursal_id = %%s AND id %% 2 = 1
        $remoto$, %3$L::BIGINT)) AS r(
            id                  BIGINT,
            sucursal_id         BIGINT,
            movimiento_stock_id BIGINT,
            producto_id         BIGINT,
            presentacion_id     BIGINT,
            numero_lote         VARCHAR(100),
            fecha_vencimiento   DATE,
            cantidad            NUMERIC(15,4),
            referencia          BIGINT,
            estado              BOOLEAN,
            usuario_id          BIGINT,
            creado_en           TIMESTAMP,
            lote_id             BIGINT)
        WHERE EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
          AND (r.presentacion_id IS NULL
               OR EXISTS (SELECT 1 FROM productos.presentacion pr WHERE pr.id = r.presentacion_id))
          AND (r.usuario_id IS NULL
               OR EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
        ON CONFLICT DO NOTHING
    $sql$, v_dblink_schema, v_conninfo, v_sucursal);
    GET DIAGNOSTICS v_ledger_ok = ROW_COUNT;

    -- La secuencia local nunca se uso (hoy solo el central crea lotes), pero despues de insertar
    -- ids explicitos queda por debajo del maximo. Se reposiciona ahora, que es gratis, en vez de
    -- dejar la trampa armada para el dia que la filial pueda crear un lote.
    PERFORM setval('operaciones.lote_id_seq',
                   GREATEST((SELECT COALESCE(MAX(id), 0) FROM operaciones.lote), 1),
                   true);

    RAISE NOTICE 'Backfill: % lotes y % filas de ledger insertadas', v_lotes_ok, v_ledger_ok;

EXCEPTION WHEN OTHERS THEN
    -- No se re-lanza a proposito: Flyway envuelve la migracion en UNA transaccion y propagar el
    -- error se llevaria puesta tambien la Parte 1, que es DDL local y no puede fallar por red.
    RAISE WARNING 'Backfill de lotes omitido por error: % (SQLSTATE %). '
                  'La Parte 1 (publicar la tabla) si se aplico.', SQLERRM, SQLSTATE;
END $$;
