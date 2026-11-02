-- Lotes flexibles, fase 3, entrega 4 - bajar a la filial los lotes viejos SIN eco hacia el central.
--
-- Esta migracion NO mueve datos: solo crea la tabla de registro y dos funciones que se corren a
-- mano, como postgres, de noche, una sucursal por vez y despues de un pg_dump verificado.
--
--   SELECT * FROM operaciones.backfill_lotes_central(false);      -- diagnostico (no escribe)
--   SELECT * FROM operaciones.backfill_lotes_central(true);       -- aplica
--   SELECT * FROM operaciones.backfill_lotes_deshacer(<corrida>); -- vuelta atras por id
--
-- Por que no alcanza con V93.3: aquel backfill insertaba en una sesion normal, y la publicacion
-- de subida de la filial publica movimiento_stock_lote. Lo insertado volvia al central con la
-- misma PK. Aca la sesion se marca con un origen de replicacion ('frc_backfill_lotes'); las
-- suscripciones del central contra las filiales tienen origin = none (verificado en farmacia el
-- 24/10), asi que esos cambios no se publican: no hay eco ni choque de clave.
--
-- Reglas:
--   - Solo inserta. Lote maestro completo y movimiento_stock_lote con id IMPAR (los genera el
--     central) y sucursal_id propio. Las filas pares (las de la filial) nunca se tocan.
--   - ON CONFLICT DO NOTHING: re-ejecutar no duplica.
--   - movimiento_stock (existencia del producto) no se toca: el saldo por lote es la suma del
--     ledger y la existencia sigue igual.
--   - Cada id insertado queda en operaciones.backfill_lote_log para poder borrarlo.
--   - pg_replication_origin_* y subconninfo requieren superusuario: se corre como postgres.
--     Si no hay permiso, la funcion falla ANTES de insertar nada.
--   - backfill_lote_log no se publica: queda local a esta base.

