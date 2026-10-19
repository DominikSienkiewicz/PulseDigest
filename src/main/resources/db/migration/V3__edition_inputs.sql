-- N24: wejście, które widział prompt, zapisane per edycja — fundament pod replay (N25).
--
-- Osobna tabela, nie kolumna w `reports`, z dwóch powodów. Pierwszy: te payloady są o rząd
-- wielkości większe od raportu, a `reports` jest czytany w gorącej ścieżce (dedup i trend memory
-- sięgają do środka jego JSONB przy każdym biegu) — nie ma powodu ciągnąć tam megabajtów wejścia.
-- Drugi: retencja. N02 rozstrzygnęło, że payloady RAPORTÓW zostają na zawsze, bo tego chcą
-- archiwum i scoreboard predykcji. Dla wejść ta decyzja nie jest wcale przesądzona, a osobna
-- tabela pozwala je przycinać bez dotykania edycji.
--
-- ON DELETE CASCADE: usunięcie edycji usuwa jej wejście. Odwrotnie nie — wejście można skasować
-- samo, bo edycja jest bez niego kompletna, tylko nieodtwarzalna.
CREATE TABLE edition_inputs (
    job_id      TEXT PRIMARY KEY REFERENCES reports (job_id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload     JSONB NOT NULL
);

CREATE INDEX idx_edition_inputs_captured_at ON edition_inputs (captured_at DESC);
