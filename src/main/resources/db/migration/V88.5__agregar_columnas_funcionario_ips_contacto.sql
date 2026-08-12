-- Espeja a central V169.0 (funcionario_ips_contacto_emergencia): columnas que el desktop
-- feat/modulo-financiero consulta en funcionarioQuery/saveFuncionario y que el filial no tenia.
-- V88.3 solo alineo lo de RRHH V154.0; central avanzo con V169.0 despues y faltaba el espejo.
-- Aditiva, IF NOT EXISTS, subscriber-safe (sin FKs ni indices unicos).

ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS fecha_ingreso_ips date;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS contacto_emergencia_nombre varchar(150);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS contacto_emergencia_telefono varchar(50);
