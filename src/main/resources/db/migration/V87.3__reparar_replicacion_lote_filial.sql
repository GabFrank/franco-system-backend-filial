-- Control de lotes - repara el canal central -> filial, que nunca llego a transportar nada.
--
-- Verificado en alpha el 2026-08-07 (central alpha:5553, filial general:5552): el central tenia
-- el lote 5555 con su desglose en el ledger, y esta base tenia las dos tablas VACIAS, con la
-- suscripcion en estado 'r', sin lag y sin apply errors. O sea que no era una falla de
-- replicacion: eran dos problemas distintos que se tapaban entre si.
--
-- ============================================================================
-- PROBLEMA 1 - fk_msl_movimiento rechaza todo lo que baja del central.
--
-- El padre operaciones.movimiento_stock esta registrado como BRANCH_TO_MAIN y NO aparece en
-- ninguna central_%_filial%_pub: la filial le manda sus movimientos al central y nunca al reves.
-- Lo que si baja es operaciones.stock_por_producto_sucursal, que es el agregado.
--
-- Pero MovimientoStockLoteService.registrarEntradaCompra() corre EN EL CENTRAL cuando se recibe
-- mercaderia para una sucursal, y crea la fila del ledger con el sucursal_id de esa sucursal
-- apuntando a un movimiento_stock que tambien vive solo en el central (id impar, generado en Java
-- por el esquema par/impar). Esa fila baja por central_%_filial%_pub filtrada por sucursal.
--
-- Resultado: TODA fila del ledger que llega desde el central es un huerfano garantizado contra
-- fk_msl_movimiento. No es una carrera ni un caso borde: el padre no existe aca y no puede
-- existir nunca. El primer INSERT que aplique el subscriber tira violacion de FK, el apply worker
-- se queda reintentando el mismo LSN para siempre y central_%_filial%_sub queda BLOQUEADA
-- ENTERA - se cae con el ledger tambien la bajada de venta, factura_legal, retiro y el stock
-- agregado.
--
-- El comentario de V81.3 asumia que la FK era viable mientras fuera de una sola columna (el
-- problema que se veia entonces era que movimiento_stock tiene claves distintas en cada base).
-- Eso era correcto como diagnostico del error de DDL, pero incompleto: el tema no es de cuantas
-- columnas es la FK, es que la fila referenciada nunca se replica.
--
-- Es el mismo razonamiento que ya aplico V82.3 para NO poner FK en lote_id, solo que ahi el
-- motivo era el orden de llegada entre publicaciones y aca es que el padre directamente no viaja.
--
-- Se puede quitar sin reemplazo: el ON DELETE CASCADE que daba la FK no lo usa nadie en esta
-- base. La filial hace SOFT delete (VentaLoteService.cambiarEstado ->
-- MovimientoStockLoteService.cambiarEstadoPorMovimiento marca estado = FALSE en las hijas); no
-- hay ninguna ruta que borre fisicamente un movimiento_stock. En el central, donde SI se borra
-- para revertir una recepcion, la FK sigue en pie (V153.3).
--
-- ============================================================================
-- PROBLEMA 2 - las filas que ya existian en el central no bajan solas.
--
-- El REFRESH PUBLICATION de los subscribers va con copy_data = false (ReplicationRefreshScheduler
-- y la mutation refreshSubscription). Engancha el stream de ahi en adelante y no copia nada de lo
-- previo. En alpha el central corrio sus migraciones de lote 10:57, la app cargo el lote 11:01, y
-- esta base recien las corrio 11:22: las filas de esos 21 minutos quedaron del lado del central
-- para siempre. Lo mismo le va a pasar a toda filial que se de de alta despues.
--
-- V82.3 anotaba como salida "re-guardar el lote en el central, que dispara un UPDATE que si se
-- replica". Eso NO funciona: cuando el subscriber recibe un UPDATE de una fila que no tiene, no
-- la crea - la ignora en silencio ("logical replication did not find row"). Solo un INSERT nuevo
-- llega. Por eso el backfill se hace aca, tirando del central por dblink.
--
-- Los datos de conexion no se hardcodean: salen del propio pg_subscription (la suscripcion contra
-- central_pub ya guarda host, puerto, base y credenciales del central) y de
-- configuraciones.local.sucursal_id. Asi la misma migracion sirve en alpha, beta y produccion.
-- ============================================================================


