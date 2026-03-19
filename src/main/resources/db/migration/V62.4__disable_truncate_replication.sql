-- V62.4: Deshabilitar propagación de TRUNCATE en filial_pub
-- El TRUNCATE replicado puede borrar datos operativos en el servidor central
DO $$
DECLARE
    v_pubname TEXT;
BEGIN
    FOR v_pubname IN
        SELECT pubname FROM pg_publication WHERE pubtruncate = true
    LOOP
        EXECUTE format('ALTER PUBLICATION %I SET (publish = ''insert, update, delete'')', v_pubname);
        RAISE NOTICE 'TRUNCATE deshabilitado en publicación: %', v_pubname;
    END LOOP;
END $$;
