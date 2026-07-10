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
-- down-votowane itemy. Kontrakt receivera:
--   GET ...?url=<url>&vote=up|down&source=<source>&category=<category>&edition=<YYYY-MM-DD>[&sig=<HMAC>]
CREATE TABLE IF NOT EXISTS feedback (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_url   TEXT NOT NULL,
    source     TEXT,
    vote       TEXT NOT NULL CHECK (vote IN ('UP', 'DOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback (created_at DESC);

-- Kategoria klikniętego itemu. 👎 na nudnym paperze ma karać temat („Research"), nie całe arXiv —
-- bez tej kolumny połowa informacji z kliknięcia była wyrzucana. NULL = receiver jeszcze nie zna
-- parametru; scoring i prompt degradują się wtedy do zachowania sprzed tej zmiany.
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS category TEXT;

-- Edycja, w której wysłano klikniętego linka. Kolumna dodawana osobnym ALTER-em, bo tabela
-- istnieje w prod od dawna, a schema.sql odpala się przy KAŻDYM starcie (spring.sql.init: always).
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS edition TEXT;

-- Jeden głos na item na edycję. Skaner pocztowy, który pobierze linka kilka razy (albo pobierze
-- i 👍, i 👎), nie zwielokrotni głosu — pierwszy wygrywa, reszta odbija się o constraint.
-- Indeks częściowy: wiersze bez `edition` (stary receiver, który jeszcze nie zna parametru)
-- nie są nim objęte, więc wdrożenie kolumny nie psuje działającej pętli.
CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_one_vote_per_edition
    ON feedback (item_url, edition) WHERE edition IS NOT NULL;

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

-- Żywy Model Czytelnika: wersjonowany, append-only profil destylowany raz w tygodniu z głosów 👍/👎.
-- NIE nadpisujemy wierszy — profil, który zdryfował, ma zostać do wglądu obok tego, który go zastąpił.
-- `profile` to JSONB z listą hipotez, każda z własną ewidencją i datą obserwacji (TTL po stronie kodu).
CREATE TABLE IF NOT EXISTS reader_profile (
    version      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    distilled_at TIMESTAMPTZ NOT NULL,
    vote_count   INTEGER NOT NULL,
    profile      JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reader_profile_distilled_at ON reader_profile (distilled_at DESC);
