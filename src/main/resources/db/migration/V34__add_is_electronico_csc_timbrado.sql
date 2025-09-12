-- Add isElectronico field to timbrado table
ALTER TABLE financiero.timbrado ADD COLUMN is_electronico BOOLEAN DEFAULT FALSE;

-- Add csc field to timbrado table
ALTER TABLE financiero.timbrado ADD COLUMN csc VARCHAR(255) DEFAULT NULL;
