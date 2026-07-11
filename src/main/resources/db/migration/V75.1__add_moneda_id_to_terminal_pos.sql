ALTER TABLE financiero.terminal_pos
    ADD COLUMN moneda_id BIGINT,
    ADD CONSTRAINT fk_terminal_pos_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);
