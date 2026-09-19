-- =====================================================================
-- formato_terminal_pos: el formato es del MODELO DE APARATO, no del proveedor
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- financiero.formato_qr_pos resuelve el formato POR PROVEEDOR, con un unico indice
-- (uq_formato_qr_pos_proveedor). Eso no distingue una maquinita Bancard de un portal web
-- Bancard, y tampoco dos firmwares distintos de la misma marca. Y son cosas que se leen de
-- formas distintas: la maquinita imprime un ticket que hay que fotografiar, el portal web
-- imprime un QR que el lector del PDV escanea.
--
-- El modelo nuevo: un proveedor tiene tantos formatos como modelos de aparato tenga, y cada
-- terminal elige el suyo. Bancard v5.2 y Bancard v5.5 conviven sin duplicar nada.
--
-- POR QUE ES UNA TABLA NUEVA Y NO UN RENAME
--
-- financiero.formato_qr_pos esta replicada MAIN_TO_ALL. Un ALTER TABLE ... RENAME no la saca de
-- la publicacion --Postgres trackea la membresia por OID, no por nombre-- pero SI rompe el otro
-- lado: el protocolo de replicacion logica identifica la relacion EN EL SUBSCRIBER por
-- schema.nombre. Si central renombra y las 24 filiales todavia no, cada apply worker busca
-- financiero.formato_qr_pos, no la encuentra y se detiene. Y esa ventana no se puede cerrar:
-- cada filial actualiza por cron cada 15 minutos, por su cuenta.
--
-- Por eso: tabla nueva, central copia la fila, y el DROP de la vieja va en una entrega
-- posterior, cuando toda la flota corra el codigo nuevo.
--
-- ⚠️ ORDEN DE DESPLIEGUE: esta migracion va ANTES que la de central, en TODA la flota.
-- financiero.formato_terminal_pos se replica MAIN_TO_ALL y formato_terminal_pos_id es una
-- columna nueva en financiero.terminal_pos, que tambien es MAIN_TO_ALL. Si el filial no las
-- tiene, el apply worker central→filial entra en crash-loop con el slot reteniendo WAL
-- (corte del 2026-08-20). Mismo patron que V91.5 respecto de la V217.5 de central.
--
-- Todo aditivo e idempotente.
-- =====================================================================

-- ── 1) financiero.formato_terminal_pos ──────────────────────────────────────────────────────
--
-- El filial solo LEE esta tabla (la carga y la edita el central); llega por MAIN_TO_ALL.
--
-- Sin FK a personas.proveedor_servicio: si la fila del proveedor todavia no bajo por
-- replicacion, una FK haria fallar el apply de la fila del formato. El filial es subscriber:
-- valida el central, no el filial. Mismo criterio que V91.5.
--
-- ⚠️ SOLO LA PK LLEVA NOT NULL. En un subscriber, cualquier restriccion que el publisher no
-- comparta convierte un valor legitimo en un corte de replicacion: el dia que central inserte una
-- fila con esa columna en NULL --por una variante del dominio que hoy no existe, como el tipo API
-- que ya obligo a que `patron` sea nullable-- el apply worker de esta filial se detiene. La
-- obligatoriedad de nombre/mapeo/tipo es una regla de NEGOCIO y vive donde se escribe: el ABM del
-- central. Aca no compra nada, porque el filial nunca escribe esta tabla.
--
-- La version anterior de esta migracion los declaraba NOT NULL copiando V91.5. Ahi era correcto
-- por casualidad --coincidia exactamente con la V217.5 de central--, no por metodo: aca no hay
-- todavia una migracion de central contra la cual comparar.
CREATE TABLE IF NOT EXISTS financiero.formato_terminal_pos (
    id                     BIGINT       NOT NULL,
    nombre                 VARCHAR(100) NULL,
    proveedor_servicio_id  BIGINT       NULL,
    -- MAQUINA | WEB | API. Es el router del flujo: decide si el cupon se lee por QR, por foto,
    -- o si los campos llegan estructurados del proveedor.
    --
    -- SIN CHECK y sin NOT NULL, a proposito: ver la nota de arriba de la tabla. Es el mismo
    -- criterio por el que abajo hay un indice y no un UNIQUE.
    --
    -- Y VARCHAR y no un enum de PostgreSQL: financiero.venta_tarjeta.estado ya es VARCHAR(20)
    -- mapeado a String en Java, que es la convencion de este modulo. Un enum de PG habria
    -- exigido un ALTER TYPE coordinado en las 24 filiales cada vez que aparezca un tipo nuevo.
    tipo                   VARCHAR(20)  NULL DEFAULT 'MAQUINA',
    -- Regex con grupos nombrados. NULL solo para tipo API, donde los campos no se parsean de un
    -- texto. Para MAQUINA y WEB es obligatorio: el OCR devuelve texto igual que el QR y se
    -- matchea con el mismo patron.
    patron                 TEXT         NULL,
    mapeo                  TEXT         NULL,
    ejemplo                TEXT         NULL,
    activo                 BOOLEAN      NULL DEFAULT true,
    usuario_id             BIGINT       NULL,
    creado_en              TIMESTAMP    NULL DEFAULT NOW(),
    CONSTRAINT formato_terminal_pos_pkey PRIMARY KEY (id)
);

