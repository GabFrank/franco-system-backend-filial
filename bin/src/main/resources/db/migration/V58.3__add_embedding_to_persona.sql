--se agrego la columna embedding a la tabla personas para realizar comparativas para el reconocimiento facial
ALTER TABLE personas.persona ADD COLUMN embedding text;
