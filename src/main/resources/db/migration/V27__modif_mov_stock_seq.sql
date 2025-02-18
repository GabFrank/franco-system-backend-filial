

    DO $$
    DECLARE
        last_id BIGINT;
        new_start BIGINT;
    BEGIN
        -- Get the last ID value from the table
        SELECT COALESCE(MAX(id), 0) INTO last_id FROM operaciones.movimiento_stock;

        -- Debug output to verify the last_id
        RAISE NOTICE 'Last ID from table: %', last_id;

        -- Determine if the last ID is odd or even
        IF mod(last_id, 2) = 0 THEN
            -- It's even, so add 1 to make it odd
           new_start := last_id;
        ELSE
            -- It's already odd, so use it as the starting point
            new_start := last_id + 1;
        END IF;

        -- Debug output to verify the new_start
        RAISE NOTICE 'New start value for sequence: %', new_start;

        -- Set the sequence to the new value and set increment by 2
        PERFORM setval('operaciones.movimiento_stock_id_seq', new_start, true);
        EXECUTE 'ALTER SEQUENCE operaciones.movimiento_stock_id_seq INCREMENT BY 2;';
    END $$;