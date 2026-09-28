-- Reparto de ids con el central en las tablas que este filial replica al central:
-- el filial genera pares y el central impares (migracion espejo V223.1 del
-- central). Es el mismo patron que ya usan marcacion, jornada y movimiento_stock.
-- Sin esto los dos lados generaban ids en el mismo espacio y el INSERT replicado
-- del filial chocaba con la clave primaria, cortando la suscripcion entera.

-- Deja la secuencia en el proximo par mayor que todo lo ya usado: lo que hay en la
-- tabla, la propia secuencia y un minimo opcional. El minimo lo manda la mutation
-- alinearSecuenciasFiliales del central: el mayor id par que el central ya tiene
-- para esta sucursal. Correrla de nuevo no hace dano, solo deja un hueco.
CREATE OR REPLACE FUNCTION configuraciones.alinear_secuencia_par(secuencia regclass, tabla regclass, minimo bigint DEFAULT 0)
    RETURNS bigint
    LANGUAGE plpgsql AS
$$
DECLARE
    maximo  bigint;
    proximo bigint;
BEGIN
    EXECUTE format('SELECT GREATEST(COALESCE((SELECT MAX(id) FROM %s), 0), (SELECT last_value FROM %s), $1)',
                   tabla, secuencia)
        INTO maximo
        USING COALESCE(minimo, 0);
    proximo := maximo + CASE WHEN maximo % 2 = 0 THEN 2 ELSE 1 END;
    EXECUTE format('ALTER SEQUENCE %s INCREMENT BY 2', secuencia);
    PERFORM setval(secuencia, proximo, false);
    RETURN proximo;
END;
$$;

DO $$
DECLARE
    t record;
BEGIN
    FOR t IN SELECT * FROM (VALUES
            ('configuraciones.inicio_sesion_id_seq', 'configuraciones.inicio_sesion'),
            ('financiero.gasto_id_seq', 'financiero.gasto'),
            ('financiero.venta_tarjeta_id_seq', 'financiero.venta_tarjeta'),
            ('financiero.maletin_id_seq', 'financiero.maletin'),
            ('financiero.movimiento_personas_id_seq', 'financiero.movimiento_personas')
        ) AS v(secuencia, tabla)
    LOOP
        IF to_regclass(t.secuencia) IS NOT NULL AND to_regclass(t.tabla) IS NOT NULL THEN
            PERFORM configuraciones.alinear_secuencia_par(t.secuencia::regclass, t.tabla::regclass);
        END IF;
    END LOOP;
END;
$$;
