-- Eliminar la restriccion de unicidad (usuario_id, fecha) de la tabla jornada
-- Esto permite que un usuario tenga multiples jornadas en el mismo dia
-- Equivalente a V107.3 del servidor
ALTER TABLE administrativo.jornada DROP CONSTRAINT IF EXISTS uq_jornada_usuario_fecha;
