package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReportStoragePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

/**
 * Odpowiada za persystencję raportów do pliku JSON na dysku.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FileReportStorageAdapter implements ReportStoragePort {

    private static final String LATEST_FILE = "latest-report.json";
    private static final String HISTORY_FILE = "report-%s.json";

    @Value("${report.storage-path:./reports}")
    private String storagePath;
    private final ReportProperties reportProperties;

    private Path storageDir;
    private ObjectMapper storageMapper;
    private int cacheTtlMinutes;

    private volatile PersistedReport cachedReport = null;
    private volatile Instant cacheExpiresAt = null;

    /**
     * Ładuje ostatni raport z dysku przy starcie aplikacji.
     * Jeśli plik nie istnieje lub jest uszkodzony – startuje bez raportu (graceful).
     */
    @PostConstruct
    public void loadOnStartup() {
        this.storageDir = Paths.get(storagePath).toAbsolutePath();
        this.cacheTtlMinutes = reportProperties.cacheTtlMinutes();
        this.storageMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Path latestPath = storageDir.resolve(LATEST_FILE);
        if (!Files.exists(latestPath)) {
            log.info("Brak zapisanego raportu ({}). Będzie wygenerowany na żądanie.", latestPath);
            return;
        }
        try {
            PersistedReport loaded = storageMapper.readValue(latestPath.toFile(), PersistedReport.class);
            int insightsCount = loaded.report() != null && loaded.report().topInsights() != null
                    ? loaded.report().topInsights().size() : 0;
            int itemsCount = loaded.report() != null && loaded.report().items() != null
                    ? loaded.report().items().size() : 0;

            this.cachedReport = loaded;
            this.cacheExpiresAt = Instant.now().plusSeconds(cacheTtlMinutes * 60L);

            log.info("Załadowano raport z dysku: {} | {} insights, {} itemów | wygenerowany: {} | cache wygasa: {}",
                    latestPath, insightsCount, itemsCount, loaded.generatedAt(), cacheExpiresAt);
        } catch (IOException e) {
            log.warn("Nie można odczytać persystowanego raportu ({}): {}", latestPath, e.getMessage());
        }
    }

    /**
     * Zapisuje raport na dysk (latest + archiwum).
     */
    public void save(PersistedReport report) {
        try {
            Files.createDirectories(storageDir);

            // latest-report.json (nadpisywany)
            Path latestPath = storageDir.resolve(LATEST_FILE);
            storageMapper.writerWithDefaultPrettyPrinter().writeValue(latestPath.toFile(), report);

            // Archiwum z timestampem
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
                    .withZone(java.time.ZoneOffset.UTC)
                    .format(report.generatedAt());
            Path historyPath = storageDir.resolve(HISTORY_FILE.formatted(timestamp));
            storageMapper.writerWithDefaultPrettyPrinter().writeValue(historyPath.toFile(), report);

            this.cachedReport = report;
            this.cacheExpiresAt = Instant.now().plusSeconds(cacheTtlMinutes * 60L);
            log.info("Raport zapisany: {} | archiwum: {} | cache wygasa: {}", latestPath.getFileName(), historyPath.getFileName(),
                    cacheExpiresAt);

        } catch (IOException e) {
            log.error("Błąd zapisu raportu do pliku: {}", e.getMessage(), e);
        }
    }

    /**
     * Zwraca ostatnio załadowany/zapisany raport lub empty jeśli brak.
     * Używa cache z TTL – jeśli cache jest ważny, zwraca bez odczytu z dysku.
     * Po wygaśnięciu TTL odczytuje z dysku i odświeża cache.
     */
    public Optional<PersistedReport> getLatest() {
        if (cachedReport != null && cacheExpiresAt != null && Instant.now().isBefore(cacheExpiresAt)) {
            log.debug("Cache hit – zwracam raport z pamięci (wygasa: {})", cacheExpiresAt);
            return Optional.of(cachedReport);
        }

        log.debug("Cache miss lub TTL wygasł – odczytuję raport z dysku");
        Path latestPath = storageDir.resolve(LATEST_FILE);
        if (!Files.exists(latestPath)) {
            return Optional.empty();
        }
        try {
            PersistedReport fromDisk = storageMapper.readValue(latestPath.toFile(), PersistedReport.class);
            this.cachedReport = fromDisk;
            this.cacheExpiresAt = Instant.now().plusSeconds(cacheTtlMinutes * 60L);
            log.debug("Raport załadowany z dysku do cache (wygasa: {})", cacheExpiresAt);
            return Optional.of(fromDisk);
        } catch (IOException e) {
            log.warn("Nie można odczytać raportu z dysku ({}): {}", latestPath, e.getMessage());
            return Optional.ofNullable(cachedReport);
        }
    }

    /**
     * Zwraca ścieżkę do katalogu storage (do logowania/debug).
     */
    public Path getStorageDir() {
        return storageDir;
    }
}
