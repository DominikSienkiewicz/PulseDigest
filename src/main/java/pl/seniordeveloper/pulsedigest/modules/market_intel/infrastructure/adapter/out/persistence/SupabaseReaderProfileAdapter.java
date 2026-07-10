package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ProfileHypothesis;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReaderProfile;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.ReaderProfilePort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Append-only versioned store of reader profiles in {@code reader_profile}. Each distillation adds a
 * row; nothing is ever updated, so a profile that drifted stays next to the one that replaced it.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class SupabaseReaderProfileAdapter implements ReaderProfilePort {

    private static final String LATEST_SQL = """
            SELECT distilled_at, vote_count, profile
            FROM reader_profile
            ORDER BY distilled_at DESC, version DESC
            LIMIT 1
            """;

    private static final String INSERT_SQL = """
            INSERT INTO reader_profile (distilled_at, vote_count, profile)
            VALUES (?, ?, ?::jsonb)
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ReaderProfile> latest() {
        return jdbcClient.sql(LATEST_SQL)
                .query((rs, rowNum) -> new ReaderProfile(
                        rs.getObject("distilled_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("vote_count"),
                        readHypotheses(rs.getString("profile"))))
                .optional();
    }

    @Override
    public void save(ReaderProfile profile) {
        try {
            jdbcClient.sql(INSERT_SQL)
                    .params(OffsetDateTime.ofInstant(profile.distilledAt(), ZoneOffset.UTC),
                            profile.voteCount(),
                            objectMapper.writeValueAsString(profile.hypotheses()))
                    .update();
            log.info("Reader profile v+1 stored: {} hypothesis/es from {} vote(s)",
                    profile.hypotheses().size(), profile.voteCount());
        } catch (Exception e) {
            throw new IllegalStateException("Could not persist reader profile", e);
        }
    }

    private List<ProfileHypothesis> readHypotheses(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProfileHypothesis>>() { });
        } catch (Exception e) {
            log.warn("Unreadable reader_profile payload — treating as no profile: {}", e.getMessage());
            return List.of();
        }
    }
}
