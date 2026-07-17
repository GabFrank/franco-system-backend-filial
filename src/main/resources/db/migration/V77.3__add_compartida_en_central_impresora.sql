-- Ver V148.3__add_compartida_en_central_impresora.sql (servidor). Columna espejo para que la
-- replicacion logica (MAIN_TO_ALL, impresora es unidireccional central -> filial) aplique sin
-- conflictos de esquema.
ALTER TABLE empresarial.impresora
    ADD COLUMN IF NOT EXISTS compartida_en_central BOOLEAN DEFAULT FALSE;
