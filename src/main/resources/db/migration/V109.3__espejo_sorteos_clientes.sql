-- =====================================================================
-- sorteos para clientes: espejo de sorteo / sorteo_sucursal / sorteo_premio
-- y tabla propia sorteo_participacion
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El central define los sorteos (vigencia, monto minimo, premios, sucursales). Cada filial los lee
-- al cobrar una venta (SorteoLector), decide cuantos tickets corresponden y registra la
-- participacion del cliente en sorteo_participacion, que despues sube al central
-- (BRANCH_TO_MAIN). Ver central:docs/superpowers/specs/2026-10-28-sorteos-clientes-design.md
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL (V260.3)
--
-- sorteo, sorteo_sucursal y sorteo_premio son MAIN_TO_ALL. Una vez que el central las agrega a
-- central_pub, una filial SIN estas tablas corta TODA su replicacion entrante (el apply worker
-- cae en bucle con "logical replication target relation does not exist"). Por eso se verifica en
-- CADA filial antes de publicarlas. Para una filial inalcanzable, este DDL es idempotente y se
-- puede correr a mano.
--
-- CHECKLIST DE VERIFICACION ANTES DE CARGAR EL PRIMER SORTEO REAL
--
-- (a) MAIN_TO_ALL (sorteo, sorteo_sucursal, sorteo_premio): en CADA filial verificar
--     srsubstate='r' para las tres tablas en pg_subscription_rel (suscripcion a central_pub).
--     No crear sorteos antes de eso.
-- (b) BRANCH_TO_MAIN (operaciones.sorteo_participacion): la tabla entra a la publicacion
--     *_filialX_pub de cada filial solo via ReplicationPublicationSyncScheduler (replication.sync
--     .enabled, a los 2 min del arranque y luego cada hora, y solo si la filial es alcanzable).
--     En CADA filial verificar:
--       select * from pg_publication_tables where tablename='sorteo_participacion';
--     y en el central verificar que la filialX_sub correspondiente la tenga en srsubstate='r':
--       select * from pg_subscription_rel where srrelid='operaciones.sorteo_participacion'::regclass;
--     (si no esta, hacer REFRESH PUBLICATION en esa suscripcion).
--     Consecuencia de copy_data=false: toda participacion insertada en una filial ANTES de que
--     la tabla este en la publicacion y la suscripcion del central refrescada NUNCA llega al
--     central (queda solo en la filial). El listado y "Validar codigo" no encontrarian ese ticket.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Las tres tablas espejo tienen las mismas columnas y tipos que el central, todas nullable. PK en
-- id (la usa el apply para ubicar la fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin
-- seed: las restricciones viven en el central. El filial nunca escribe las tablas espejo.
--
-- sorteo_participacion es propia de la filial (BRANCH_TO_MAIN): la escribe la filial, una fila por
-- venta y por sorteo. Sin columna de estado: el central deriva ACTIVA/ANULADA de la venta.
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS operaciones;

CREATE TABLE IF NOT EXISTS operaciones.sorteo (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR,
    descripcion         VARCHAR,
    monto_minimo        NUMERIC,
    modo_participacion  VARCHAR,
    cantidad_fija       INTEGER,
    tope_por_venta      INTEGER,
    incluye_tarjeta     BOOLEAN,
    fecha_desde         DATE,
    fecha_hasta         DATE,
    fecha_sorteo        DATE,
    todas_sucursales    BOOLEAN,
    activo              BOOLEAN,
    usuario_id          BIGINT,
    creado_en           TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_sucursal (
    id           BIGSERIAL PRIMARY KEY,
    sorteo_id    BIGINT,
    sucursal_id  BIGINT
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_premio (
    id           BIGSERIAL PRIMARY KEY,
    sorteo_id    BIGINT,
    orden        INTEGER,
    descripcion  VARCHAR,
    producto_id  BIGINT,
    cantidad     INTEGER
);

CREATE TABLE IF NOT EXISTS operaciones.sorteo_participacion (
    id           BIGSERIAL PRIMARY KEY,
    sorteo_id    BIGINT NOT NULL,
    venta_id     BIGINT NOT NULL,
    sucursal_id  BIGINT NOT NULL,
    cantidad     INTEGER NOT NULL,
    cliente_id   BIGINT,
    documento    VARCHAR,
    nombre       VARCHAR,
    codigo       VARCHAR NOT NULL,
    usuario_id   BIGINT,
    creado_en    TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_sorteo_participacion_sorteo_venta UNIQUE (sorteo_id, venta_id)
);

CREATE INDEX IF NOT EXISTS idx_sorteo_participacion_venta
    ON operaciones.sorteo_participacion (venta_id);

COMMENT ON TABLE operaciones.sorteo IS
    'Sorteo para clientes. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee.';
COMMENT ON TABLE operaciones.sorteo_sucursal IS
    'Sucursales donde rige un sorteo que no es para todas. Llega por replicacion desde el central (MAIN_TO_ALL).';
COMMENT ON TABLE operaciones.sorteo_premio IS
    'Premios de un sorteo. Llega por replicacion desde el central (MAIN_TO_ALL).';
COMMENT ON TABLE operaciones.sorteo_participacion IS
    'Participacion de un cliente en un sorteo, una fila por venta y sorteo. La escribe el filial y sube al central (BRANCH_TO_MAIN).';
