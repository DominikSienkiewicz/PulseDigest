package pl.seniordeveloper.pulsedigest.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Standalone entry point that applies the Flyway migrations under {@code db/migration} to the
 * database named by the {@code FLYWAY_URL} / {@code FLYWAY_USER} / {@code FLYWAY_PASSWORD} environment
 * variables. Invoked by the {@code flywayMigrate} Gradle task as a discrete deploy step.
 *
 * <p>Deliberately outside the application: Flyway is a {@code compileOnly} dependency, so it never
 * reaches the digest's runtime classpath. Migrating the schema is the CI step's job, not the app's.
 */
@Slf4j
public final class FlywayMigrate {

    private FlywayMigrate() {
    }

    /**
     * Runs the migration. Exits non-zero on any failure so the CI step fails loudly.
     *
     * <p>Takes no arguments: everything it needs comes from the environment, and the launch protocol
     * has accepted an argument-less {@code main} since Java 25.
     */
    public static void main() {
        MigrateResult result = Flyway.configure()
                .dataSource(required("FLYWAY_URL"), required("FLYWAY_USER"), System.getenv("FLYWAY_PASSWORD"))
                .locations("classpath:db/migration")
                .load()
                .migrate();
        log.info("Flyway: {} migration(s) applied, schema now at version {}",
                result.migrationsExecuted, result.targetSchemaVersion);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
