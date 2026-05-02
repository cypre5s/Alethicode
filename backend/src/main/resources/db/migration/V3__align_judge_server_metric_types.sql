ALTER TABLE judge_server
    ALTER COLUMN memory_usage TYPE DOUBLE PRECISION USING memory_usage::double precision,
    ALTER COLUMN cpu_usage TYPE DOUBLE PRECISION USING cpu_usage::double precision;
