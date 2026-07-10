-- PulseDigest report storage schema (idempotent — Spring uruchamia przy każdym starcie).
-- Pojedyncza tabela z payloadem jako JSONB pozwala na elastyczne query
-- (np. payload->'report'->'items') bez normalizacji items na osobną tabelę.
CREATE TABLE IF NOT EXISTS reports (
    job_id       TEXT PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    payload      JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_generated_at ON reports (generated_at DESC);

-- GIN index na payloadzie wspiera zapytania JSONB po zawartości raportu
-- (np. payload->'report'->'items', kategorie, tytuły). jsonb_path_ops jest mniejszy
-- i szybszy dla operatora @> niż domyślny jsonb_ops.
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

-- Tech-demand history: jeden wiersz per miesięczny wątek HN "Who is hiring?". Wcześniej delta m/m
-- była liczona bezstanowo — każdy bieg ponownie ściągał ~1000 komentarzy poprzedniego miesiąca.
-- vocabulary_version wersjonuje słownik technologii: zmiana listy zmienia znaczenie "mentions",
-- więc porównywanie w poprzek tej granicy byłoby fałszem. Klucz naturalny: (miesiąc, słownik).
CREATE TABLE IF NOT EXISTS tech_demand_history (
    month_label        TEXT NOT NULL,
    vocabulary_version TEXT NOT NULL,
    total_postings     INTEGER NOT NULL,
    counts             JSONB NOT NULL,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (month_label, vocabulary_version)
);
