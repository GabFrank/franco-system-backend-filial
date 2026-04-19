-- Add replication_test to the filial's branch publication so it replicates filial→central.
-- The publication name is dynamic (e.g. beta_filial2_pub, bodega_filial2_pub), so we discover it.
DO $$
DECLARE
    pub_name TEXT;
BEGIN
    SELECT pubname INTO pub_name
    FROM pg_publication
    WHERE pubname LIKE '%_filial%_pub'
      AND pubname NOT LIKE 'central_%'
    LIMIT 1;

    IF pub_name IS NOT NULL THEN
        -- Check if table is already in the publication
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = pub_name AND tablename = 'replication_test'
        ) THEN
            EXECUTE format('ALTER PUBLICATION %I ADD TABLE configuraciones.replication_test', pub_name);
            RAISE NOTICE 'Added configuraciones.replication_test to publication %', pub_name;
        ELSE
            RAISE NOTICE 'configuraciones.replication_test already in publication %', pub_name;
        END IF;
    ELSE
        RAISE NOTICE 'No branch publication found — skipping (replication may not be configured yet)';
    END IF;
END $$;
