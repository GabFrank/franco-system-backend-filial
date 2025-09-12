-- Add tipoContribuyente field to cliente table
ALTER TABLE personas.cliente ADD COLUMN tipo_contribuyente INTEGER DEFAULT NULL;

-- Add cdc field to factura_legal table
ALTER TABLE financiero.factura_legal ADD COLUMN cdc VARCHAR(255) DEFAULT NULL;





