-- Espejo en FILIAL de las columnas agregadas en CENTRAL por V216.5
-- (frc-comercial/central: V216.5__marcacion_metodo_y_similitud.sql).
--
-- La replicacion logica NO propaga DDL. `administrativo.marcacion` viaja en las dos
-- direcciones --sube por BRANCH_TO_MAIN y baja por `central_filial<N>_pub`--, y esas
-- publicaciones NO llevan lista de columnas: el publisher manda todas las columnas de
-- la tabla.
--
-- Consecuencia si esta migracion no corre en una filial: en cuanto alguien marque desde
-- el kiosco de la PWA o desde su telefono, central inserta la fila con metodo_registro
-- y la publica. El apply worker del filial no conoce la columna y muere:
--   ERROR: logical replication target relation "administrativo.marcacion"
--          is missing replicated column: "metodo_registro"
-- -> crash-loop, slot inactivo con WAL retenido creciendo, y la bajada central->filial
--    cortada por completo para esa sucursal. Es el mismo modo de falla del incidente
--    de V90.7 con el enum tipo_dispositivo, cambiando el enum por una columna.
--
-- Por eso VA ANTES que el deploy del central, no despues.
--
-- Las tres columnas son opcionales y la filial no las escribe: hoy quien identifica por
-- rostro es la PWA contra el central. Existen aca para poder recibir las filas.
--
-- Aditiva: ningun DROP ni RENAME.
--
-- IDEMPOTENTE / re-ejecutable: todo el script es ADD COLUMN IF NOT EXISTS + COMMENT ON,
-- asi que corriendo de nuevo sobre una base que ya lo tiene queda no-op y no falla. Eso
-- importa aca porque durante un corte de replicacion la columna se suele agregar a mano
-- en la filial afectada para levantar el apply worker antes de desplegar: cuando despues
-- corre Flyway, esta migracion no choca con el parche manual. Un solo ALTER TABLE para
-- las tres columnas: un unico lock sobre la tabla y las tres entran o no entra ninguna.
--
-- El guard con to_regclass cubre el caso de una filial vieja donde administrativo.marcacion
-- todavia no exista: se avisa por NOTICE en vez de abortar el deploy entero, porque sin
-- la tabla no hay replicacion de marcacion que romper.

DO $$
BEGIN
    IF to_regclass('administrativo.marcacion') IS NULL THEN
        RAISE NOTICE 'administrativo.marcacion no existe en esta base; se saltea el espejo de V216.5';
        RETURN;
    END IF;

    ALTER TABLE administrativo.marcacion
        ADD COLUMN IF NOT EXISTS metodo_registro          VARCHAR(30),
        ADD COLUMN IF NOT EXISTS similitud_facial         REAL,
        ADD COLUMN IF NOT EXISTS margen_segundo_candidato REAL;

    -- metodo_registro es VARCHAR y no un enum de Postgres a proposito, y es la leccion de
    -- V90.7: un tipo nuevo hay que crearlo en cada filial antes de que llegue la primera
    -- fila, y si una se saltea la migracion el worker entra en crash-loop. Un VARCHAR
    -- acepta cualquier label sin DDL previo.
    COMMENT ON COLUMN administrativo.marcacion.metodo_registro IS
        'MANUAL | FACIAL_1A1 | FACIAL_1AN_KIOSCO. Espejo de central V216.5.';
    COMMENT ON COLUMN administrativo.marcacion.similitud_facial IS
        'similitud del match facial, 0..1. Null si la marcacion fue manual. Espejo de central V216.5.';
    COMMENT ON COLUMN administrativo.marcacion.margen_segundo_candidato IS
        'similitud del mejor menos la del segundo. Dice si el 1:N fue solido o una moneda al aire.';
END $$;
