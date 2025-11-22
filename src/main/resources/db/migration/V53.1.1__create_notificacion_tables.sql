CREATE SCHEMA IF NOT EXISTS configuraciones;

CREATE TABLE IF NOT EXISTS configuraciones.notificacion (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    mensaje TEXT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    data TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    intentos_envio INTEGER DEFAULT 0,
    ultimo_error TEXT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notificacion_tipo ON configuraciones.notificacion(tipo);
CREATE INDEX IF NOT EXISTS idx_notificacion_estado ON configuraciones.notificacion(estado);
CREATE INDEX IF NOT EXISTS idx_notificacion_creado_en ON configuraciones.notificacion(creado_en);

CREATE TABLE IF NOT EXISTS configuraciones.notificacion_usuario (
    id BIGSERIAL PRIMARY KEY,
    notificacion_id BIGINT NOT NULL REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES personas.usuario(id) ON DELETE CASCADE,
    token_fcm VARCHAR(500),
    estado_envio VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_envio TIMESTAMP,
    fecha_entrega TIMESTAMP,
    leida BOOLEAN DEFAULT FALSE,
    fecha_leida TIMESTAMP,
    interactuada BOOLEAN DEFAULT FALSE,
    fecha_interaccion TIMESTAMP,
    accion_realizada VARCHAR(100),
    mensaje_error TEXT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notificacion_usuario UNIQUE (notificacion_id, usuario_id, token_fcm)
);

CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_usuario
    ON configuraciones.notificacion_usuario(usuario_id);
CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_estado
    ON configuraciones.notificacion_usuario(estado_envio);
CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_leida
    ON configuraciones.notificacion_usuario(leida);
CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_fecha_envio
    ON configuraciones.notificacion_usuario(fecha_envio);
CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_notificacion
    ON configuraciones.notificacion_usuario(notificacion_id);
