-- Add columns that central added via V114 and V116 to keep replication in sync
ALTER TABLE configuraciones.local ADD COLUMN IF NOT EXISTS nombre_base_datos_master VARCHAR;
ALTER TABLE configuraciones.local ADD COLUMN IF NOT EXISTS nombre_base_datos_filial VARCHAR;
ALTER TABLE configuraciones.local ADD COLUMN IF NOT EXISTS ip_servidor_central VARCHAR;
ALTER TABLE configuraciones.local ADD COLUMN IF NOT EXISTS puerto_servidor_central INTEGER;
