-- =====================================================================
-- configuracion_venta_tarjeta: el modulo deja de tener una sola perilla
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La tabla tiene una fila y un campo util: habilitado. Todo lo demas que decide comportamiento
-- esta clavado en el codigo, y algunos numeros ya son inconsistentes entre si --el countdown del
-- dialogo de registro son 120 s en dos archivos del desktop y 60 s en un tercero.
--
-- El campo que mas importa es registro_obligatorio. Hoy "Registrar mas tarde" deja la venta en
-- PENDIENTE y NADA la persigue: el cierre de caja no mira ventas con tarjeta pendientes. La
-- razon de ser del modulo depende de que el cajero se acuerde.
--
-- POR QUE LA TOLERANCIA ES UN PORCENTAJE Y NO UN MONTO
--
-- monto y monto_escaneado se guardan SIN unidad. Un cupon de 8.000 R$ contra un cobro de 8.000
-- Gs da diferencia CERO en cualquier reporte de conciliacion, y son ~5900x --esta documentado en
-- VentaTarjetaService.validarMoneda y se verifico en la prueba manual del 2026-09-04. Un umbral
-- absoluto heredaria exactamente ese problema; un porcentaje es agnostico de moneda.
--
-- LAS DOS COLUMNAS DE RETENCION ENTRAN AHORA, EL JOB QUE LAS LEE NO
--
-- dias_retencion_imagenes y mb_libres_minimos quedan sin lector hasta la etapa 6. No es
-- descuido: es mas barato agregarlas en la misma migracion que ya toca esta tabla replicada que
-- coordinar un segundo despliegue filial-primero sobre 24 filiales mas adelante. Y el riesgo que
-- cubren es real: las imagenes, releases/ y los datos de PostgreSQL comparten disco, y un disco
-- lleno no solo rompe el guardado de fotos --impide que Postgres escriba WAL y tumba todas las
-- ventas de esa sucursal.
--
-- QUEDA GLOBAL, SIN sucursal_id
--
-- Decidido el 2026-09-10 sabiendo el costo: si mas adelante una farmacia y una bodega necesitan
-- retenciones distintas, abrirlo por sucursal va a exigir backfill sobre valores ya cargados.
-- Se acepto a cambio de no arrastrar una dimension que hoy nadie pide.
--
-- ⚠️ ORDEN DE DESPLIEGUE: financiero.configuracion_venta_tarjeta es MAIN_TO_ALL (V150.1 del
-- central). El filial es SUBSCRIBER: esta migracion va ANTES que la de central en toda la flota,
-- o el apply worker se detiene con "missing replicated columns".
--
-- Por eso mismo, ningun CHECK y ningun NOT NULL nuevo: en el subscriber, una restriccion que el
-- publisher no comparte convierte un valor legitimo en un corte de replicacion. Los defaults SI
-- van, para que un filial que todavia no recibio la fila replicada se comporte como hoy.
--
-- Todos los defaults reproducen el comportamiento actual: la migracion no cambia nada hasta que
-- alguien toque el ABM.
-- =====================================================================

-- LIBRE | AVISA_AL_CERRAR | BLOQUEA_EL_CIERRE.
-- LIBRE es lo de hoy. Migrar y que de golpe el cierre de caja empiece a bloquear seria un cambio
-- de comportamiento silencioso en toda la flota; que lo prenda quien lo quiera.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS registro_obligatorio VARCHAR(20) DEFAULT 'LIBRE';

-- 0 = confirmar siempre, que es lo de hoy.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS tolerancia_diferencia_monto_pct NUMERIC(5,2) DEFAULT 0;

-- Vida del token del QR de captura. Reemplaza CapturaCuponService.MINUTOS_VALIDEZ.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS minutos_validez_captura INTEGER DEFAULT 10;

-- Countdown del dialogo de registro. Reemplaza los 120 clavados en dos archivos del desktop.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS segundos_dialogo_registro INTEGER DEFAULT 120;

-- Cuanto atras mira el chequeo de cupon duplicado por codigo de autorizacion.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS horas_ventana_duplicado INTEGER DEFAULT 24;

-- NULL = no purgar. Sin lector hasta la etapa 6.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS dias_retencion_imagenes INTEGER NULL;

-- NULL = sin alerta. Sin lector hasta la etapa 6.
ALTER TABLE financiero.configuracion_venta_tarjeta
    ADD COLUMN IF NOT EXISTS mb_libres_minimos INTEGER NULL;

COMMENT ON COLUMN financiero.configuracion_venta_tarjeta.registro_obligatorio IS
    'LIBRE | AVISA_AL_CERRAR | BLOQUEA_EL_CIERRE. Que pasa al cerrar la caja con ventas con tarjeta en PENDIENTE.';
COMMENT ON COLUMN financiero.configuracion_venta_tarjeta.tolerancia_diferencia_monto_pct IS
    'Porcentaje por debajo del cual la diferencia entre el cupon y el cobro no pide confirmacion. Porcentaje y no monto porque monto/monto_escaneado se guardan sin unidad.';