-- Indice, NO constraint unico: un UNIQUE en el subscriber puede abortar el apply si el central
-- manda un update transitorio que lo viole. Y ademas, en el modelo nuevo un proveedor SI puede
-- tener varios formatos --es el punto de la tabla--, asi que ni siquiera en central hay unicidad
-- por proveedor solo: alla es (proveedor_servicio_id, nombre).
CREATE INDEX IF NOT EXISTS idx_formato_terminal_pos_proveedor
    ON financiero.formato_terminal_pos (proveedor_servicio_id);

COMMENT ON TABLE financiero.formato_terminal_pos IS
    'Como se lee el ticket de un modelo de aparato. Reemplaza a formato_qr_pos, que queda viva y sin uso hasta que toda la flota corra el codigo nuevo.';
COMMENT ON COLUMN financiero.formato_terminal_pos.tipo IS
    'MAQUINA | WEB | API. Cierra el camino que no corresponde: una terminal WEB solo lee QR, una MAQUINA solo camara.';
COMMENT ON COLUMN financiero.formato_terminal_pos.mapeo IS
    'JSON: campo destino -> {de: grupo, obligatorio: bool, y opcionalmente mapa / escala / escalaSegunMoneda / formato+zona / mayusculas}. Los obligatorios deciden tres cosas: que debe encontrar el OCR, cuando el resultado es utilizable, y que campos pide la carga a mano.';

-- ── 2) financiero.terminal_pos: a que formato apunta ────────────────────────────────────────
--
-- Sin FK, por el mismo motivo que arriba: terminal_pos y formato_terminal_pos bajan por dos
-- streams y no hay garantia de orden entre ellos. Una FK aca convierte un desfasaje de segundos
-- en un corte de replicacion.
--
-- Nullable a proposito: el dia del corte TODAS las terminales de las 24 sucursales quedan en
-- NULL, porque no hay backfill --la asignacion se completa a mano por SQL. El desktop bloquea
-- la venta con tarjeta mientras la terminal no tenga formato, asi que ese SQL es
-- PRERREQUISITO de liberar la version del desktop, no una tarea posterior.
ALTER TABLE financiero.terminal_pos
    ADD COLUMN IF NOT EXISTS formato_terminal_pos_id BIGINT NULL;

COMMENT ON COLUMN financiero.terminal_pos.formato_terminal_pos_id IS
    'Formato del modelo de aparato que es esta terminal. NULL = sin configurar: el desktop bloquea la venta con tarjeta y pide que un administrador lo asigne.';
