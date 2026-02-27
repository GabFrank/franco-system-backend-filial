ALTER TABLE financiero.sencillo ADD caja_entrada_id int8 NULL;
ALTER TABLE financiero.sencillo ADD caja_salida_id int8 NULL;
ALTER TABLE financiero.sencillo ADD CONSTRAINT sencillo_caja_entrada_fk FOREIGN KEY (caja_entrada_id) REFERENCES financiero.pdv_caja(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE financiero.sencillo ADD CONSTRAINT sencillo_caja_salida_fk FOREIGN KEY (caja_salida_id) REFERENCES financiero.pdv_caja(id);
