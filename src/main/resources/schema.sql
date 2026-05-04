-- PulseDigest report storage schema (idempotent — Spring uruchamia przy każdym starcie).
-- Pojedyncza tabela z payloadem jako JSONB pozwala na elastyczne query
-- (np. payload->'report'->'items') bez normalizacji items na osobną tabelę.
CREATE TABLE IF NOT EXISTS reports (
    job_id       TEXT PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    payload      JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_generated_at ON reports (generated_at DESC);
