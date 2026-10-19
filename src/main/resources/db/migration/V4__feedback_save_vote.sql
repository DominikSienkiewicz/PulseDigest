-- N05: trzecia akcja w mailu — „zapisz" obok 👍/👎.
--
-- Powód jest o jakości sygnału, nie o wygodzie. Dopóki w mailu były dwa przyciski, czytelnik, który
-- chciał tylko odłożyć item na później, klikał 👍 — bo to był jedyny przycisk, który nie karał. Każdy
-- taki klik wchodził do `netVotesBySource` jako +1 preferencji wobec źródła, której czytelnik nigdy
-- nie wyraził. Osobna wartość odciąża 👍 z roli zakładki i tym samym czyści dane, na których uczy się
-- scoring.
--
-- SAVE jest arytmetycznie NEUTRALNE: `netVotesBySource` i `netVotesByCategory` liczą je jako 0, nie
-- jako -1 (co robiłby wcześniejszy `CASE vote WHEN 'UP' THEN 1 ELSE -1 END`) i nie jako +1 (co
-- scalałoby z powrotem dwa sygnały, które ta zmiana właśnie rozdziela). Nie wchodzi też do
-- kalibracji: „odłożę na później" nie jest potwierdzeniem, że item był trafiony.
--
-- Nazwa constraintu to nazwa nadana przez Postgresa inline'owemu CHECK z V1 (`feedback_vote_check`).
-- DROP bez IF EXISTS celowo: gdyby nazwa była inna, migracja ma paść głośno, a nie dołożyć drugi
-- constraint obok starego, który dalej odrzucałby SAVE.
ALTER TABLE feedback DROP CONSTRAINT feedback_vote_check;

ALTER TABLE feedback ADD CONSTRAINT feedback_vote_check CHECK (vote IN ('UP', 'DOWN', 'SAVE'));

COMMENT ON COLUMN feedback.vote IS
    'UP = więcej takich, DOWN = mniej takich, SAVE = zakładka (neutralna dla scoringu i kalibracji)';
