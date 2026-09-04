-- Espejo de la estructura que CENTRAL crea en V216.5 (formato de QR impreso por el POS).
--
-- POR QUE ESTA MIGRACION EXISTE Y POR QUE VA PRIMERO
--
-- La replicacion logica propaga DML, no DDL. Si central empieza a replicar filas de una tabla
-- que en el filial no existe, o con una columna que en el filial falta, el apply worker
-- central→filial crashea:
--     ERROR: logical replication target relation "..." is missing replicated columns
-- y entra en crash-loop cada 5s con el slot inactivo reteniendo WAL. Es exactamente el corte
-- del 2026-08-20 con tipo_dispositivo, y lo que V89.5 documenta.
--
-- Por eso esta migracion se despliega en TODA la flota ANTES que la V216.5 del central.
-- Mismo patron que V81.2 respecto de la V153.2 de central.
--
-- Todo aditivo e idempotente. Sin FK a personas.proveedor_servicio: si por cualquier motivo la
-- fila del proveedor todavia no bajo por replicacion, una FK haria fallar el apply de la fila
-- del formato. El filial es subscriber: valida el central, no el filial.

-- ── 1) financiero.formato_qr_pos ────────────────────────────────────────────────────────────
--
-- Una fila por proveedor de servicio: como se lee el QR que imprime SU maquinita.
-- El filial solo LEE esta tabla (la carga y la edita el central); llega por MAIN_TO_ALL.
CREATE TABLE IF NOT EXISTS financiero.formato_qr_pos (
    id                     BIGINT       NOT NULL,
    nombre                 VARCHAR(100) NOT NULL,
    proveedor_servicio_id  BIGINT       NULL,
    patron                 TEXT         NOT NULL,
    mapeo                  TEXT         NOT NULL,
    ejemplo                TEXT         NOT NULL,
    activo                 BOOLEAN      NOT NULL DEFAULT true,
    usuario_id             BIGINT       NULL,
    creado_en              TIMESTAMP    NULL DEFAULT NOW(),
    CONSTRAINT formato_qr_pos_pkey PRIMARY KEY (id)
);

-- Indice, NO constraint unico: un UNIQUE en el subscriber puede abortar el apply si el central
-- manda un update transitorio que lo viole. La unicidad se garantiza en el central (V216.5).
CREATE INDEX IF NOT EXISTS idx_formato_qr_pos_proveedor
    ON financiero.formato_qr_pos (proveedor_servicio_id);

-- ── 2) financiero.venta_tarjeta: la cadena cruda escaneada ──────────────────────────────────
--
-- Sin esto no hay forma de diagnosticar un cupon que parseo mal: el cajero ya se fue, el ticket
-- termico se borro y los campos quedaron a medias. Se guarda tal cual entro, sin normalizar.
-- 512 es el tope que el parser acepta como entrada (defensa contra ReDoS).
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS qr_crudo VARCHAR(512) NULL;
