-- =============================================================================
-- V88.3 - Alinear la estructura de la filial con el central para el módulo
--         Financiero/Tesorería (PR central `feat/modulo-financiero`).
--
-- MOTIVO
-- El PR de central agrega columnas a varias tablas MAIN_TO_ALL (publicadas en
-- `central_pub`, dirección central→filial). Si la filial no tiene esas columnas,
-- el apply worker de la suscripción lógica entra en bucle de error:
--
--   ERROR: na relação de destino de replicação lógica «financiero.moneda» está
--          faltando colunas replicadas: «activo», «principal», «decimales», ...
--
-- El worker muere y reinicia cada ~5s, el slot queda inactivo reteniendo WAL en
-- el central y TODA la suscripción (no solo esa tabla) queda trabada. Es el mismo
-- modo de falla que resolvió V79.3 (para vehículo/equipo); esta migración hace lo
-- mismo para las tablas del módulo financiero.
--
-- ALCANCE / REGLAS
--   - Solo columnas faltantes; tipos y defaults copiados EXACTO del central
--     (V178.5, V180.5, V181.5, V183.5, V184.5, V187.5, y RRHH V154.0).
--   - SIN FKs nuevas: en un suscriptor lógico una FK puede rechazar el apply si
--     la fila padre todavía no llegó (moneda_principal_id, funcionario.moneda_id
--     quedan como bigint sin FK, igual que el criterio de V79.3).
--   - SIN el índice único parcial `uq_moneda_principal` del central: en el
--     suscriptor podría abortar el apply si llegan dos principal=true.
--   - Todo idempotente (ADD COLUMN IF NOT EXISTS): se puede correr más de una vez.
-- =============================================================================


-- =============================================================================
-- 1. financiero.moneda  (central V178.5)
-- =============================================================================
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS activo            boolean DEFAULT true;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS principal         boolean DEFAULT false;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS decimales         integer DEFAULT 0;
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS regla_redondeo    varchar(20);
ALTER TABLE financiero.moneda ADD COLUMN IF NOT EXISTS redondeo_multiplo numeric(18,4);


-- =============================================================================
-- 2. financiero.forma_pago  (central V178.5)
-- =============================================================================
ALTER TABLE financiero.forma_pago ADD COLUMN IF NOT EXISTS orden     integer DEFAULT 0;
ALTER TABLE financiero.forma_pago ADD COLUMN IF NOT EXISTS principal boolean DEFAULT false;


-- =============================================================================
-- 3. financiero.cuenta_bancaria  (central V180.5 + V187.5)
--    disponible_operaciones_financieras: en central se agrega sin default y el
--    default se setea después; acá basta que exista para el apply.
-- =============================================================================
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS saldo                              numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS saldo_reservado                    numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS titular                            varchar(150);
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS alias                              varchar(100);
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS activo                             boolean DEFAULT true;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS permite_saldo_negativo             boolean DEFAULT false;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS nombre                             varchar(150);
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS disponible_operaciones_financieras boolean DEFAULT true;


-- =============================================================================
-- 4. financiero.terminal_pos  (central V183.5)
-- =============================================================================
ALTER TABLE financiero.terminal_pos ADD COLUMN IF NOT EXISTS porcentaje_comision  numeric(6,3) DEFAULT 0;
ALTER TABLE financiero.terminal_pos ADD COLUMN IF NOT EXISTS minutos_acreditacion integer DEFAULT 0;


-- =============================================================================
-- 5. personas.cliente  (central V181.5)
-- =============================================================================
ALTER TABLE personas.cliente ADD COLUMN IF NOT EXISTS saldo_actual numeric(18,4) DEFAULT 0;


-- =============================================================================
-- 6. personas.funcionario  (central RRHH V154.0)
--    moneda_id sin FK (mismo criterio de subscriber que arriba).
-- =============================================================================
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS fecha_egreso    timestamp;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS motivo_egreso   varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS codigo_interno  varchar(50);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS ips_activo      boolean DEFAULT false;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS numero_ips      varchar(30);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS valor_jornal    numeric(18,2);
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS moneda_id       bigint;
ALTER TABLE personas.funcionario ADD COLUMN IF NOT EXISTS cuenta_bancaria varchar(100);


-- =============================================================================
-- 7. empresarial.configuracion_general  (central V178.5 + V184.5)
--    moneda_principal_id sin FK (subscriber).
-- =============================================================================
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS timbrado_numero         varchar(30);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS timbrado_vigencia_hasta date;
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS punto_expedicion        varchar(10);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS zona_horaria            varchar(60) DEFAULT 'America/Asuncion';
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS moneda_principal_id     bigint;
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS logo_url                varchar(300);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS actividad_economica     varchar(200);
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS dias_limite_anulacion   integer;
