-- =====================================================================
-- captura_cupon: de que terminal es la foto
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La captura sabe de que caja y de que sucursal es, pero no de que aparato. Y sin el aparato no
-- hay formato; sin formato no hay patron ni mapeo; sin eso el OCR guarda texto crudo y el
-- cajero lo transcribe a mano igual.
--
-- Es lo que faltaba para cerrar el camino de la camara: la foto entra, sale con los campos ya
-- separados.
--
-- POR QUE NULLABLE
--
-- Una captura abierta por un desktop viejo --que todavia no manda la terminal-- tiene que
-- seguir funcionando: guarda el texto leido y no extrae campos, que es exactamente lo que hace
-- hoy. Degradar, no romper.
--
-- SIN FK, A PROPOSITO
--
-- financiero.terminal_pos es un espejo que baja por replicacion. Una FK convertiria un
-- desfasaje de segundos entre streams --la captura creada antes de que la terminal termine de
-- bajar-- en un error que voltea la subida de la foto. Mismo criterio que
-- terminal_pos.formato_terminal_pos_id (V95.5).
--
-- ORDEN DE DESPLIEGUE
--
-- Ninguno: financiero.captura_cupon es LOCAL del filial y no se replica (ver V94.5). No
-- aparece en pg_publication_rel ni en configuraciones.replication_table. Esta migracion no
-- tiene espejo en central y no puede cortar nada.
-- =====================================================================
ALTER TABLE financiero.captura_cupon
    ADD COLUMN IF NOT EXISTS terminal_pos_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_captura_cupon_terminal
    ON financiero.captura_cupon (terminal_pos_id);

COMMENT ON COLUMN financiero.captura_cupon.terminal_pos_id IS
    'De que aparato es la foto. De aca sale el formato, y del formato el patron y el mapeo con los que se extraen los campos. NULL = captura abierta por un cliente viejo: se guarda el texto y no se extrae nada.';
