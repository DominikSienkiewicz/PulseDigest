package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;

/**
 * Port wzbogacenia raportu o dodatkowe sekcje (trendy, sentyment, itp.).
 * Implementacje w innych modułach mogą domykać ten kontrakt; brak implementacji
 * = pipeline działa bez zmian (zwracany jest oryginalny raport).
 */
public interface ReportEnrichmentPort {

    /**
     * Wzbogaca raport o dodatkowe dane. Implementacja MUSI być graceful —
     * w razie błędu zwraca oryginalny raport bez modyfikacji (pipeline mailowy
     * nigdy nie powinien paść z powodu enrichment).
     */
    ReportData enrich(ReportData report);
}
