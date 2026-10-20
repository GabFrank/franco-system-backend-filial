-- =====================================================================
-- configuracion_cobro: tope del Aumento (F8) del cobro (desktop #546)
-- =====================================================================
-- Hasta ahora el tope era un 20% del total clavado en el desktop, y solo lo respetaba el boton
-- (F8 y el delivery no tenian tope). Esta tabla lo vuelve configurable desde el central: con tope
-- (porcentaje) o sin tope. El desktop la lee de ESTE servidor al abrir el cobro, para que funcione
-- sin internet.
--
-- Tabla VACIA = con tope del 20%, lo que hacia el boton (ConfiguracionCobroService.findOrDefault).
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DEL CENTRAL (V251.1)
--
-- Es una tabla MAIN_TO_ALL nueva. Cuando el central la agregue a central_pub y refresque las
-- suscripciones, el REFRESH de una filial que no la tenga falla entero. Esta migracion se despliega
-- (y se verifica en flyway_schema_history de cada filial) antes que la del central.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismas columnas y tipos que el central, todas nullable. PK en id (la usa el apply para ubicar la
-- fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin seed: las restricciones viven en el
-- central, que es el publisher. El filial nunca escribe esta tabla.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_cobro (
    id                      BIGSERIAL PRIMARY KEY,
    aumento_con_tope        BOOLEAN,
    aumento_tope_porcentaje NUMERIC(6, 2),
    usuario_id              BIGINT,
    creado_en               TIMESTAMP,
    modificado_en           TIMESTAMP
);

COMMENT ON TABLE financiero.configuracion_cobro IS
    'Configuracion del cobro del PDV. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee. Sin filas = aumento con tope del 20%.';

COMMENT ON COLUMN financiero.configuracion_cobro.aumento_con_tope IS
    'true = el aumento del cobro no puede pasar aumento_tope_porcentaje del total; false = sin tope. NULL = true.';

COMMENT ON COLUMN financiero.configuracion_cobro.aumento_tope_porcentaje IS
    'Tope del aumento, en porcentaje del total del cobro. NULL = 20.';
