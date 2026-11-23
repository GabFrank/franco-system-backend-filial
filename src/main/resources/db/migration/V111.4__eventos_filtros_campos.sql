-- =====================================================================
-- Eventos por webhook, fase 2 (filial #246): filtros, campos y datos sensibles por suscripcion
-- =====================================================================
-- Espejo MAIN_TO_ALL: las columnas nuevas de configuraciones.webhook_suscripcion las publica el
-- central con su V267.4. ⚠️ ESTA VA ANTES, en todas las filiales del ambiente: si el central publica
-- columnas que la filial no tiene, se corta la replicacion entrante. Mientras el central no tenga la
-- V267.4 quedan en NULL y todo funciona como en la 1a. Idempotente: se puede correr a mano.
--
--   filtros          {"tipo": {"atributo": [valores]}}
--   campos           {"tipo": ["ruta", ...]}
--   datos_sensibles  autorizacion, boleta, QR crudo e identificador de transaccion
-- =====================================================================
ALTER TABLE configuraciones.webhook_suscripcion ADD COLUMN IF NOT EXISTS filtros JSONB;
ALTER TABLE configuraciones.webhook_suscripcion ADD COLUMN IF NOT EXISTS campos JSONB;
ALTER TABLE configuraciones.webhook_suscripcion ADD COLUMN IF NOT EXISTS datos_sensibles BOOLEAN;

-- Local: atributos de filtro del hecho, chicos y separados del data
ALTER TABLE eventos.outbox_evento ADD COLUMN IF NOT EXISTS atributos JSONB;

-- eventos.nodo_estado NO se toca: es BRANCH_TO_MAIN y una columna que el central no tiene cortaria la
-- replicacion saliente. Los filtros invalidos van en el latido (data.filtros_invalidos) hasta que el
-- central sume la columna (#587).
