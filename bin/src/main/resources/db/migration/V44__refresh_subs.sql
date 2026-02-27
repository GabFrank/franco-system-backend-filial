-- ============================================================
-- Flyway Migration Script - FILIAL
-- Descripción: Refresca todas las suscripciones para sincronizar nuevas tablas
-- Fecha: 2025-10-16
-- Versión: V2
-- ============================================================
-- IMPORTANTE: Este script debe ejecutarse en CADA servidor de FILIAL
-- ============================================================

DO $$
DECLARE
    v_subscription RECORD;
    v_total INTEGER := 0;
    v_success INTEGER := 0;
    v_failed INTEGER := 0;
BEGIN
    RAISE NOTICE 'Iniciando refresco de suscripciones...';
    
    -- Iterar sobre TODAS las suscripciones
    FOR v_subscription IN 
        SELECT subname 
        FROM pg_subscription 
        ORDER BY subname
    LOOP
        v_total := v_total + 1;
        
        BEGIN
            -- Refrescar la suscripción
            EXECUTE format('ALTER SUBSCRIPTION %I REFRESH PUBLICATION', v_subscription.subname);
            v_success := v_success + 1;
            RAISE NOTICE '✓ Suscripción refrescada: %', v_subscription.subname;
            
        EXCEPTION WHEN OTHERS THEN
            v_failed := v_failed + 1;
            RAISE WARNING '✗ Error al refrescar %: %', v_subscription.subname, SQLERRM;
        END;
        
    END LOOP;
    
    -- Resumen del proceso
    IF v_total = 0 THEN
        RAISE WARNING 'No se encontraron suscripciones. Verifica que estés en un servidor de FILIAL.';
    ELSE
        RAISE NOTICE '============================================================';
        RAISE NOTICE 'Proceso completado:';
        RAISE NOTICE '  Total suscripciones: %', v_total;
        RAISE NOTICE '  Exitosas: %', v_success;
        RAISE NOTICE '  Fallidas: %', v_failed;
        RAISE NOTICE '============================================================';
    END IF;
    
END $$;

