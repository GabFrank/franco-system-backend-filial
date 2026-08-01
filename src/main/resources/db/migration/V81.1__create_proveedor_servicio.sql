-- Proveedor de servicios (empresas que proveen las terminales POS y su soporte).
-- DDL espejo del central V152.1. Aca la tabla solo necesita EXISTIR para que la
-- replicacion logica MAIN_TO_ALL pueda aplicar las filas que manda el central.
--
-- Sin FKs a proposito: durante el initial sync de una suscripcion las tablas se
-- copian en paralelo, asi que una FK a personas.persona / financiero.cuenta_bancaria
-- puede dispararse antes de que la tabla referenciada termine de copiarse y frenar
-- el apply worker. El central es el unico que crea filas acá, y alli si hay FKs.

CREATE TABLE IF NOT EXISTS personas.proveedor_servicio (
    id                 BIGSERIAL PRIMARY KEY,
    persona_id         BIGINT,
    cuenta_bancaria_id BIGINT,
    nombre_contacto    VARCHAR(255),
    numero_contacto    VARCHAR(50),
    usuario_id         BIGINT,
    creado_en          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proveedor_servicio_persona
    ON personas.proveedor_servicio(persona_id);
