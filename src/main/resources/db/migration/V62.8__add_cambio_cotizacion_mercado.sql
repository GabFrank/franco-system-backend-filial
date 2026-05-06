-- Agregar cotizaciones de mercado a la tabla de cambio
ALTER TABLE financiero.cambio ADD COLUMN IF NOT EXISTS valor_en_gs_venta_mercado DOUBLE PRECISION;
ALTER TABLE financiero.cambio ADD COLUMN IF NOT EXISTS valor_en_gs_compra_mercado DOUBLE PRECISION;
