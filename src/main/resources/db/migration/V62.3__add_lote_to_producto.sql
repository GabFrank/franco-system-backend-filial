-- V62.3: Add lote field to producto
-- Sincronización con Central (V101.5): agrega columna lote a productos.producto
-- para que la replicación lógica no falle al recibir INSERTs desde farmacia

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'productos' AND table_name = 'producto') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'productos' AND table_name = 'producto' AND column_name = 'lote') THEN
            ALTER TABLE productos.producto ADD COLUMN lote BOOLEAN DEFAULT FALSE;
        END IF;
    END IF;
END $$;

COMMENT ON COLUMN productos.producto.lote IS 'Indica si el producto requiere control de lotes';