CREATE TABLE IF NOT EXISTS operaciones.backfill_lote_log (
    id          BIGSERIAL PRIMARY KEY,
    corrida     BIGINT      NOT NULL,
    tabla       VARCHAR(40) NOT NULL,
    fila_id     BIGINT      NOT NULL,
    creado_en   TIMESTAMP   NOT NULL DEFAULT now(),
    deshecho_en TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_backfill_lote_log_corrida ON operaciones.backfill_lote_log (corrida);


CREATE OR REPLACE FUNCTION operaciones.backfill_lotes_central(p_aplicar BOOLEAN DEFAULT false, p_conninfo TEXT DEFAULT NULL)
RETURNS TABLE (concepto TEXT, valor TEXT)
LANGUAGE plpgsql AS $fn$
DECLARE
    v_conninfo      TEXT;
    v_sucursal      BIGINT;
    v_dblink_schema TEXT;
    v_corrida       BIGINT;
    v_n             BIGINT;
BEGIN
    SELECT n.nspname INTO v_dblink_schema
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'dblink';
    IF v_dblink_schema IS NULL THEN
        RAISE EXCEPTION 'Falta la extension dblink en esta base';
    END IF;

    -- Mismo descubrimiento que V93.3: la suscripcion contra central_pub apunta al central.
    -- p_conninfo solo para pruebas o si la suscripcion tiene otro nombre de publicacion.
    v_conninfo := p_conninfo;
    IF v_conninfo IS NULL THEN
        SELECT subconninfo INTO v_conninfo
        FROM pg_subscription
        WHERE 'central_pub' = ANY(subpublications)
          AND subdbid = (SELECT oid FROM pg_database WHERE datname = current_database())
        ORDER BY subname LIMIT 1;
    END IF;
    IF v_conninfo IS NULL THEN
        RAISE EXCEPTION 'Esta base no tiene suscripcion contra central_pub';
    END IF;

    SELECT COALESCE(
        (SELECT NULLIF(sucursal_id, 0) FROM configuraciones.local ORDER BY id LIMIT 1),
        (SELECT sucursal_id FROM operaciones.movimiento_stock
          WHERE sucursal_id IS NOT NULL AND sucursal_id <> 0
          GROUP BY sucursal_id ORDER BY count(*) DESC LIMIT 1)
    ) INTO v_sucursal;
    IF v_sucursal IS NULL THEN
        RAISE EXCEPTION 'No se pudo determinar el sucursal_id de esta base';
    END IF;

    -- Copia de lo que tiene el central, en tablas temporales (no se replican).
    DROP TABLE IF EXISTS bf_lote;
    DROP TABLE IF EXISTS bf_msl;
    EXECUTE format($sql$
        CREATE TEMP TABLE bf_lote AS
        SELECT * FROM %1$I.dblink(%2$L, $r$
            SELECT id, producto_id, numero_lote, fecha_vencimiento, fecha_retiro, fecha_fabricacion,
                   proveedor_id, estado, observacion, usuario_id, creado_en, actualizado_en
            FROM operaciones.lote $r$) AS r(
            id BIGINT, producto_id BIGINT, numero_lote VARCHAR(100), fecha_vencimiento DATE,
            fecha_retiro DATE, fecha_fabricacion DATE, proveedor_id BIGINT, estado VARCHAR(20),
            observacion VARCHAR(500), usuario_id BIGINT, creado_en TIMESTAMP, actualizado_en TIMESTAMP)
    $sql$, v_dblink_schema, v_conninfo);
    EXECUTE format($sql$
        CREATE TEMP TABLE bf_msl AS
        SELECT * FROM %1$I.dblink(%2$L, format($r$
            SELECT id, sucursal_id, movimiento_stock_id, producto_id, presentacion_id, numero_lote,
                   fecha_vencimiento, cantidad, referencia, estado, usuario_id, creado_en, lote_id
            FROM operaciones.movimiento_stock_lote
            WHERE sucursal_id = %%s AND id %%%% 2 = 1 $r$, %3$L::BIGINT)) AS r(
            id BIGINT, sucursal_id BIGINT, movimiento_stock_id BIGINT, producto_id BIGINT,
            presentacion_id BIGINT, numero_lote VARCHAR(100), fecha_vencimiento DATE,
            cantidad NUMERIC(15,4), referencia BIGINT, estado BOOLEAN, usuario_id BIGINT,
            creado_en TIMESTAMP, lote_id BIGINT)
    $sql$, v_dblink_schema, v_conninfo, v_sucursal);

    concepto := 'sucursal_id'; valor := v_sucursal::TEXT; RETURN NEXT;
    concepto := 'lotes_en_central'; valor := (SELECT count(*) FROM bf_lote)::TEXT; RETURN NEXT;
    concepto := 'lotes_faltantes';
    valor := (SELECT count(*) FROM bf_lote r WHERE NOT EXISTS
              (SELECT 1 FROM operaciones.lote l WHERE l.id = r.id))::TEXT; RETURN NEXT;
    concepto := 'lotes_con_numero_repetido_otro_id';
    valor := (SELECT count(*) FROM bf_lote r JOIN operaciones.lote l
              ON l.producto_id = r.producto_id AND l.numero_lote = r.numero_lote AND l.id <> r.id)::TEXT;
    RETURN NEXT;
    concepto := 'ledger_impar_faltante';
    valor := (SELECT count(*) FROM bf_msl r WHERE NOT EXISTS
              (SELECT 1 FROM operaciones.movimiento_stock_lote m WHERE m.id = r.id))::TEXT; RETURN NEXT;
    concepto := 'ledger_impar_distinto';
    valor := (SELECT count(*) FROM bf_msl r JOIN operaciones.movimiento_stock_lote m ON m.id = r.id
              WHERE (m.cantidad, m.estado, m.lote_id) IS DISTINCT FROM (r.cantidad, r.estado, r.lote_id))::TEXT;
    RETURN NEXT;

    IF NOT p_aplicar THEN
        concepto := 'modo'; valor := 'diagnostico (no se escribio nada)'; RETURN NEXT;
        RETURN;
    END IF;

    -- Marcar la sesion: lo que se inserte desde aca no se publica hacia el central.
    IF NOT EXISTS (SELECT 1 FROM pg_replication_origin WHERE roname = 'frc_backfill_lotes') THEN
        PERFORM pg_replication_origin_create('frc_backfill_lotes');
    END IF;
    IF pg_replication_origin_session_is_setup() THEN
        PERFORM pg_replication_origin_session_reset();
    END IF;
    PERFORM pg_replication_origin_session_setup('frc_backfill_lotes');

    v_corrida := (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT;

    WITH ins AS (
        INSERT INTO operaciones.lote (
            id, producto_id, numero_lote, fecha_vencimiento, fecha_retiro, fecha_fabricacion,
            proveedor_id, estado, observacion, usuario_id, creado_en, actualizado_en)
        SELECT r.id, r.producto_id, r.numero_lote, r.fecha_vencimiento, r.fecha_retiro,
               r.fecha_fabricacion, r.proveedor_id, r.estado, r.observacion, r.usuario_id,
               r.creado_en, r.actualizado_en
        FROM bf_lote r
        WHERE EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
          AND (r.usuario_id IS NULL OR EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
        ON CONFLICT DO NOTHING
        RETURNING id)
    INSERT INTO operaciones.backfill_lote_log (corrida, tabla, fila_id)
    SELECT v_corrida, 'lote', id FROM ins;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    concepto := 'lotes_insertados'; valor := v_n::TEXT; RETURN NEXT;

    WITH ins AS (
        INSERT INTO operaciones.movimiento_stock_lote (
            id, sucursal_id, movimiento_stock_id, producto_id, presentacion_id, numero_lote,
            fecha_vencimiento, cantidad, referencia, estado, usuario_id, creado_en, lote_id)
        SELECT r.id, r.sucursal_id, r.movimiento_stock_id, r.producto_id, r.presentacion_id,
               r.numero_lote, r.fecha_vencimiento, r.cantidad, r.referencia, r.estado,
               r.usuario_id, r.creado_en, r.lote_id
        FROM bf_msl r
        WHERE r.id % 2 = 1
          AND EXISTS (SELECT 1 FROM productos.producto p WHERE p.id = r.producto_id)
          AND (r.presentacion_id IS NULL OR EXISTS (SELECT 1 FROM productos.presentacion pr WHERE pr.id = r.presentacion_id))
          AND (r.usuario_id IS NULL OR EXISTS (SELECT 1 FROM personas.usuario u WHERE u.id = r.usuario_id))
          AND (r.lote_id IS NULL OR EXISTS (SELECT 1 FROM operaciones.lote l WHERE l.id = r.lote_id))
        ON CONFLICT DO NOTHING
        RETURNING id)
    INSERT INTO operaciones.backfill_lote_log (corrida, tabla, fila_id)
    SELECT v_corrida, 'movimiento_stock_lote', id FROM ins;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    concepto := 'ledger_insertado'; valor := v_n::TEXT; RETURN NEXT;

    PERFORM setval('operaciones.lote_id_seq',
                   GREATEST((SELECT COALESCE(MAX(id), 0) FROM operaciones.lote), 1), true);
    PERFORM pg_replication_origin_session_reset();

    concepto := 'corrida'; valor := v_corrida::TEXT; RETURN NEXT;
EXCEPTION WHEN OTHERS THEN
    IF pg_replication_origin_session_is_setup() THEN
        PERFORM pg_replication_origin_session_reset();
    END IF;
    RAISE;
END $fn$;


-- Vuelta atras: borra por id lo que inserto una corrida, bajo el mismo origen (el borrado tampoco
-- sube). No deshace si algun lote tocado por la corrida tiene movimientos posteriores a ella.
CREATE OR REPLACE FUNCTION operaciones.backfill_lotes_deshacer(p_corrida BIGINT)
RETURNS TABLE (concepto TEXT, valor TEXT)
LANGUAGE plpgsql AS $fn$
DECLARE
    v_n BIGINT;
BEGIN
    -- Lotes tocados por la corrida: los que inserto y los de las filas de ledger que inserto.
    IF EXISTS (
        WITH tocados AS (
            SELECT g.fila_id AS lote_id FROM operaciones.backfill_lote_log g
            WHERE g.corrida = p_corrida AND g.tabla = 'lote' AND g.deshecho_en IS NULL
            UNION
            SELECT m.lote_id FROM operaciones.backfill_lote_log g
            JOIN operaciones.movimiento_stock_lote m ON m.id = g.fila_id
            WHERE g.corrida = p_corrida AND g.tabla = 'movimiento_stock_lote' AND g.deshecho_en IS NULL),
        desde AS (
            SELECT min(creado_en) AS t FROM operaciones.backfill_lote_log WHERE corrida = p_corrida)
        SELECT 1 FROM operaciones.movimiento_stock_lote m, desde
        WHERE m.lote_id IN (SELECT lote_id FROM tocados)
          AND m.creado_en >= desde.t
          AND NOT EXISTS (SELECT 1 FROM operaciones.backfill_lote_log g2
                          WHERE g2.corrida = p_corrida AND g2.tabla = 'movimiento_stock_lote'
                            AND g2.fila_id = m.id)
    ) THEN
        RAISE EXCEPTION 'Hay movimientos posteriores sobre lotes de la corrida %: no se deshace', p_corrida;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_replication_origin WHERE roname = 'frc_backfill_lotes') THEN
        PERFORM pg_replication_origin_create('frc_backfill_lotes');
    END IF;
    IF pg_replication_origin_session_is_setup() THEN
        PERFORM pg_replication_origin_session_reset();
    END IF;
    PERFORM pg_replication_origin_session_setup('frc_backfill_lotes');

    DELETE FROM operaciones.movimiento_stock_lote m USING operaciones.backfill_lote_log g
    WHERE g.corrida = p_corrida AND g.tabla = 'movimiento_stock_lote' AND g.deshecho_en IS NULL
      AND m.id = g.fila_id;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    concepto := 'ledger_borrado'; valor := v_n::TEXT; RETURN NEXT;

    DELETE FROM operaciones.lote l USING operaciones.backfill_lote_log g
    WHERE g.corrida = p_corrida AND g.tabla = 'lote' AND g.deshecho_en IS NULL AND l.id = g.fila_id;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    concepto := 'lotes_borrados'; valor := v_n::TEXT; RETURN NEXT;

    UPDATE operaciones.backfill_lote_log SET deshecho_en = now()
    WHERE corrida = p_corrida AND deshecho_en IS NULL;
    PERFORM pg_replication_origin_session_reset();
EXCEPTION WHEN OTHERS THEN
    IF pg_replication_origin_session_is_setup() THEN
        PERFORM pg_replication_origin_session_reset();
    END IF;
    RAISE;
END $fn$;