-- ============================================================================
-- Parte 1 - quitar la FK al movimiento agregado.
--
-- Va PRIMERO y fuera del bloque con EXCEPTION: es DDL local que no puede fallar por red, y es
-- justamente lo que impide que el backfill de la Parte 2 rebote.
--
-- Se conserva idx_msl_movimiento: las consultas que resuelven "que lotes movio este movimiento"
-- siguen igual de rapidas, solo se pierde la validacion referencial.
-- ============================================================================
ALTER TABLE operaciones.movimiento_stock_lote
    DROP CONSTRAINT IF EXISTS fk_msl_movimiento;

COMMENT ON COLUMN operaciones.movimiento_stock_lote.movimiento_stock_id IS
    'SIN foreign key a proposito (V87.3): operaciones.movimiento_stock es BRANCH_TO_MAIN, asi que '
    'las filas creadas en el central para esta sucursal referencian un movimiento que nunca se '
    'replica hacia aca. Con FK, el apply worker se bloquea. En el central la FK si existe.';


-- ============================================================================
-- Parte 2 - backfill de lo que quedo del lado del central.
--
-- Todo el bloque falla suave: si no hay suscripcion configurada, si falta dblink o si el central
-- esta caido, avisa y la migracion termina OK. Acoplar el arranque de la filial a que el central
-- responda seria peor que el hueco de datos que arregla, y el backfill se puede reintentar
-- corriendo el mismo bloque a mano.
--
-- Orden: primero el maestro operaciones.lote y despues el ledger, para que lote_id resuelva
-- apenas termina la migracion en vez de quedar la vista degradada hasta el proximo refresh.
-- ============================================================================
DO $$
DECLARE
    v_conninfo       TEXT;
    v_sucursal       BIGINT;
    v_dblink_schema  TEXT;
    v_lotes_ok       BIGINT := 0;
    v_lotes_skip     BIGINT := 0;
    v_ledger_ok      BIGINT := 0;
    v_ledger_skip    BIGINT := 0;
