ALTER TABLE financiero.cambio_caja ADD caja_id int8 NULL;
ALTER TABLE financiero.cambio_caja ADD CONSTRAINT cambio_caja_pdv_caja_fk FOREIGN KEY (caja_id) REFERENCES financiero.pdv_caja(id) ON DELETE CASCADE ON UPDATE CASCADE;
