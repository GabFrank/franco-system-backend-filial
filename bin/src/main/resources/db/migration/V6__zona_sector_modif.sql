ALTER TABLE empresarial.sector DROP CONSTRAINT sector_un;
ALTER TABLE empresarial.sector ADD CONSTRAINT sector_unique UNIQUE (sucursal_id,descripcion);
ALTER TABLE empresarial.zona DROP CONSTRAINT zona_un;
ALTER TABLE empresarial.zona ADD CONSTRAINT zona_unique UNIQUE (sector_id,descripcion);
