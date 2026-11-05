-- =====================================================================
-- Reabrir una caja PDV cerrada por equivocacion (issue desktop #662)
-- =====================================================================
-- Un ADMIN reabre la caja con un motivo (PdvCajaService.reabrirCaja). Cada reapertura deja una fila
-- aca: quien, cuando, por que y que conteo de cierre se descarto. El conteo anterior no se borra.
--
-- BRANCH_TO_MAIN, INSERT-ONLY: la escribe solo la filial y sube al central. El central la registra
-- en replication_table (central V266.3) y ReplicationPublicationSyncScheduler la agrega a la
-- *_filialX_pub de cada filial. Mismo camino que operaciones.sorteo_participacion (V109.3).
--
-- ORDEN DE DESPLIEGUE: esta va ANTES que la del central, en cada filial.
-- copy_data=false: una reapertura hecha antes de que la tabla entre a la publicacion y el central
-- refresque su suscripcion queda solo en la filial.
--
-- Lado subscriber del central: sin FK (las filas llegan sin orden garantizado).
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS financiero;

CREATE TABLE IF NOT EXISTS financiero.pdv_caja_reapertura (
    id                        BIGSERIAL PRIMARY KEY,
    pdv_caja_id               BIGINT    NOT NULL,
    sucursal_id               BIGINT    NOT NULL,
    conteo_cierre_anterior_id BIGINT,
    fecha_cierre_anterior     TIMESTAMP,
    motivo                    VARCHAR   NOT NULL,
    usuario_id                BIGINT,
    creado_en                 TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pdv_caja_reapertura_caja
    ON financiero.pdv_caja_reapertura (pdv_caja_id);

ALTER TABLE financiero.pdv_caja_reapertura REPLICA IDENTITY FULL;

COMMENT ON TABLE financiero.pdv_caja_reapertura IS
    'Reaperturas de cajas PDV hechas por un ADMIN, con motivo. BRANCH_TO_MAIN, INSERT-ONLY.';
