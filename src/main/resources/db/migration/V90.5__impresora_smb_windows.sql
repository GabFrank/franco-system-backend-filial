-- Ver V201.5__impresora_smb_windows.sql (servidor). Columnas espejo para que la replicacion
-- logica (MAIN_TO_ALL, impresora es unidireccional central -> filial) aplique sin conflictos
-- de esquema.
ALTER TABLE empresarial.impresora
    ADD COLUMN IF NOT EXISTS smb_host VARCHAR(120),
    ADD COLUMN IF NOT EXISTS smb_recurso VARCHAR(255),
    ADD COLUMN IF NOT EXISTS smb_usuario VARCHAR(120),
    ADD COLUMN IF NOT EXISTS smb_dominio VARCHAR(120);
