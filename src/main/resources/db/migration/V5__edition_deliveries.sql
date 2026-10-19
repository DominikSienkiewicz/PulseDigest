-- F16: strażnik podwójnej wysyłki NA POZIOMIE APLIKACJI.
--
-- Do tej pory jedyną ochroną była grupa `concurrency` w GitHub Actions. To ochrona na poziomie
-- orkiestratora: działa, dopóki wszystkie biegi przechodzą przez tę jedną ścieżkę. Ręczne
-- uruchomienie JAR-a, drugi runner, przepisany workflow albo awaria samej grupy — i nie ma nic.
-- Czytelnik dostaje ten sam digest dwa razy, a OpenAI jest opłacone dwukrotnie.
--
-- Klucz na DACIE WYDANIA, nie na job_id: pytanie brzmi „czy to wydanie już poszło", a każdy bieg ma
-- własne job_id, więc tabela kluczowana po nim nie ma na czym się zderzyć.
--
-- Rozdzielone `claimed_at` i `delivered_at`, bo to dwa różne fakty. Rezerwacja mówi „ten bieg
-- właśnie wysyła"; dostarczenie mówi „poszło". Gdyby istniała tylko jedna kolumna, bieg, któremu
-- padła wysyłka, zablokowałby ponowienie na zawsze — a ponowienie po nieudanej wysyłce to
-- dokładnie ten przypadek, dla którego strażnik ma być bezpieczny.
--
-- Rezerwację nieukończoną można przejąć po `claim-timeout-minutes` (patrz
-- `SupabaseEditionDeliveryAdapter`): bieg, który zginął między rezerwacją a wysyłką, inaczej
-- zamurowałby wydanie. Rezerwacji ZAKOŃCZONEJ wysyłką nie przejmuje nic.
CREATE TABLE edition_deliveries (
    edition_date DATE PRIMARY KEY,
    job_id       TEXT        NOT NULL,
    claimed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ
);

COMMENT ON TABLE edition_deliveries IS
    'Jeden wiersz na datę wydania. Rezerwacja wysyłki (claimed_at) i potwierdzenie (delivered_at).';
