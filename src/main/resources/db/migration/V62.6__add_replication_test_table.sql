-- Tabla de test para verificar replicación bidireccional entre central y filial.
-- Usada por LogicalReplicationService.testReplicationE2E() durante el setup de replicación.
-- Anteriormente se creaba dinámicamente vía JDBC remoto; esta migración la formaliza.
CREATE TABLE IF NOT EXISTS configuraciones.replication_test (
    id BIGSERIAL PRIMARY KEY,
    test_uuid VARCHAR(64) NOT NULL,
    source_db VARCHAR(20) NOT NULL,
    sucursal_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

ALTER TABLE configuraciones.replication_test REPLICA IDENTITY FULL;

COMMENT ON TABLE configuraciones.replication_test IS 'Tabla usada para test E2E de replicación lógica (insert/select/delete en ambas direcciones).';
