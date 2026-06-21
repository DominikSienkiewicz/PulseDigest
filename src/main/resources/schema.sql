-- PulseDigest report storage schema (idempotent — Spring uruchamia przy każdym starcie).
-- Pojedyncza tabela z payloadem jako JSONB pozwala na elastyczne query
-- (np. payload->'report'->'items') bez normalizacji items na osobną tabelę.
CREATE TABLE IF NOT EXISTS reports (
    job_id       TEXT PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    payload      JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_generated_at ON reports (generated_at DESC);

-- GIN index na payloadzie wspiera zapytania JSONB w trend_analytics
-- (np. payload->'report'->'items', kategorie, tytuły). jsonb_path_ops jest mniejszy
-- i szybszy dla operatora @> niż domyślny jsonb_ops; wystarcza do trend-detection.
CREATE INDEX IF NOT EXISTS idx_reports_payload_gin ON reports USING GIN (payload jsonb_path_ops);

-- Reader feedback (C6): jeden wiersz per klik "👍/👎 takich" z maila. Zapisywany przez ZEWNĘTRZNY
-- receiver (headless-batch NIGDY nie serwuje HTTP), czytany przy kolejnym biegu, by wyciszyć
-- down-votowane itemy. Kontrakt receivera: GET ...?url=<url>&vote=up|down&source=<source>.
CREATE TABLE IF NOT EXISTS feedback (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_url   TEXT NOT NULL,
    source     TEXT,
    vote       TEXT NOT NULL CHECK (vote IN ('UP', 'DOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback (created_at DESC);
