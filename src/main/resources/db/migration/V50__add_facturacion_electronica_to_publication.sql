-- Agregar tablas de facturación electrónica a la publicación filial{{id}}_pub
-- Estas tablas ahora tienen sucursal_id para identificación compuesta (id, sucursal_id)
-- para soportar la replicación lógica al servidor master
--
-- Este script detecta automáticamente la publicación con formato filial*_pub
-- IMPORTANTE: Este script FALLA si no encuentra la publicación o si hay errores

DO $$
DECLARE
    v_pubname TEXT;
    v_table_count INTEGER := 0;
BEGIN
    -- Buscar la publicación que coincida con el patrón filial*_pub
    SELECT pubname INTO v_pubname
    FROM pg_publication
    WHERE pubname LIKE 'filial%_pub'
    LIMIT 1;
    
    -- Si no se encuentra la publicación, FALLAR
    IF v_pubname IS NULL THEN
        RAISE EXCEPTION 'ERROR CRÍTICO: No se encontró ninguna publicación con el patrón filial%%_pub. Verifica que la publicación existe antes de ejecutar esta migración.';
    END IF;
    
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Publicación encontrada: %', v_pubname;
    RAISE NOTICE 'Agregando tablas de facturación electrónica...';
    RAISE NOTICE '============================================================';
    
    -- Agregar documento_electronico
    BEGIN
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.documento_electronico', v_pubname);
        v_table_count := v_table_count + 1;
        RAISE NOTICE '  ✓ financiero.documento_electronico agregado';
    EXCEPTION
        WHEN duplicate_object THEN
            RAISE NOTICE '  - financiero.documento_electronico ya existe en la publicación (OK)';
        WHEN OTHERS THEN
            RAISE EXCEPTION 'ERROR al agregar financiero.documento_electronico: % - %', SQLSTATE, SQLERRM;
    END;
    
    -- Agregar lote_de
    BEGIN
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.lote_de', v_pubname);
        v_table_count := v_table_count + 1;
        RAISE NOTICE '  ✓ financiero.lote_de agregado';
    EXCEPTION
        WHEN duplicate_object THEN
            RAISE NOTICE '  - financiero.lote_de ya existe en la publicación (OK)';
        WHEN OTHERS THEN
            RAISE EXCEPTION 'ERROR al agregar financiero.lote_de: % - %', SQLSTATE, SQLERRM;
    END;
    
    -- Agregar evento_nominacion_de
    BEGIN
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_nominacion_de', v_pubname);
        v_table_count := v_table_count + 1;
        RAISE NOTICE '  ✓ financiero.evento_nominacion_de agregado';
    EXCEPTION
        WHEN duplicate_object THEN
            RAISE NOTICE '  - financiero.evento_nominacion_de ya existe en la publicación (OK)';
        WHEN OTHERS THEN
            RAISE EXCEPTION 'ERROR al agregar financiero.evento_nominacion_de: % - %', SQLSTATE, SQLERRM;
    END;
    
    -- Agregar evento_cancelacion_de
    BEGIN
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.evento_cancelacion_de', v_pubname);
        v_table_count := v_table_count + 1;
        RAISE NOTICE '  ✓ financiero.evento_cancelacion_de agregado';
    EXCEPTION
        WHEN duplicate_object THEN
            RAISE NOTICE '  - financiero.evento_cancelacion_de ya existe en la publicación (OK)';
        WHEN OTHERS THEN
            RAISE EXCEPTION 'ERROR al agregar financiero.evento_cancelacion_de: % - %', SQLSTATE, SQLERRM;
    END;
    
    -- Agregar timbrado_detalle
    BEGIN
        EXECUTE format('ALTER PUBLICATION %I ADD TABLE financiero.timbrado_detalle', v_pubname);
        v_table_count := v_table_count + 1;
        RAISE NOTICE '  ✓ financiero.timbrado_detalle agregado';
    EXCEPTION
        WHEN duplicate_object THEN
            RAISE NOTICE '  - financiero.timbrado_detalle ya existe en la publicación (OK)';
        WHEN OTHERS THEN
            RAISE EXCEPTION 'ERROR al agregar financiero.timbrado_detalle: % - %', SQLSTATE, SQLERRM;
    END;
    
    RAISE NOTICE '============================================================';
    RAISE NOTICE '✅ PROCESO COMPLETADO EXITOSAMENTE';
    RAISE NOTICE 'Publicación: %', v_pubname;
    RAISE NOTICE 'Tablas agregadas en esta ejecución: %', v_table_count;
    RAISE NOTICE '============================================================';
    
END $$;

-- Verificación: Listar todas las tablas en la publicación
DO $$
DECLARE
    v_pubname TEXT;
    v_count INTEGER;
BEGIN
    SELECT pubname INTO v_pubname
    FROM pg_publication
    WHERE pubname LIKE 'filial%_pub'
    LIMIT 1;
    
    IF v_pubname IS NOT NULL THEN
        RAISE NOTICE '';
        RAISE NOTICE '📋 VERIFICACIÓN - Tablas de facturación electrónica en %:', v_pubname;
        RAISE NOTICE '------------------------------------------------------------';
        
        FOR v_count IN 
            SELECT 1
            FROM pg_publication_tables
            WHERE pubname = v_pubname
              AND tablename IN ('documento_electronico', 'lote_de', 'evento_nominacion_de', 'evento_cancelacion_de', 'timbrado_detalle')
            ORDER BY tablename
        LOOP
            NULL;
        END LOOP;
        
        SELECT COUNT(*) INTO v_count
        FROM pg_publication_tables
        WHERE pubname = v_pubname
          AND tablename IN ('documento_electronico', 'lote_de', 'evento_nominacion_de', 'evento_cancelacion_de', 'timbrado_detalle');
        
        RAISE NOTICE 'Total de tablas de facturación electrónica: %/5', v_count;
        
        IF v_count < 5 THEN
            RAISE EXCEPTION 'VERIFICACIÓN FALLIDA: Solo % de 5 tablas están en la publicación', v_count;
        END IF;
        
        RAISE NOTICE '✅ Verificación exitosa: Todas las tablas están en la publicación';
    END IF;
END $$;
