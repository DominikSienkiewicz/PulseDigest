package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Applies the production Flyway migrations to a test database.
 *
 * <p>The ITs run the very same {@code db/migration} scripts that ship to Supabase, so every build
 * proves the migration set applies cleanly to a fresh Postgres — not a hand-maintained copy of the
 * schema that could quietly drift from what actually gets deployed.
 */
final class TestSchema {

    private TestSchema() {
    }

    static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
