CREATE SEQUENCE administrativo.jornada_id_seq
    START WITH 2
    INCREMENT BY 2
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE administrativo.jornada (
    id BIGINT NOT NULL DEFAULT nextval('administrativo.jornada_id_seq'),
    
    usuario_id BIGINT,
    fecha DATE NOT NULL,
    
    entrada_id BIGINT,
    salida_id BIGINT,
    
    minutos_trabajados BIGINT DEFAULT 0,
    minutos_extras BIGINT DEFAULT 0,
    minutos_llegada_tardia BIGINT DEFAULT 0,
    
    estado VARCHAR(30),
    observacion VARCHAR(255),
    actualizado_en TIMESTAMP WITHOUT TIME ZONE,
    
    CONSTRAINT pk_jornada PRIMARY KEY (id),
    
    CONSTRAINT fk_jornada_usuario 
        FOREIGN KEY (usuario_id) 
        REFERENCES personas.usuario (id),
        
    CONSTRAINT fk_jornada_entrada 
        FOREIGN KEY (entrada_id) 
        REFERENCES administrativo.marcacion (id),
        
    CONSTRAINT fk_jornada_salida 
        FOREIGN KEY (salida_id) 
        REFERENCES administrativo.marcacion (id),

    CONSTRAINT uq_jornada_usuario_fecha UNIQUE (usuario_id, fecha)
);
CREATE INDEX idx_jornada_usuario ON administrativo.jornada(usuario_id);
CREATE INDEX idx_jornada_fecha ON administrativo.jornada(fecha);