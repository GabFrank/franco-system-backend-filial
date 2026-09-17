-- =====================================================================
-- venta_tarjeta: quien dejo un cobro sin conciliar, cuando y por que (lado PUBLISHER)
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- `NO_COMPLETADO` es terminal: ese cobro con tarjeta ya no se registra nunca, y su plata queda sin
-- cupon contra el cual conciliar la liquidacion del proveedor. La fila dice que eso paso y nada
-- mas --ni quien lo decidio, ni cuando, ni por que-- asi que no hay a quien preguntarle despues.
--
-- Estas columnas son la condicion para lo otro que cambia en esta entrega: que el CAJERO pueda
-- cerrar su caja dejando un cobro sin conciliar. Hasta hoy solo podia un ADMIN, justamente porque
-- el escape no dejaba rastro. Con rastro, la decision se puede revisar; sin rastro, seria un
-- agujero.
--
-- PUBLISHER: EL CHECK VA ACA
--
-- financiero.venta_tarjeta es BRANCH_TO_MAIN: este repo es el que ESCRIBE y central se suscribe.
-- Un CHECK aca valida lo que esta filial escribe, que es justamente donde sirve. En central no va
-- --ver V228.5 de ese repo-- porque un CHECK en el subscriber puede abortar el apply.
--
-- ⚠️ ESTA MIGRACION NO PUEDE MERGEARSE ANTES QUE LA DE CENTRAL (V228.5)
--
-- La fila de esta tabla en pg_publication_rel NO tiene column list, asi que PostgreSQL publica
-- cualquier columna nueva sin ningun ALTER PUBLICATION. Apenas esta filial aplique la migracion y
-- procese UN cobro, el stream hacia central incluye las cuatro columnas; si central no las tiene,
-- su apply worker se detiene con "missing replicated column" y entra en crash-loop cada 5 s con el
-- slot reteniendo WAL. Es el corte del 2026-08-20, y como develop -> alpha es automatico cada 15
-- minutos y sin aprobacion, llega solo.
--
-- SECUENCIA OBLIGATORIA:
--   1. central: V228.5
--   2. central: desplegado y confirmado
--   3. recien entonces: mergear esta
--
-- SIN BACKFILL
--
-- Los NO_COMPLETADO viejos quedan en NULL = no se sabe quien los marco, que es la verdad.
-- =====================================================================
ALTER TABLE financiero.venta_tarjeta
    ADD COLUMN IF NOT EXISTS no_completado_motivo VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS no_completado_observacion VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS no_completado_por_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS no_completado_en TIMESTAMP NULL;

-- NOT VALID y despues VALIDATE, en dos pasos: el ADD queda como metadata y no escanea la tabla
-- bajo AccessExclusiveLock, que en horario de atencion se encola detras de cualquier transaccion
-- larga y bloquea toda consulta nueva sobre los cobros con tarjeta. Aca ademas el escaneo es
-- trivial, porque la columna acaba de nacer y todo esta en NULL, que el CHECK acepta.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'venta_tarjeta_no_completado_motivo_check'
          AND conrelid = 'financiero.venta_tarjeta'::regclass
    ) THEN
        ALTER TABLE financiero.venta_tarjeta
            ADD CONSTRAINT venta_tarjeta_no_completado_motivo_check
            CHECK (no_completado_motivo IS NULL OR no_completado_motivo IN
                   ('CUPON_NO_IMPRESO', 'POS_FALLADO', 'CUPON_PERDIDO', 'OTRO')) NOT VALID;

        ALTER TABLE financiero.venta_tarjeta
            VALIDATE CONSTRAINT venta_tarjeta_no_completado_motivo_check;
    END IF;
END $$;

-- El usuario SI lleva FK en este lado: es el repo que escribe, y personas.usuario ya esta acá
-- --venta_tarjeta.usuario_id la tiene desde su creacion--, asi que no agrega una dependencia nueva.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_vt_no_completado_por'
          AND conrelid = 'financiero.venta_tarjeta'::regclass
    ) THEN
        ALTER TABLE financiero.venta_tarjeta
            ADD CONSTRAINT fk_vt_no_completado_por
            FOREIGN KEY (no_completado_por_id) REFERENCES personas.usuario(id) NOT VALID;

        ALTER TABLE financiero.venta_tarjeta
            VALIDATE CONSTRAINT fk_vt_no_completado_por;
    END IF;
END $$;

COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_motivo IS
    'Por que este cobro quedo sin conciliar: CUPON_NO_IMPRESO | POS_FALLADO | CUPON_PERDIDO | OTRO.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_observacion IS
    'Lo que el cajero escribio cuando el motivo es OTRO, o el detalle que quiso dejar.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_por_id IS
    'Usuario que decidio cerrar sin conciliar este cobro.';
COMMENT ON COLUMN financiero.venta_tarjeta.no_completado_en IS
    'Cuando se marco. Con no_completado_por_id es lo que permite revisar la decision despues.';
