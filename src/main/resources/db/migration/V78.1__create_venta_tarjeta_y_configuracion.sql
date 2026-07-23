-- Venta con tarjeta offline-first: el filial pasa a ser el creador de venta_tarjeta
-- (replica BRANCH_TO_MAIN al central) y recibe configuracion_venta_tarjeta replicada
-- del central (MAIN_TO_ALL). DDL espejo del central V140.1 + V141.2 + V146.1.

CREATE TABLE IF NOT EXISTS financiero.venta_tarjeta (
    id                  BIGSERIAL,
    sucursal_id         BIGINT NOT NULL,
    venta_id            BIGINT NOT NULL,
    terminal_pos_id     BIGINT,
    caja_id             BIGINT NOT NULL,
    codigo_autorizacion VARCHAR(100),
    numero_boleta       VARCHAR(100),
    monto               NUMERIC(18,2) NOT NULL,
    imagen_url          VARCHAR(500),
    usuario_id          BIGINT,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    monto_escaneado     NUMERIC(18,2),
    PRIMARY KEY (id, sucursal_id),
    CONSTRAINT fk_vt_terminal_pos FOREIGN KEY (terminal_pos_id) REFERENCES financiero.terminal_pos(id),
    CONSTRAINT fk_vt_usuario      FOREIGN KEY (usuario_id)      REFERENCES personas.usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_venta_tarjeta_venta_id ON financiero.venta_tarjeta(venta_id, sucursal_id);
CREATE INDEX IF NOT EXISTS idx_venta_tarjeta_caja_id  ON financiero.venta_tarjeta(caja_id, sucursal_id);
CREATE INDEX IF NOT EXISTS idx_venta_tarjeta_estado   ON financiero.venta_tarjeta(caja_id, sucursal_id, estado);

-- SIN seed: la fila unica de configuracion llega por replicacion logica desde el central.
CREATE TABLE IF NOT EXISTS financiero.configuracion_venta_tarjeta (
    id            BIGSERIAL PRIMARY KEY,
    habilitado    BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_id    BIGINT,
    creado_en     TIMESTAMP DEFAULT NOW(),
    modificado_en TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_configuracion_venta_tarjeta_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id)
);
