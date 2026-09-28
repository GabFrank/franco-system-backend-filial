-- Espejo de la migracion del central que generaliza documento_electronico para las notas
-- electronicas (nota de credito y nota de remision).
--
-- Las notas las emite SOLO el central y su DE se guarda con factura_legal_id NULL y
-- nota_credito_id / nota_remision_id cargado. Esa fila baja a las filiales por la replicacion
-- logica: con la columna NOT NULL el apply worker muere ("null value in column
-- factura_legal_id violates not-null") y la suscripcion se corta entera.
--
-- Sin FK a las tablas de notas (el filial no las tiene) y sin CHECK: el filial nunca escribe
-- estas columnas. Aditiva: el JAR anterior sigue cargando factura_legal_id siempre.
ALTER TABLE financiero.documento_electronico ALTER COLUMN factura_legal_id DROP NOT NULL;

ALTER TABLE financiero.documento_electronico
    ADD COLUMN IF NOT EXISTS nota_credito_id  BIGINT NULL,
    ADD COLUMN IF NOT EXISTS nota_remision_id BIGINT NULL;
