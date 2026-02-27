DO $$
BEGIN
    -- Check if the column 'ip' exists
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'empresarial'
                     AND table_name = 'sucursal'
                     AND column_name = 'ip') THEN
        ALTER TABLE empresarial.sucursal ADD COLUMN ip VARCHAR NULL;
    END IF;

    -- Check if the column 'puerto' exists
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'empresarial'
                     AND table_name = 'sucursal'
                     AND column_name = 'puerto') THEN
        ALTER TABLE empresarial.sucursal ADD COLUMN puerto INT4 NULL;
    END IF;
END $$;