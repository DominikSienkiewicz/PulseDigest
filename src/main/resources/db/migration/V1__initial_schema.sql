-- PulseDigest — pierwotny schemat (baseline Flyway V1).
-- Świeża Supabase: jeden czysty plik. Idempotencję i wersjonowanie trzyma flyway_schema_history,
-- więc żadnych IF NOT EXISTS ani ALTER-ów — kolejne zmiany dodaje się jako V2, V3, ... .

-- Persystencja raportów. Payload jako pojedynczy JSONB pozwala na elastyczne query
-- (np. payload->'report'->'items') bez normalizacji itemów na osobną tabelę.
CREATE TABLE reports (
    job_id       TEXT PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    payload      JSONB NOT NULL
);

CREATE INDEX idx_reports_generated_at ON reports (generated_at DESC);

-- GIN na payloadzie wspiera zapytania JSONB po zawartości raportu. jsonb_path_ops jest mniejszy
-- i szybszy dla operatora @> niż domyślny jsonb_ops.
CREATE INDEX idx_reports_payload_gin ON reports USING GIN (payload jsonb_path_ops);

-- Reader feedback (C6/C13): jeden wiersz per klik "👍/👎 takich" z maila. Zapisywany przez ZEWNĘTRZNY
-- receiver (headless-batch NIGDY nie serwuje HTTP), czytany przy kolejnym biegu, by wyciszyć
-- down-votowane itemy i nauczyć się preferencji per kategoria. Kontrakt receivera:
--   GET ...?url=<url>&vote=up|down&source=<source>&category=<category>&edition=<YYYY-MM-DD>[&sig=<HMAC>]
-- category: 👎 na nudnym paperze karze temat („Research"), nie całe arXiv. NULL = receiver jeszcze
--   nie zna parametru; scoring i prompt degradują się wtedy do zachowania per-źródło.
-- edition: pozwala receiverowi wymusić jeden głos na item na edycję (patrz indeks niżej).
CREATE TABLE feedback (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_url   TEXT NOT NULL,
    source     TEXT,
    vote       TEXT NOT NULL CHECK (vote IN ('UP', 'DOWN')),
    category   TEXT,
    edition    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_created_at ON feedback (created_at DESC);

-- Jeden głos na item na edycję. Skaner pocztowy, który pobierze linka kilka razy (albo pobierze
-- i 👍, i 👎), nie zwielokrotni głosu — pierwszy wygrywa, reszta odbija się o constraint. Indeks
-- częściowy: wiersze bez `edition` (stary receiver) nie są nim objęte, więc pętla działa dalej.
CREATE UNIQUE INDEX uq_feedback_one_vote_per_edition
    ON feedback (item_url, edition) WHERE edition IS NOT NULL;

-- Tech-demand history: jeden wiersz per miesięczny wątek HN "Who is hiring?". Bez tej tabeli delta
-- m/m była liczona bezstanowo — każdy bieg ponownie ściągał ~1000 komentarzy poprzedniego miesiąca.
-- vocabulary_version wersjonuje słownik technologii: zmiana listy zmienia znaczenie "mentions", więc
-- porównywanie w poprzek tej granicy byłoby fałszem. Klucz naturalny: (miesiąc, słownik).
CREATE TABLE tech_demand_history (
    month_label        TEXT NOT NULL,
    vocabulary_version TEXT NOT NULL,
    total_postings     INTEGER NOT NULL,
    counts             JSONB NOT NULL,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (month_label, vocabulary_version)
);

-- Żywy Model Czytelnika: wersjonowany, append-only profil destylowany raz w tygodniu z głosów 👍/👎.
-- NIE nadpisujemy wierszy — profil, który zdryfował, ma zostać do wglądu obok tego, który go zastąpił.
-- `profile` to JSONB z listą hipotez, każda z własną ewidencją i datą obserwacji (TTL po stronie kodu).
CREATE TABLE reader_profile (
    version      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    distilled_at TIMESTAMPTZ NOT NULL,
    vote_count   INTEGER NOT NULL,
    profile      JSONB NOT NULL
);

CREATE INDEX idx_reader_profile_distilled_at ON reader_profile (distilled_at DESC);