BEGIN
    -- dblink puede no estar en filiales recien instaladas. Si el rol de migracion no es superuser
    -- esto tira excepcion y cae en el handler de abajo, que es el comportamiento buscado.
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'dblink') THEN
        CREATE EXTENSION dblink;
        RAISE NOTICE 'Extension dblink creada';
    END IF;

    -- Donde vive dblink() cambia por base: en general (alpha filial 2) quedo instalada en el
    -- esquema 'general', no en 'public'. Como el search_path de la conexion de migracion es
    -- '"$user", public', llamarla sin calificar tira "function dblink(unknown, unknown) does not
    -- exist". Se resuelve el esquema real y se califica en cada llamada, en vez de tocar el
    -- search_path de la transaccion.
    SELECT n.nspname INTO v_dblink_schema
    FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'dblink';

    -- La suscripcion contra central_pub es, por definicion, la que apunta al central: central_pub
    -- es la publicacion MAIN_TO_ALL y solo el central la tiene. Su conninfo ya trae host, puerto,
    -- base y credenciales, asi que no hace falta ningun parametro nuevo por entorno.
    SELECT subconninfo INTO v_conninfo
    FROM pg_subscription
    WHERE 'central_pub' = ANY(subpublications)
    ORDER BY subname
    LIMIT 1;

    IF v_conninfo IS NULL THEN
        RAISE WARNING 'Backfill de lotes omitido: esta base no tiene suscripcion contra central_pub '
                      '(replicacion todavia no configurada). Volver a correr el bloque cuando lo este.';
        RETURN;
    END IF;

    SELECT sucursal_id INTO v_sucursal
    FROM configuraciones.local
    ORDER BY id
    LIMIT 1;

    IF v_sucursal IS NULL THEN
        RAISE WARNING 'Backfill de lotes omitido: configuraciones.local no tiene sucursal_id.';
        RETURN;
    END IF;

    RAISE NOTICE 'Backfill de lotes para sucursal_id = % desde el central', v_sucursal;

    -- ------------------------------------------------------------------
    -- operaciones.lote - maestro completo, sin filtrar.
    --
    -- Es el mismo alcance que central_pub: la tabla no tiene columna sucursal_id porque el numero
    -- de lote lo asigna el fabricante por producto, no por sucursal.
    --
    -- ON CONFLICT DO NOTHING sin target cubre las dos restricciones a la vez: la PK (id) y
    -- uq_lote_producto_numero (producto_id, numero_lote). Con target habria que elegir una.
    --
    -- Los ids vienen explicitos del central, que es el unico que crea lotes (MAIN_TO_ALL), asi que
    -- no hay riesgo de pisar nada generado localmente.
    --
    -- El WHERE saltea filas cuyo producto o usuario todavia no bajaron: esas FKs se conservan y sin
    -- el filtro un solo huerfano abortaria la migracion entera y dejaria la filial sin arrancar.
    -- proveedor_id no se valida porque V83.3 le saco la FK justamente por este motivo.
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

    EXECUTE format($sql$
        SELECT count(*)
        FROM %1$I.dblink(%2$L, $remoto$
            SELECT id, producto_id, usuario_id FROM operaciones.lote
        $remoto$) AS r(id BIGINT, producto_id BIGINT, usuario_id BIGINT)
        WHERE NOT EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
           OR (r.usuario_id IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
    $sql$, v_dblink_schema, v_conninfo) INTO v_lotes_skip;

    -- ------------------------------------------------------------------
    -- operaciones.movimiento_stock_lote - solo la sucursal propia.
    --
    -- Espeja el row filter de central_%_filial%_pub (WHERE sucursal_id = N) que puso V159.3 en el
    -- central. Traer las otras sucursales inflaria v_stock_lote con stock que no es de esta boca.
    --
    -- Sin colision de ids: el central genera impares y la secuencia local va INCREMENT BY 2
    -- arrancando en par (V81.3), asi que los rangos no se tocan.
    --
    -- movimiento_stock_id se copia tal cual aunque ese movimiento no exista localmente: es
    -- exactamente el caso que habilita la Parte 1. Queda como referencia trazable al central.
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
            WHERE sucursal_id = %%s
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

    EXECUTE format($sql$
        SELECT count(*)
        FROM %1$I.dblink(%2$L, format($remoto$
            SELECT id, producto_id, presentacion_id, usuario_id
            FROM operaciones.movimiento_stock_lote
            WHERE sucursal_id = %%s
        $remoto$, %3$L::BIGINT)) AS r(
            id BIGINT, producto_id BIGINT, presentacion_id BIGINT, usuario_id BIGINT)
        WHERE NOT EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
           OR (r.presentacion_id IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM productos.presentacion pr WHERE pr.id = r.presentacion_id))
           OR (r.usuario_id IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
    $sql$, v_dblink_schema, v_conninfo, v_sucursal) INTO v_ledger_skip;

    -- La secuencia local nunca se uso: hoy solo el central crea lotes (V82.3). Pero despues de
    -- meter ids explicitos queda apuntando por debajo del maximo, y el dia que la Etapa 2 le
    -- permita a la filial crear un lote el primer nextval() chocaria contra la PK. Se reposiciona
    -- ahora, que es gratis, en vez de dejar la trampa armada.
    PERFORM setval('operaciones.lote_id_seq',
                   GREATEST((SELECT COALESCE(MAX(id), 0) FROM operaciones.lote), 1),
                   true);

    RAISE NOTICE 'Backfill lote: % filas insertadas, % omitidas por FK sin resolver', v_lotes_ok, v_lotes_skip;
    RAISE NOTICE 'Backfill movimiento_stock_lote: % filas insertadas, % omitidas por FK sin resolver',
                 v_ledger_ok, v_ledger_skip;

    -- Las omitidas no se pierden: cuando baje el producto/presentacion/usuario que falta, el
    -- bloque se puede volver a correr tal cual (es idempotente por el ON CONFLICT DO NOTHING).
    IF v_lotes_skip > 0 OR v_ledger_skip > 0 THEN
        RAISE WARNING 'Quedaron filas de lote sin backfillear por FKs sin resolver en esta base. '
                      'Reintentar el bloque despues de que la replicacion baje producto/presentacion/usuario.';
    END IF;

EXCEPTION WHEN OTHERS THEN
    -- A proposito no se re-lanza. Flyway envuelve toda la migracion en UNA transaccion, asi que
    -- si esto propagara el error se perderia tambien la Parte 1. Al atraparlo, solo revierte la
    -- subtransaccion del bloque: el ALTER TABLE de arriba sigue vivo y commitea con la migracion.
    -- Que el central este caido no tiene que impedir que la filial levante ni dejar la suscripcion
    -- bloqueada.
    RAISE WARNING 'Backfill de lotes omitido por error: % (SQLSTATE %). '
                  'La Parte 1 (quitar fk_msl_movimiento) si se aplico.', SQLERRM, SQLSTATE;
END $$;
