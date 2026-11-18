-- #526: borra configuraciones.actualizacion, del modulo viejo de actualizacion. El codigo que la
-- usaba ya se quito (frc-sistemas-integrados-angular#632) y la tabla quedo sin lectores ni escritores.
--
-- Desplegar DESPUES del central (V282.3 del central), que la saca de central_pub. Si la filial la
-- borrara mientras el central todavia la publica, el REFRESH PUBLICATION de la suscripcion fallaria
-- por la tabla faltante.

DO $$
DECLARE
    r record;
BEGIN
    IF to_regclass('configuraciones.actualizacion') IS NULL THEN
        RETURN;
    END IF;

    -- Por las dudas: nunca borrar datos en silencio.
    IF EXISTS (SELECT 1 FROM configuraciones.actualizacion) THEN
        RAISE EXCEPTION 'configuraciones.actualizacion tiene datos; revisar antes de borrarla (#526)';
    END IF;

    -- La tabla es MAIN_TO_ALL y no deberia estar en publicaciones de la filial, pero si alguna la
    -- incluye, sacarla antes del DROP.
    FOR r IN
        SELECT pt.pubname
        FROM pg_publication_tables pt
        JOIN pg_publication p ON p.pubname = pt.pubname
        WHERE pt.schemaname = 'configuraciones'
          AND pt.tablename = 'actualizacion'
          AND NOT p.puballtables
    LOOP
        EXECUTE format('ALTER PUBLICATION %I DROP TABLE configuraciones.actualizacion', r.pubname);
    END LOOP;
END $$;

DROP TABLE IF EXISTS configuraciones.actualizacion;
DROP SEQUENCE IF EXISTS configuraciones.actualizacion_id_seq;
DROP TYPE IF EXISTS configuraciones.tipo_actualizacion;
DROP TYPE IF EXISTS configuraciones.nivel_actualizacion;
