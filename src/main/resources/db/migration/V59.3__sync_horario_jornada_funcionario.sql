-- Migración V59.3 - Sincronización de Horarios, Jornadas y Funcionarios

-- Crear tabla administrativo.horario
CREATE SEQUENCE IF NOT EXISTS administrativo.horario_id_seq START WITH 2 INCREMENT BY 2 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS administrativo.horario (
    id BIGINT NOT NULL DEFAULT nextval('administrativo.horario_id_seq'),
    descripcion VARCHAR(255),
    hora_entrada TIME,
    hora_salida TIME,
    tolerancia_minutos INTEGER,
    inicio_descanso TIME,
    fin_descanso TIME,
    turno VARCHAR(20),
    usuario_id BIGINT,
    creado_en TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_horario PRIMARY KEY (id),
    CONSTRAINT fk_horario_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id)
);

-- Crear tabla para los días del horario (ElementCollection)
CREATE TABLE IF NOT EXISTS administrativo.horario_dias (
    horario_id BIGINT NOT NULL,
    dia VARCHAR(20) NOT NULL,
    CONSTRAINT fk_horario_dias_horario FOREIGN KEY (horario_id) REFERENCES administrativo.horario (id)
);

-- Actualizar tabla personas.funcionario para incluir el horario
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS horario_id BIGINT;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_funcionario_horario') THEN
        ALTER TABLE personas.funcionario ADD CONSTRAINT fk_funcionario_horario FOREIGN KEY (horario_id) REFERENCES administrativo.horario (id);
    END IF;
END$$;

-- Actualizar tabla administrativo.jornada con nuevos campos de cálculo y almuerzo
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS marcacion_salida_almuerzo_id BIGINT;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS marcacion_entrada_almuerzo_id BIGINT;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS minutos_llegada_tardia_almuerzo BIGINT DEFAULT 0;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS turno VARCHAR(20);
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS hora_entrada_horario TIME;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS hora_salida_horario TIME;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS inicio_descanso_horario TIME;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS fin_descanso_horario TIME;
ALTER TABLE administrativo.jornada ADD COLUMN IF NOT EXISTS tolerancia_minutos_horario INTEGER;

-- Constraints para las nuevas marcaciones de la jornada
ALTER TABLE administrativo.jornada ADD CONSTRAINT fk_jornada_salida_almuerzo FOREIGN KEY (marcacion_salida_almuerzo_id) REFERENCES administrativo.marcacion (id);
ALTER TABLE administrativo.jornada ADD CONSTRAINT fk_jornada_entrada_almuerzo FOREIGN KEY (marcacion_entrada_almuerzo_id) REFERENCES administrativo.marcacion (id);
