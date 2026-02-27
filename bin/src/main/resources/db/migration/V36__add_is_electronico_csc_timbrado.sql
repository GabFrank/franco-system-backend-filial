-- Agregar campos is_electronico y csc a la tabla timbrado
ALTER TABLE financiero.timbrado 
ADD COLUMN is_electronico BOOLEAN DEFAULT FALSE,
ADD COLUMN csc VARCHAR(255);
