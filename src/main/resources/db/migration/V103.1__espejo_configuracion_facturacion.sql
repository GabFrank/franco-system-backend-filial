-- =====================================================================
-- configuracion_facturacion: politica de facturacion por sucursal (issue #127)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Hoy la decision "esta venta se factura" la toma el contador facturaCountDown, una property
-- de application.properties que se edita a mano en cada filial, y el boton "Venta + Ticket" se
-- la saltea. Esta tabla lleva la politica a un lugar administrable desde el central: una fila
-- global (sucursal_id NULL) y overrides por sucursal. El filial la lee en cada venta, sin cache.
--
-- Tabla VACIA = comportamiento de hoy: el filial cae a la property facturaCountDown.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTA VA ANTES QUE LA DE CENTRAL
--
-- Es una tabla MAIN_TO_ALL nueva. Cuando el central la agregue a central_pub y refresque las
-- suscripciones, el REFRESH de una filial que no la tenga falla entero. Por eso esta migracion
-- se despliega (y se verifica en flyway_schema_history de cada filial) antes que la del central.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Mismas columnas y tipos que el central, todas nullable. PK en id (la usa el apply para
-- ubicar la fila en UPDATE/DELETE). Sin FK, sin UNIQUE, sin CHECK, sin seed: las restricciones
-- viven en el central, que es el publisher. Una restriccion aca que el publisher no comparta
-- solo agrega una forma de cortar la replicacion. El filial nunca escribe esta tabla.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.configuracion_facturacion (
    id                            BIGSERIAL PRIMARY KEY,
    sucursal_id                   BIGINT,
    modo                          VARCHAR(20),
    ventas_sin_factura            INTEGER,
    venta_ticket_respeta_politica BOOLEAN,
    usuario_id                    BIGINT,
    creado_en                     TIMESTAMP,
    modificado_en                 TIMESTAMP
);

COMMENT ON TABLE financiero.configuracion_facturacion IS
    'Politica de facturacion automatica. Llega por replicacion desde el central (MAIN_TO_ALL); el filial solo la lee. Sin filas = property facturaCountDown.';

COMMENT ON COLUMN financiero.configuracion_facturacion.sucursal_id IS
    'NULL = politica global; valor = override de esa sucursal.';

COMMENT ON COLUMN financiero.configuracion_facturacion.modo IS
    'TODAS, INTERVALO o A_PEDIDO. Un valor desconocido hace que el filial ignore la fila.';

COMMENT ON COLUMN financiero.configuracion_facturacion.ventas_sin_factura IS
    'Solo INTERVALO: ventas sin factura entre dos facturadas (misma semantica que facturaCountDown).';

COMMENT ON COLUMN financiero.configuracion_facturacion.venta_ticket_respeta_politica IS
    'false = Venta + Ticket y delivery facturan siempre (comportamiento historico); true = decide la politica.';
