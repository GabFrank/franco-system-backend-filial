-- =============================================================================
-- V79.3 - Alinear la estructura de la filial (general6) con el servidor central
--
-- MOTIVO
-- La suscripcion logica `bodega_filial24_central_sub` (central_pub, MAIN_TO_ALL)
-- estaba en bucle de error infinito:
--
--   ERROR: a la relacion destino de replicacion logica «vehiculos.vehiculo» le
--          faltan las columnas replicadas: «ultima_latitud», «ultima_longitud»,
--          «ultima_fecha_reporte», «ignicion_actual», «km_virtual»,
--          «valor_estimado_pyg», «valor_estimado_brl»
--
-- El apply worker moria y reiniciaba cada 5 segundos (10.285 errores
-- acumulados), el slot quedaba inactivo reteniendo WAL en el central y TODO lo
-- publicado detras de esa transaccion quedaba bloqueado, no solo vehiculos.
--
-- Causa raiz: los repos central y filial evolucionaron el schema por separado.
-- El central aplico V121.3 (telemetria + valores multimoneda, y DROP de columnas
-- viejas) y la filial nunca recibio el equivalente.
--
-- ALCANCE
-- Esta migracion deja a general6 con la estructura del central para las tablas
-- publicadas en `central_pub`:
--   1. Columnas faltantes (esto es lo que desbloquea la replicacion)
--   2. FK mal apuntada en vehiculo (segundo bloqueo, ver seccion 2)
--   3. Columnas sobrantes sin uso ni datos
--   4. Tipos mas angostos que el central (misma clase de bug, a futuro)
--
-- Todo es idempotente: se puede correr mas de una vez sin efecto.
-- =============================================================================


-- =============================================================================
-- 1. vehiculos.vehiculo - columnas faltantes
--    Origen: central V121.3. Tipos y defaults copiados exactos de bodega3.
-- =============================================================================
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_latitud       double precision;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_longitud      double precision;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ultima_fecha_reporte timestamp without time zone;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS ignicion_actual      boolean DEFAULT false;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS km_virtual           numeric(19,2) DEFAULT 0;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_pyg   numeric;
ALTER TABLE vehiculos.vehiculo ADD COLUMN IF NOT EXISTS valor_estimado_brl   numeric;


-- =============================================================================
-- 2. vehiculos.vehiculo - FKs
--
--    SEGUNDO BLOQUEO: en la filial `vehiculo_tipo_vehiculo_fkey` apuntaba a
--    vehiculos.vehiculo(id) en vez de vehiculos.tipo_vehiculo(id). La fila que
--    viene del central trae tipo_vehiculo=1 y la filial tiene 0 vehiculos, asi
--    que la FK habria rechazado el INSERT aun con las columnas ya creadas.
--    En el central la FK apunta correctamente a tipo_vehiculo(id).
--
--    Tambien se elimina `vehiculo_usuario_id_fkey`: no existe en el central y
--    en un suscriptor logico una FK contra personas.usuario puede rechazar el
--    apply si el usuario todavia no fue replicado.
--
--    El central solo tiene: vehiculo_modelo_fk y vehiculo_tipo_vehiculo_fkey.
-- =============================================================================
ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_tipo_vehiculo_fkey;
ALTER TABLE vehiculos.vehiculo
    ADD CONSTRAINT vehiculo_tipo_vehiculo_fkey
    FOREIGN KEY (tipo_vehiculo) REFERENCES vehiculos.tipo_vehiculo(id);

ALTER TABLE vehiculos.vehiculo DROP CONSTRAINT IF EXISTS vehiculo_usuario_id_fkey;


-- =============================================================================
-- 3. vehiculos.vehiculo - columnas sobrantes
--    El central las dropeo en V121.3 (la info paso a las tablas hijas
--    vehiculo_especificaciones / vehiculo_finanzas / vehiculo_adjuntos).
--    En general6 estas columnas tienen 0 filas con datos (la tabla esta vacia).
--    Ninguna se usa: Vehiculo.java en la filial no tiene repository, service ni
--    resolver GraphQL, y el frontend consulta vehiculos SIEMPRE contra el
--    central (EnteService.abrirBuscadorEnte -> isServidor: true).
--
--    tipo_combustible_id arrastra su FK vehiculo_tipo_combustible_id_fkey, que
--    cae junto con la columna. La tabla vehiculos.tipo_combustible se mantiene
--    porque tambien existe en el central.
-- =============================================================================
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS primer_kilometraje;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS imagenes_documentos;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS imagenes_vehiculo;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS capacidad_kg;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS capacidad_pasajeros;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS tipo_combustible_id;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS chasis;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS aire_acondicionado;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS mantenimiento_motor_intervalo;
ALTER TABLE vehiculos.vehiculo DROP COLUMN IF EXISTS mantenimiento_caja_intervalo;


-- =============================================================================
-- 4. equipos.equipo - columnas faltantes
--
--    Sin FKs a proposito:
--      - modelo_id referenciaria equipos.modelo, tabla que NO existe en general6
--        (la filial solo tiene equipo, equipo_sucursal y tipo_equipo).
--      - propietario_id / sucursal_id: una FK en un suscriptor logico puede
--        rechazar el apply si la fila padre todavia no fue replicada.
--    El modulo Equipo no existe en el backend filial (cero clases Java), asi que
--    estas columnas son puramente para que la replicacion pueda aplicar.
-- =============================================================================
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS sucursal_id     bigint DEFAULT 0;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS propietario_id  bigint;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS identificador   character varying(100);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS consume_energia boolean;
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS consumo_valor   character varying(50);
ALTER TABLE equipos.equipo ADD COLUMN IF NOT EXISTS modelo_id       bigint;


