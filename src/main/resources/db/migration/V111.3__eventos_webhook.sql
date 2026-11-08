-- =====================================================================
-- Eventos por webhook, fase 1a (filial #246, central #587)
-- =====================================================================
-- Cada nodo guarda sus eventos en una cola propia (outbox) dentro de la misma transaccion que la
-- operacion, y los manda el solo a los receptores suscriptos, sin pasar por el central.
--
-- LOCALES (nunca van a replication_table): eventos.outbox_evento, eventos.webhook_entrega,
-- eventos.destino_estado.
--
-- ESPEJO MAIN_TO_ALL: configuraciones.webhook_suscripcion. La edita solo el central (V267.3) y baja
-- por central_pub. Mismas columnas que el central, sin FK ni CHECK, como V109.3. La filial nunca la
-- escribe.
--
-- BRANCH_TO_MAIN: eventos.nodo_estado (una fila por nodo, PK = sucursal_id). La escribe el latido
-- y sube al central para la pantalla de estado.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DEL CENTRAL (V267.3), EN LAS 21 FILIALES.
-- Si el central publica webhook_suscripcion con una filial sin esta tabla, se corta toda la
-- replicacion entrante de esa filial. El DDL es idempotente: se puede correr a mano.
--
-- El valor de los secretos y la clave privada de firma NO estan en la base: viven en el overlay
-- application.properties de cada nodo (frc.webhooks.*).
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS eventos;
CREATE SCHEMA IF NOT EXISTS configuraciones;

-- Espejo de las suscripciones (lado subscriber de MAIN_TO_ALL)
CREATE TABLE IF NOT EXISTS configuraciones.webhook_suscripcion (
    id                BIGSERIAL PRIMARY KEY,
    consumidor_id     BIGINT,
    nombre            VARCHAR,
    url               VARCHAR,
    eventos           TEXT[],
    eventos_pausados  TEXT[],
    sucursal_ids      BIGINT[],
    umbrales          JSONB,
    contenido         VARCHAR,
    entrega           VARCHAR,
    version_evento     INTEGER,
    datos_personales  BOOLEAN,
    cabecera_nombre   VARCHAR,
    secreto_ref      VARCHAR,
    latido            BOOLEAN,
    verificada        BOOLEAN,
    activo            BOOLEAN,
    config_version    BIGINT,
    creado_por        BIGINT,
    creado_en         TIMESTAMPTZ,
    actualizado_en    TIMESTAMPTZ
);

-- Cola de hechos: solo el hecho con sus valores crudos, sin lecturas extra
CREATE TABLE IF NOT EXISTS eventos.outbox_evento (
    id              BIGSERIAL PRIMARY KEY,
    evento_id       VARCHAR     NOT NULL UNIQUE,
    tipo            VARCHAR     NOT NULL,
    version_evento   INTEGER     NOT NULL DEFAULT 1,
    gravedad        VARCHAR,
    ocurrido_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sucursal_id     BIGINT,
    usuario_id      BIGINT,
    sujeto          VARCHAR,
    datos           JSONB,
    link            VARCHAR,
    repartido_en   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_evento_sin_despachar
    ON eventos.outbox_evento (id) WHERE repartido_en IS NULL;

-- Una entrega por evento y suscripcion. secuencia: correlativa y sin saltos por suscripcion y nodo.
CREATE TABLE IF NOT EXISTS eventos.webhook_entrega (
    id                  BIGSERIAL PRIMARY KEY,
    outbox_id           BIGINT      NOT NULL REFERENCES eventos.outbox_evento (id) ON DELETE CASCADE,
    suscripcion_id      BIGINT      NOT NULL,
    secuencia           BIGINT      NOT NULL,
    config_version      BIGINT,
    umbral_aplicado     JSONB,
    estado              VARCHAR     NOT NULL DEFAULT 'PENDIENTE',
    intentos            INTEGER     NOT NULL DEFAULT 0,
    primer_intento_en   TIMESTAMPTZ,
    proximo_intento_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_hasta         TIMESTAMPTZ,
    ultimo_status_http  INTEGER,
    ultimo_error        VARCHAR(300),
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en          TIMESTAMPTZ,
    UNIQUE (suscripcion_id, secuencia),
    UNIQUE (outbox_id, suscripcion_id)
) WITH (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.02);

CREATE INDEX IF NOT EXISTS idx_webhook_entrega_vivas
    ON eventos.webhook_entrega (proximo_intento_en, suscripcion_id)
    WHERE estado IN ('PENDIENTE', 'REINTENTO', 'EN_CURSO');

-- Estado por destino: secuencia, circuit breaker y pausa por 410
CREATE TABLE IF NOT EXISTS eventos.destino_estado (
    suscripcion_id     BIGINT PRIMARY KEY,
    ultima_secuencia   BIGINT      NOT NULL DEFAULT 0,
    fallos_seguidos    INTEGER     NOT NULL DEFAULT 0,
    pausado_hasta      TIMESTAMPTZ,
    pausado_por_410    BOOLEAN     NOT NULL DEFAULT false,
    ultimo_error       VARCHAR(300),
    actualizado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Estado del nodo (BRANCH_TO_MAIN). PK = sucursal_id para que no choquen las filas de las filiales.
CREATE TABLE IF NOT EXISTS eventos.nodo_estado (
    sucursal_id            BIGINT PRIMARY KEY,
    emisor_encendido       BOOLEAN,
    firma_configurada      BOOLEAN,
    config_version         BIGINT,
    pendientes             INTEGER,
    pendiente_mas_viejo    TIMESTAMPTZ,
    descartadas_24h        INTEGER,
    fallos_emisor          BIGINT,
    hora_nodo              TIMESTAMPTZ,
    actualizado_en         TIMESTAMPTZ
);

ALTER TABLE eventos.nodo_estado REPLICA IDENTITY DEFAULT;

COMMENT ON TABLE eventos.outbox_evento IS 'Cola local de eventos del nodo. No se replica.';
COMMENT ON TABLE eventos.webhook_entrega IS 'Entregas por suscripcion, con lease y reintentos. No se replica.';
COMMENT ON TABLE configuraciones.webhook_suscripcion IS 'Espejo MAIN_TO_ALL de las suscripciones. La filial no la escribe.';
COMMENT ON TABLE eventos.nodo_estado IS 'Estado del nodo para la pantalla del central. BRANCH_TO_MAIN.';
