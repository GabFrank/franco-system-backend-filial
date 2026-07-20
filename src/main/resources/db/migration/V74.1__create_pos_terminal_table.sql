CREATE TABLE financiero.terminal_pos (
    id SERIAL PRIMARY KEY,
    descripcion VARCHAR(255),
    codigo VARCHAR(55),
    cuenta_bancaria_id BIGINT,
    moneda_id BIGINT,
    activo BOOLEAN DEFAULT FALSE,
    creado_en TIMESTAMP,
    usuario_id BIGINT,
    CONSTRAINT fk_terminal_pos_cuenta_bancaria FOREIGN KEY (cuenta_bancaria_id) REFERENCES financiero.cuenta_bancaria(id),
    CONSTRAINT fk_terminal_pos_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id),
    CONSTRAINT fk_terminal_pos_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);
