-- puerto_servidor almacena el puerto HTTP/GraphQL de la app de la filial,
-- independiente del puerto de Postgres usado para la replicacion.
ALTER TABLE empresarial.sucursal
    ADD COLUMN IF NOT EXISTS puerto_servidor INTEGER;