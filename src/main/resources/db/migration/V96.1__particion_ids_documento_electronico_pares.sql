-- Reparto de ids con el central en las tablas de facturacion electronica: el filial genera
-- pares y el central impares (migracion espejo del central). Mismo patron que V94.1.
--
-- Estas cuatro tablas viajan en las dos direcciones: el filial publica sus DE, lotes y eventos
-- al central, y el central baja a la filial los que emite para esa sucursal (hoy
-- crearDocumentoElectronicoDesdeFactura; con las notas electronicas, todos sus DE). Con los dos
-- nodos generando ids en el mismo espacio, el INSERT replicado choca con la clave primaria y
-- corta la suscripcion.
--
-- Reusa configuraciones.alinear_secuencia_par, creada en V94.1.
DO $$
DECLARE
    t record;
BEGIN
    FOR t IN SELECT * FROM (VALUES
            ('financiero.documento_electronico_id_seq', 'financiero.documento_electronico'),
            ('financiero.lote_de_id_seq', 'financiero.lote_de'),
            ('financiero.evento_cancelacion_de_id_seq', 'financiero.evento_cancelacion_de'),
            ('financiero.evento_nominacion_de_id_seq', 'financiero.evento_nominacion_de')
        ) AS v(secuencia, tabla)
    LOOP
        IF to_regclass(t.secuencia) IS NOT NULL AND to_regclass(t.tabla) IS NOT NULL THEN
            PERFORM configuraciones.alinear_secuencia_par(t.secuencia::regclass, t.tabla::regclass);
        END IF;
    END LOOP;
END;
$$;
