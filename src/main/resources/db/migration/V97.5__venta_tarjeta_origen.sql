-- =====================================================================
-- venta_tarjeta.origen: de donde salieron estos datos
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- La tabla tiene los campos del cupon y ninguna columna que diga COMO se obtuvieron. No todos
-- los origenes merecen la misma confianza: un codigo leido por OCR puede tener un caracter mal
-- --el `Cargo: 002511` leido `802511` es el error mas grande del modulo y lo fallan los dos
-- motores--, uno tipeado por un cajero puede tener cualquier cosa, y uno que viene de la API del
-- proveedor no puede estar mal.
--
-- Sin esto, la conciliacion no puede responder la unica pregunta que importa: cuales de estas
-- filas necesitan que las mire una persona.
--
-- UNA COLUMNA POR FILA, CON EL ORIGEN DOMINANTE
--
-- No un origen por campo. Si el cajero corrige a mano un campo que el OCR leyo mal, la fila es
-- MANUAL: lo que se quiere saber es si hubo intervencion humana, no la genealogia de cada dato.
-- El detalle fino, si algun dia hace falta, cabe en datos_extra.
--
-- POR QUE VARCHAR Y NO UN ENUM DE POSTGRESQL
--
-- financiero.venta_tarjeta.estado ya es VARCHAR(20) mapeado a String en Java: es la convencion
-- de este modulo. Un enum de PG habria exigido un ALTER TYPE coordinado en las 24 filiales cada
-- vez que aparezca un origen nuevo, y ALTER TYPE ... ADD VALUE no se puede usar en la misma
-- transaccion que lo agrega. El CHECK da la misma proteccion sin ese costo.
--
-- El CHECK SI va aca, y es la diferencia con V95.5: en financiero.venta_tarjeta el filial es el
-- PUBLISHER (BRANCH_TO_MAIN), no el subscriber. Un CHECK en el publisher valida lo que este
-- filial escribe; un CHECK en un subscriber puede abortar el apply de una fila que el otro lado
-- considera valida. Regla: CHECK solo donde el repo es el que escribe.
--
-- ⚠️⚠️ ESTA MIGRACION NO PUEDE MERGEARSE ANTES QUE LA DE CENTRAL. VIVE EN SU PROPIA RAMA.
--
-- financiero.venta_tarjeta es BRANCH_TO_MAIN: la filial PUBLICA y el central se suscribe. Y la
-- fila de esta tabla en pg_publication_rel NO tiene column list --verificado: `prattrs IS NULL`--,
-- asi que PostgreSQL publica cualquier columna nueva automaticamente, sin que haga falta ningun
-- ALTER PUBLICATION.
--
-- Consecuencia exacta: apenas una filial aplique esta migracion y procese UNA SOLA venta con
-- tarjeta, el stream hacia central incluye `origen`. Si central no tiene la columna, su apply
-- worker se detiene con "missing replicated column" y entra en crash-loop cada 5 s con el slot
-- reteniendo WAL. Es el corte del 2026-08-20, y como develop→alpha es automatico cada 15 minutos
-- y sin aprobacion, llega a las 24 filiales sin que nadie apriete nada.
--
-- Al 2026-09-10 la columna NO existe en central: verificado contra la base y grepeando todas las
-- ramas remotas de ese repo.
--
-- POR ESO ESTA MIGRACION VA EN UNA RAMA APARTE. No es una preferencia de organizacion: las tres
-- migraciones de la etapa 3 en el mismo JAR se aplican JUNTAS --Flyway corre todas las
-- pendientes-- y entonces no hay forma de desplegar "V95.5 y V96.5 ahora, esta despues". Es la
-- direccion CONTRARIA a la de las otras dos, y el plan siempre dijo que la etapa 3 son DOS pasos
-- de despliegue, no uno.
--
-- SECUENCIA OBLIGATORIA:
--   1. central: migracion que agrega venta_tarjeta.origen
--   2. central: desplegar (Deploy manual a la instancia que corresponda) y confirmar la version
--   3. recien entonces: mergear esta rama del filial
--
-- SIN BACKFILL
--
-- Nace nullable y las filas viejas quedan en NULL = historico desconocido. Convertir una
-- suposicion en dato es peor que dejar el hueco visible: si mañana alguien audita, NULL le dice
-- "no se sabe" y un 'QR' inventado le miente.
-- =====================================================================
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS origen VARCHAR(20) NULL;

-- NOT VALID y despues VALIDATE, en dos pasos.
--
-- `ADD CONSTRAINT ... CHECK` a secas toma AccessExclusiveLock y valida contra TODAS las filas bajo
-- ese lock. financiero.venta_tarjeta es la tabla de cobros con tarjeta de un local en horario de
-- atencion, y el lock ademas se encola detras de cualquier transaccion larga que ya la tenga
-- tomada, bloqueando mientras espera toda consulta nueva sobre ella.
--
-- Con NOT VALID el ADD es metadata: el CHECK empieza a regir para lo que se escriba de ahora en
-- mas, sin escanear nada. El VALIDATE posterior toma solo ShareUpdateExclusiveLock, que no bloquea
-- lecturas ni escrituras. Y aca el escaneo es trivial de todos modos, porque la columna acaba de
-- nacer y todas las filas estan en NULL, que el CHECK acepta.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'venta_tarjeta_origen_check'
          AND conrelid = 'financiero.venta_tarjeta'::regclass
    ) THEN
        ALTER TABLE financiero.venta_tarjeta
            ADD CONSTRAINT venta_tarjeta_origen_check
            CHECK (origen IS NULL OR origen IN ('QR', 'OCR', 'MANUAL', 'API')) NOT VALID;

        ALTER TABLE financiero.venta_tarjeta
            VALIDATE CONSTRAINT venta_tarjeta_origen_check;
    END IF;
END $$;

COMMENT ON COLUMN financiero.venta_tarjeta.origen IS
    'QR | OCR | MANUAL | API. Origen dominante de los datos del cupon; NULL = anterior a esta columna. Responde si la fila necesita revision humana.';