-- =============================================================================
-- 5. equipos.equipo - columnas sobrantes
--    En el central `marca` y `modelo` pasaron a ser modelo_id (FK), y `costo`
--    se movio al modulo financiero. En general6 la tabla esta vacia (0 filas).
-- =============================================================================
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS marca;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS modelo;
ALTER TABLE equipos.equipo DROP COLUMN IF EXISTS costo;


-- =============================================================================
-- 6. Resto de tablas de central_pub con columnas faltantes
--    Todavia no habian roto la replicacion solo porque nadie inserto/actualizo
--    esas filas en el central desde que aparecio el drift. Mismo modo de falla.
--    Sin FK a empresarial.sucursal por la misma razon de la seccion 4.
-- =============================================================================
ALTER TABLE empresarial.configuracion_general ADD COLUMN IF NOT EXISTS sucursal_id    bigint DEFAULT 0 NOT NULL;
ALTER TABLE financiero.cuenta_bancaria        ADD COLUMN IF NOT EXISTS sucursal_id    bigint DEFAULT 0;
ALTER TABLE financiero.documento              ADD COLUMN IF NOT EXISTS sucursal_id    bigint DEFAULT 0;
ALTER TABLE general.ciudad                    ADD COLUMN IF NOT EXISTS sucursal_id    bigint DEFAULT 0;
ALTER TABLE productos.codigo_tipo_precio      ADD COLUMN IF NOT EXISTS sucursal_id    bigint DEFAULT 0;
ALTER TABLE general.contacto                  ADD COLUMN IF NOT EXISTS redes_sociales character varying;


-- =============================================================================
-- 7. Columnas sobrantes sin uso ni datos
--    `id_central` no esta mapeada en Cargo.java ni en Cambio.java y no tiene
--    ningun valor cargado (cargo: 0 filas; cambio: 200 filas, todas NULL).
--    Los indices unicos cargo_un_id_central / cambio_un_id_central caen junto
--    con la columna.
-- =============================================================================
ALTER TABLE empresarial.cargo  DROP COLUMN IF EXISTS id_central;
ALTER TABLE financiero.cambio  DROP COLUMN IF EXISTS id_central;


-- =============================================================================
-- 8. Tipos mas angostos que el central
--    Un varchar mas corto en el suscriptor rompe el apply igual que una columna
--    faltante, pero recien cuando llega un valor que no entra. Ampliar es no
--    destructivo y no reescribe la tabla.
-- =============================================================================
ALTER TABLE financiero.timbrado ALTER COLUMN cod_actividad_economica_principal TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN domicilio_fiscal_barrio           TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN domicilio_fiscal_ciudad           TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN domicilio_fiscal_codigo_ciudad    TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN domicilio_fiscal_departamento     TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN domicilio_fiscal_localidad        TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN telefono                          TYPE character varying(255);
ALTER TABLE financiero.timbrado ALTER COLUMN tipo_sociedad                     TYPE character varying(255);

ALTER TABLE financiero.timbrado_detalle ALTER COLUMN barrio         TYPE character varying(255);
ALTER TABLE financiero.timbrado_detalle ALTER COLUMN ciudad         TYPE character varying(255);
ALTER TABLE financiero.timbrado_detalle ALTER COLUMN codigo_ciudad  TYPE character varying(255);
ALTER TABLE financiero.timbrado_detalle ALTER COLUMN departamento   TYPE character varying(255);
ALTER TABLE financiero.timbrado_detalle ALTER COLUMN localidad      TYPE character varying(255);
ALTER TABLE financiero.timbrado_detalle ALTER COLUMN telefono       TYPE character varying(255);

ALTER TABLE financiero.documento_electronico ALTER COLUMN numero_documento        TYPE character varying(50);
ALTER TABLE financiero.documento_electronico ALTER COLUMN mensaje_respuesta_sifen TYPE text;

ALTER TABLE financiero.evento_nominacion_de  ALTER COLUMN total_factura           TYPE numeric;


-- =============================================================================
-- 9. NO INCLUIDO A PROPOSITO - requiere decision explicita
--
-- 9.a) Columnas sobrantes QUE SI TIENEN DATOS.
--      No se dropean aca porque el borrado es irreversible. Ninguna de las dos
--      esta mapeada en su entidad JPA (Timbrado.java y PrecioDelivery.java no
--      las declaran), pero la data existe y timbrado.sucursal_id ademas tiene
--      FK contra empresarial.sucursal.
--
--        financiero.timbrado.sucursal_id          -> 4 filas, 4 con dato
--        operaciones.precio_delivery.sucursal_id  -> 6 filas, 6 con dato
--
--      Si se confirma que son descartables, descomentar:
--
--      ALTER TABLE financiero.timbrado         DROP COLUMN IF EXISTS sucursal_id;
--      ALTER TABLE operaciones.precio_delivery DROP COLUMN IF EXISTS sucursal_id;
--
-- 9.b) timestamp without time zone -> timestamp with time zone.
--      El central usa timestamptz y la filial timestamp en creado_en /
--      actualizado_en / fecha_* de documento_electronico, evento_nominacion_de,
--      evento_cancelacion_de y lote_de. La replicacion convierte, asi que no
--      rompe, pero los valores ya guardados se reinterpretarian segun el
--      TimeZone de la sesion al hacer el ALTER. Es un cambio de semantica sobre
--      datos SIFEN en produccion y va en su propia migracion.
--
-- 9.c) Columnas donde la filial es MAS ANCHA que el central
--      (documento_electronico.cdc 50 vs 44, lote_de.protocolo 255 vs 50,
--      configuraciones.local.* sin limite vs 255). No rompen la replicacion y
--      angostarlas podria truncar datos, asi que se dejan como estan.
-- =============================================================================
