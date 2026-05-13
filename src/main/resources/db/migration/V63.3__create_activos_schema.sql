CREATE SCHEMA IF NOT EXISTS activos;

CREATE TABLE activos.familia_mueble (
    id BIGSERIAL PRIMARY KEY,
    descripcion VARCHAR(255),
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activos.tipo_mueble (
    id BIGSERIAL PRIMARY KEY,
    descripcion VARCHAR(255),
    familia_mueble_id BIGINT REFERENCES activos.familia_mueble(id),
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activos.ente (
    id BIGSERIAL PRIMARY KEY,
    tipo_ente VARCHAR(50),
    referencia_id BIGINT,
    activo BOOLEAN,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activos.inmueble (
    id BIGSERIAL PRIMARY KEY,
    propietario_id BIGINT,
    nombre_asignado VARCHAR(255),
    pais_id BIGINT,
    ciudad_id BIGINT,
    direccion VARCHAR(255),
    google_maps_url TEXT,
    codigo_catastral VARCHAR(100),
    valor_tasacion DECIMAL(19, 2),
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activos.mueble (
    id BIGSERIAL PRIMARY KEY,
    propietario_id BIGINT,
    identificador VARCHAR(100),
    descripcion TEXT,
    familia_id BIGINT REFERENCES activos.familia_mueble(id),
    tipo_mueble_id BIGINT REFERENCES activos.tipo_mueble(id),
    consume_energia BOOLEAN,
    consumo_valor VARCHAR(50),
    valor_tasacion DECIMAL(19, 2),
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE activos.ente_archivo (
    id BIGSERIAL PRIMARY KEY,
    ente_id BIGINT REFERENCES activos.ente(id),
    tipo_archivo VARCHAR(50),
    url TEXT,
    descripcion VARCHAR(255),
    vigente BOOLEAN,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE financiero.ente_financiero (
    id BIGSERIAL PRIMARY KEY,
    ente_id BIGINT REFERENCES activos.ente(id),
    situacion_pago VARCHAR(50),
    proveedor_id BIGINT,
    moneda_id BIGINT,
    monto_total DECIMAL(19, 2),
    monto_ya_pagado DECIMAL(19, 2),
    cantidad_cuotas INTEGER,
    dia_vencimiento INTEGER,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE financiero.ente_cuota (
    id BIGSERIAL PRIMARY KEY,
    ente_financiero_id BIGINT REFERENCES financiero.ente_financiero(id),
    numero_cuota INTEGER,
    monto DECIMAL(19, 2),
    pagado BOOLEAN,
    fecha_vencimiento DATE,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE financiero.ente_vinculacion (
    id BIGSERIAL PRIMARY KEY,
    ente_id BIGINT REFERENCES activos.ente(id),
    sucursal_id BIGINT,
    es_propio BOOLEAN,
    alquiler_proveedor_id BIGINT,
    alquiler_monto DECIMAL(19, 2),
    alquiler_dia_vencimiento INTEGER,
    alquiler_vigencia DATE,
    observacion TEXT,
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehiculos.tipo_combustible (
    id BIGSERIAL PRIMARY KEY,
    descripcion VARCHAR(150),
    usuario_id BIGINT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE vehiculos.vehiculo 
ADD COLUMN propietario_id BIGINT,
ADD COLUMN identificador_interno VARCHAR(100),
ADD COLUMN tipo_combustible_id BIGINT REFERENCES vehiculos.tipo_combustible(id),
ADD COLUMN chasis VARCHAR(150),
ADD COLUMN aire_acondicionado BOOLEAN,
ADD COLUMN valor_estimado DECIMAL(19, 2),
ADD COLUMN mantenimiento_motor_intervalo INTEGER,
ADD COLUMN mantenimiento_caja_intervalo INTEGER;
