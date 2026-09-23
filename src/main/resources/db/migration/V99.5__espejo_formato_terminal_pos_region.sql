-- =====================================================================
-- formato_terminal_pos_region: el mapa que convierte cajas del OCR en campos
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- Hoy el OCR guarda el texto leido y nada mas: captura_cupon.texto_ocr se llena, `campos`
-- nunca. Ni `patron` ni `mapeo` del formato tienen un solo consumidor en este repo. O sea que
-- el cajero saca la foto, ve el texto en pantalla y lo transcribe a mano igual: el OCR es una
-- lupa, no un extractor.
--
-- Esta tabla es el mapa. Cada fila dice: en el formato F, el campo C se encuentra anclado a la
-- etiqueta E, en la posicion P respecto de ella, y tiene que ser del tipo T.
--
-- ANCLADO A LA ETIQUETA, NO A COORDENADAS ABSOLUTAS
--
-- La regla mas importante del diseno, y la que es facil perder al implementar. Un mapa por
-- coordenadas se rompe el dia que el proveedor agrega una linea al ticket --y se rompen TODOS
-- los mapas de ese modelo a la vez, sin que nadie entienda por que--. Anclado a la etiqueta,
-- sobrevive: si "AUT:" se corrio 20px para abajo, el valor sigue estando a su derecha.
--
-- La geometria (x1..y2, normalizada 0..1) esta igual, pero como PISTA para acotar el
-- reconocimiento, no como verdad para asignar. Es el hibrido de §2.2: la geometria achica el
-- trabajo, la etiqueta decide de quien es cada caja.
--
-- EL MAPA ES UN INTERPRETE, NO UNA TIJERA
--
-- Una sola pasada de OCR produce cajas con texto, coordenadas y confianza; el mapa ASIGNA cada
-- caja a un campo. No se recorta campo por campo: eso serian N inferencias en vez de una, y el
-- tiempo escalaria con la cantidad de campos.
--
-- CUELGAN DEL FORMATO, NO DE LA TERMINAL
--
-- El doc de dominio decia "por POS" porque se escribio antes de la etapa 3. Ahora que el
-- formato es del modelo de aparato, dos cajas con la misma maquinita comparten el mapa en vez
-- de dibujarlo dos veces.
--
-- ORDEN DE DESPLIEGUE
--
-- Tabla nueva MAIN_TO_ALL: central publica, la filial se suscribe. Esta va ANTES que la de
-- central (V225.5), como todos los espejos. Si se invierte el orden el costo es acotado --el
-- REFRESH PUBLICATION falla entero y no toca la suscripcion, medido el 2026-09-11-- pero no
-- hay motivo para invertirlo.
--
-- ESTE ES EL LADO SUBSCRIBER
--
-- Solo la PK. Sin NOT NULL, sin CHECK, sin FK, sin unique: esta filial no escribe esta tabla.
-- Un CHECK aca que central no tenga es una forma de cortar la replicacion, no una proteccion.
-- =====================================================================
CREATE TABLE IF NOT EXISTS financiero.formato_terminal_pos_region (
    id                        BIGINT        NOT NULL,
    formato_terminal_pos_id   BIGINT        NULL,
    -- Destino canonico (MONTO, CODIGO_AUTORIZACION, NUMERO_BOLETA, TERMINAL) o una clave libre,
    -- en cuyo caso el valor cae en venta_tarjeta.datos_extra.
    campo                     VARCHAR(40)   NULL,
    -- La etiqueta impresa que ancla la region: "AUT:", "MONTO", "TERMINAL". Es lo que se busca
    -- en el texto ya reconocido.
    etiqueta                  VARCHAR(120)  NULL,
    -- Donde esta el valor respecto de la etiqueta: DERECHA | ABAJO | DENTRO.
    posicion                  VARCHAR(20)   NULL,
    -- TEXTO | NUMERO | FECHA. Un campo declarado NUMERO rechaza un "0i64" del OCR gratis.
    tipo                      VARCHAR(20)   NULL,
    obligatorio               BOOLEAN       NULL DEFAULT false,
    -- Pista geometrica normalizada 0..1, para acotar el reconocimiento. NULL = sin pista, se
    -- resuelve solo por etiqueta.
    x1                        NUMERIC(6,5)  NULL,
    y1                        NUMERIC(6,5)  NULL,
    x2                        NUMERIC(6,5)  NULL,
    y2                        NUMERIC(6,5)  NULL,
    -- Como se creo: DERIVADA (del cupon de muestra) o MANUAL. Decide si la derivacion la puede
    -- pisar sin preguntar.
    origen                    VARCHAR(20)   NULL,
    orden                     INTEGER       NULL DEFAULT 0,
    creado_en                 TIMESTAMP     NULL DEFAULT NOW(),
    CONSTRAINT formato_terminal_pos_region_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_formato_terminal_pos_region_formato
    ON financiero.formato_terminal_pos_region (formato_terminal_pos_id);

COMMENT ON TABLE financiero.formato_terminal_pos_region IS
    'Espejo de solo lectura. El ABM vive en central; aca solo se lee para asignar las cajas del OCR a campos.';
