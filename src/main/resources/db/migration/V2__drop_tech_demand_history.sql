-- Tech Demand został usunięty w całości 2026-10-16 (commit 92a8fee): `TechDemandPulse`,
-- `TechDemandAggregator`, oba porty, adaptery, properties i ~47 linii `application.yaml`. Tabela
-- przeżyła ten commit, bo `V1` była już zaaplikowana, a zaaplikowanej migracji się nie edytuje —
-- jedyną drogą jest nowa wersja. Nic jej nie czyta ani nie zapisuje.
DROP TABLE tech_demand_history;

-- RETENCJA: świadomie NIE wprowadzona tutaj, i to jest decyzja, nie przeoczenie.
--
-- Payload w `reports` jest nośny, nie archiwalny. Dedup czyta `payload->'report'->'items'` w oknie
-- `report.dedup.lookback-days` (10 dni), a trend memory i recap czytają `payload->'report'->'signals'`
-- w oknie `report.history.lookback-days` (28 dni). Polityka kasująca payloady musiałaby więc i tak
-- zostawić co najmniej 28 dni, czyli nie tknęłaby nic, co zajmuje miejsce.
--
-- Powyżej tej granicy przycinanie też nie jest obojętne: cztery rozważane kierunki — scoreboard
-- predykcji, wydanie kwartalne, replay starych edycji i publiczne archiwum — potrzebują właśnie
-- STARYCH payloadów. Przy ~104 edycjach rocznie koszt składowania jest trywialny, a skasowana
-- edycja jest nieodwracalna. Gdyby retencja kiedyś weszła, ta kolejność jest właściwa: najpierw
-- zdecydować o archiwum, potem o przycinaniu.
