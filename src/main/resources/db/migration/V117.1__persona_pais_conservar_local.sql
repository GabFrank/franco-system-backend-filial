-- =====================================================================
-- personas.persona: la replica del central no borra el pais cargado en la filial (#666, etapa 2 de #134)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El central va a tener su propia columna pais_id (V287.1 del central). central_pub publica persona sin lista
-- de columnas y en REPLICA IDENTITY FULL, asi que cada UPDATE de una persona baja la fila entera: con el
-- pais todavia NULL en el central, pisaria el pais que la caja cargo aca (V116.1). Una lista de columnas que
-- excluya pais_id no se puede: con identity FULL PG exige que cubra todas las columnas.
--
-- QUE HACE
--
-- Un trigger BEFORE UPDATE que, si llega pais_id NULL sobre una fila que tiene pais, conserva el local.
-- ENABLE REPLICA: corre solo en el apply de la replica (session_replication_role=replica), no en los UPDATE
-- de la aplicacion. Un pais distinto que mande el central (una correccion) se aplica igual.
-- Medido en PG 16.13 con identity FULL en los dos lados: el UPDATE de otros campos se sigue aplicando aunque
-- pais_id difiera (PG 16 busca la fila por la PK). En PG <= 15 la busqueda con identity FULL compara todas
-- las columnas: el despliegue del central exige PG >= 16 en las filiales del canal.
--
-- ORDEN: va antes que la V287.1 del central, en cada canal.
--
-- ROLLBACK (si hiciera falta, a mano):
--   DROP TRIGGER IF EXISTS persona_conservar_pais ON personas.persona;
--   DROP FUNCTION IF EXISTS personas.persona_conservar_pais_local();
-- IDEMPOTENTE.

ALTER TABLE personas.persona ADD COLUMN IF NOT EXISTS pais_id bigint;

CREATE OR REPLACE FUNCTION personas.persona_conservar_pais_local() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.pais_id := OLD.pais_id;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS persona_conservar_pais ON personas.persona;

CREATE TRIGGER persona_conservar_pais
    BEFORE UPDATE ON personas.persona
    FOR EACH ROW
    WHEN (NEW.pais_id IS NULL AND OLD.pais_id IS NOT NULL)
    EXECUTE FUNCTION personas.persona_conservar_pais_local();

ALTER TABLE personas.persona ENABLE REPLICA TRIGGER persona_conservar_pais;
