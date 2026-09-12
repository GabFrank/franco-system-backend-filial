-- =====================================================================
-- Espejo de la V221.1 del central: personas.funcionario.cobra_banco
-- =====================================================================
-- personas.funcionario se replica MAIN_TO_ALL, asi que la filial necesita
-- la columna aunque no la use: sin ella la replicacion logica de la fila
-- falla en el suscriptor.
--
-- Aditiva: columna nullable con DEFAULT false. Idempotente.
-- =====================================================================

ALTER TABLE personas.funcionario
    ADD COLUMN IF NOT EXISTS cobra_banco boolean DEFAULT false;
