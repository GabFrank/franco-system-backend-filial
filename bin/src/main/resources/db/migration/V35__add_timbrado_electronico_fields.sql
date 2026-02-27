-- Agregar campos necesarios para documento electrónico en Timbrado
ALTER TABLE financiero.timbrado 
ADD COLUMN email VARCHAR(255),
ADD COLUMN tipo_sociedad VARCHAR(100),
ADD COLUMN domicilio_fiscal_departamento VARCHAR(100),
ADD COLUMN domicilio_fiscal_ciudad VARCHAR(100),
ADD COLUMN domicilio_fiscal_codigo_ciudad VARCHAR(10),
ADD COLUMN domicilio_fiscal_localidad VARCHAR(100),
ADD COLUMN domicilio_fiscal_barrio VARCHAR(100),
ADD COLUMN domicilio_fiscal_direccion VARCHAR(255),
ADD COLUMN telefono VARCHAR(50),
ADD COLUMN cod_actividad_economica_principal VARCHAR(20),
ADD COLUMN desc_actividad_economica_principal VARCHAR(255),
ADD COLUMN list_codigo_actividad_economica_secundaria TEXT,
ADD COLUMN list_descripcion_actividad_economica_secundaria TEXT;

-- Agregar campos necesarios para documento electrónico en TimbradoDetalle
ALTER TABLE financiero.timbrado_detalle 
ADD COLUMN departamento VARCHAR(100),
ADD COLUMN ciudad VARCHAR(100),
ADD COLUMN codigo_ciudad VARCHAR(10),
ADD COLUMN localidad VARCHAR(100),
ADD COLUMN barrio VARCHAR(100),
ADD COLUMN direccion VARCHAR(255),
ADD COLUMN telefono VARCHAR(50);
