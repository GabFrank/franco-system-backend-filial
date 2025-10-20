-- ============================================================
-- Flyway Migration Script - CENTRAL
-- Descripción: Agrega tablas de documento electrónico a publicaciones de filiales
-- Fecha: 2025-10-16
-- Versión: V1
-- ============================================================
-- Este script:
-- 1. Busca dinámicamente todas las publicaciones con formato: central_filial{{id}}_pub
-- 2. Agrega las tablas de documento electrónico a cada publicación encontrada
-- 3. NO aplica filtros WHERE (todas las filas de cada tabla)
-- ============================================================

DO $$
DECLARE
    v_publication RECORD;
    v_total INTEGER := 0;
    v_success INTEGER := 0;
    v_failed INTEGER := 0;
    v_sql TEXT;
BEGIN
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Iniciando actualización de publicaciones...';
    RAISE NOTICE '============================================================';
    
    -- Iterar sobre todas las publicaciones que sigan el patrón central_filial{{id}}_pub
    FOR v_publication IN 
        SELECT pubname 
        FROM pg_publication 
        WHERE pubname LIKE 'central_filial%_pub'
        ORDER BY pubname
    LOOP
        v_total := v_total + 1;
        
        RAISE NOTICE 'Procesando: %', v_publication.pubname;
        
        BEGIN
            -- Construir el comando ALTER PUBLICATION
            v_sql := format(
                'ALTER PUBLICATION %I ADD TABLE 
                    financiero.documento_electronico,
                    financiero.evento_cancelacion_de,
                    financiero.evento_nominacion_de,
                    financiero.lote_de,
                    financiero.timbrado_detalle',
                v_publication.pubname
            );
            
            -- Ejecutar el comando
            EXECUTE v_sql;
            v_success := v_success + 1;
            RAISE NOTICE '✓ Tablas agregadas exitosamente a: %', v_publication.pubname;
            
        EXCEPTION WHEN OTHERS THEN
            v_failed := v_failed + 1;
            RAISE WARNING '✗ Error al actualizar %: %', v_publication.pubname, SQLERRM;
        END;
        
    END LOOP;
    
    -- Resumen del proceso
    RAISE NOTICE '============================================================';
    IF v_total = 0 THEN
        RAISE WARNING 'No se encontraron publicaciones con formato central_filial{{id}}_pub';
        RAISE WARNING 'Verifica que estés ejecutando este script en el servidor CENTRAL';
    ELSE
        RAISE NOTICE 'Proceso completado:';
        RAISE NOTICE '  Total publicaciones encontradas: %', v_total;
        RAISE NOTICE '  Actualizadas exitosamente: %', v_success;
        RAISE NOTICE '  Fallidas: %', v_failed;
    END IF;
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'IMPORTANTE: Debes refrescar las suscripciones en cada filial';
    RAISE NOTICE 'Ejecuta la migración V2 en cada servidor de filial';
    RAISE NOTICE '============================================================';
    
END $$;

-- ============================================================
-- VERIFICACIÓN
-- ============================================================
-- Ver las tablas agregadas a cada publicación
SELECT 
    pubname AS publicacion,
    schemaname AS esquema,
    tablename AS tabla
FROM pg_publication_tables 
WHERE pubname LIKE 'central_filial%_pub' 
  AND tablename IN ('documento_electronico', 'evento_cancelacion_de', 'evento_nominacion_de', 'lote_de', 'timbrado_detalle')
ORDER BY pubname, tablename;

-- Resumen por publicación
SELECT 
    pubname AS publicacion,
    COUNT(*) AS tablas_documento_electronico
FROM pg_publication_tables 
WHERE pubname LIKE 'central_filial%_pub' 
  AND tablename IN ('documento_electronico', 'evento_cancelacion_de', 'evento_nominacion_de', 'lote_de', 'timbrado_detalle')
GROUP BY pubname
ORDER BY pubname;

