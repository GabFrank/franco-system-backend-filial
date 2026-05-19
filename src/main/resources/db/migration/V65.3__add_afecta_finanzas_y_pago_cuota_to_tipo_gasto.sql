ALTER TABLE financiero.tipo_gasto ADD COLUMN IF NOT EXISTS afecta_finanzas_activo BOOLEAN;
ALTER TABLE financiero.tipo_gasto ADD COLUMN IF NOT EXISTS es_pago_cuota_activo BOOLEAN;
