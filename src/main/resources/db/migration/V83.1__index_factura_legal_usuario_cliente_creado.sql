-- Índice de apoyo para el aviso de posible factura duplicada.
-- La consulta busca facturas del mismo cajero, al mismo cliente y emitidas el mismo
-- día, para comparar monto e items antes de emitir una factura nueva.
CREATE INDEX IF NOT EXISTS factura_legal_usuario_cliente_creado_idx
    ON financiero.factura_legal (usuario_id, cliente_id, sucursal_id, creado_en);
