-- =====================================================================
-- captura_cupon: la sesion de foto entre el desktop y el telefono
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Cuando el POS no imprime QR --o el cupon salio borroso, o el papel esta arrugado-- el cajero
-- le saca una foto con su telefono. El desktop muestra un QR, el telefono lo escanea, abre una
-- pagina que sirve este mismo filial y sube la imagen. Esta tabla es lo que une esas dos
-- puntas.
--
-- EL TOKEN ES LA UNICA CREDENCIAL
--
-- El telefono no tiene login ni rol: el QR solo lo puede mostrar un desktop que ya paso las
-- puertas (caja abierta, rol de venta, flujo habilitado), asi que la autorizacion ocurrio
-- antes. Por eso el token es de UN SOLO USO, expira en minutos y esta atado a una caja: si
-- alguien fotografia el QR de la pantalla, no le sirve despues ni para otra venta.
--
-- `usado_en` es lo que hace el uso unico, y el indice parcial de expiracion es para el job de
-- purga.
--
-- NO SE REPLICA
--
-- Esta tabla es LOCAL del filial y no va a ninguna publicacion. Es estado efimero de minutos
-- entre dos maquinas de la misma sucursal: no le sirve al central, y mandarlo a replicar seria
-- trafico y WAL por nada. Al agregar tablas nuevas revisar que esta quede afuera --las
-- publicaciones NO son FOR ALL TABLES, asi que quedarse afuera es el default, pero conviene
-- que este dicho.
--
-- Aditiva: tabla nueva, nada que romper si hay rollback al JAR anterior --que simplemente la
-- ignora.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.captura_cupon (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(64)  NOT NULL,
    sucursal_id BIGINT       NOT NULL,
    caja_id     BIGINT       NOT NULL,
    usuario_id  BIGINT,
    estado      VARCHAR(20)  NOT NULL DEFAULT 'ESPERANDO',
    expira_en   TIMESTAMP    NOT NULL,
    usado_en    TIMESTAMP,
    imagen_url  VARCHAR(500),
    texto_ocr   TEXT,
    campos      JSONB,
    ms_ocr      INTEGER,
    intentos    INTEGER      NOT NULL DEFAULT 0,
    nitidez     NUMERIC(10,2),
    error       VARCHAR(500),
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_captura_cupon_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_captura_cupon_caja
    ON financiero.captura_cupon (caja_id, sucursal_id, creado_en DESC);

-- para el job de purga: solo interesan las que siguen sin usarse
CREATE INDEX IF NOT EXISTS idx_captura_cupon_pendientes
    ON financiero.captura_cupon (expira_en)
    WHERE usado_en IS NULL;

COMMENT ON TABLE  financiero.captura_cupon IS
    'Sesion de captura de foto de cupon entre el desktop y el telefono del cajero. Estado efimero, LOCAL del filial: no se replica al central.';
COMMENT ON COLUMN financiero.captura_cupon.token IS
    'Unica credencial del telefono. Un solo uso, expira en minutos, atada a una caja.';
COMMENT ON COLUMN financiero.captura_cupon.intentos IS
    'Fotos subidas para este token. El token solo se consume con un resultado bueno, asi que una foto movida o un fallo del motor se pueden reintentar sin volver a la caja.';
COMMENT ON COLUMN financiero.captura_cupon.nitidez IS
    'Varianza del laplaciano que midio el telefono. Se guarda para poder ajustar el umbral con datos reales en vez de dejarlo como numero magico.';
COMMENT ON COLUMN financiero.captura_cupon.campos IS
    'Campos extraidos por el OCR, ya mapeados por el formato del proveedor.';
