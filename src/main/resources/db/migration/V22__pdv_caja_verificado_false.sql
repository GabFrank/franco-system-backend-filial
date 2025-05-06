UPDATE financiero.pdv_caja
SET verificado = false
WHERE verificado IS NULL;  -- Optional, only updates rows with NULL
