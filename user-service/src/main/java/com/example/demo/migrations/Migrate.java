package com.example.demo.migrations;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * user-service's database migrations (db/migration in this project, /app/db/migration
 * in the image): roadmap step 24, gitops docs/MIGRATIONS.md.
 *
 * <p>Its own command in the service's image, run once per release by a Job
 * before the new version's pods start (gitops apps/base/user-service), never
 * by the service itself: replicas starting together would race, and Flyway
 * can't lock its history collection in MongoDB. Without Spring: it needs only
 * the database's address.
 *
 * <pre>
 * java -Dloader.main=com.example.demo.migrations.Migrate -cp app.jar \
 *   org.springframework.boot.loader.launch.PropertiesLauncher [migrate|info|repair]
 * </pre>
 *
 * <ul>
 *   <li>{@code migrate} (default): applies what hasn't been applied.</li>
 *   <li>{@code info}: lists every migration and its state.</li>
 *   <li>{@code repair}: forgets a failed migration, after its half-done changes
 *       were undone by hand, so it can run again.</li>
 * </ul>
 *
 * <p>MONGO_URI is the service's own connection string. Credentials in it are
 * handed to Flyway separately, so they never appear in what it logs.
 * MIGRATIONS_LOCATION overrides where the files are (a Flyway location, e.g.
 * {@code filesystem:db/migration} when run from this project).
 */
public final class Migrate {

    static final String DEFAULT_LOCATION = "filesystem:/app/db/migration";

    private static final Logger log = LoggerFactory.getLogger(Migrate.class);

    private Migrate() {
    }

    public static void main(String[] args) {
        String uri = System.getenv("MONGO_URI");
        if (uri == null || uri.isBlank()) {
            log.error("MONGO_URI is not set");
            System.exit(2);
        }
        String location = System.getenv().getOrDefault("MIGRATIONS_LOCATION", DEFAULT_LOCATION);
        Flyway flyway = configure(uri, location).load();
        String command = args.length > 0 ? args[0] : "migrate";
        switch (command) {
            case "migrate" -> {
                MigrateResult result = flyway.migrate();
                log.info("Migrations: schema {} -> {}, {} applied",
                        result.initialSchemaVersion == null ? "empty" : result.initialSchemaVersion,
                        result.targetSchemaVersion == null ? "unchanged" : result.targetSchemaVersion,
                        result.migrationsExecuted);
            }
            case "info" -> {
                for (MigrationInfo info : flyway.info().all()) {
                    log.info("{} {} {}", info.getVersion(), info.getState().getDisplayName(),
                            info.getDescription());
                }
            }
            case "repair" -> {
                RepairResult result = flyway.repair();
                log.info("Repair: {} failed migration(s) removed from the history",
                        result.migrationsRemoved.size());
            }
            default -> {
                log.error("Unknown command {}: migrate, info or repair", command);
                System.exit(2);
            }
        }
    }

    static FluentConfiguration configure(String uri, String location) {
        ConnectionString connection = new ConnectionString(uri);
        MongoCredential credential = connection.getCredential();
        return Flyway.configure()
                .dataSource(withoutCredentials(uri),
                        credential == null ? null : credential.getUserName(),
                        credential == null || credential.getPassword() == null
                                ? null : new String(credential.getPassword()))
                // Files on disk: Flyway's MongoDB connector can't read them
                // from inside the service's jar.
                .locations(location)
                // JSON migrations: each file is one MongoDB command, run in a
                // transaction unless its <file>.conf says
                // executeInTransaction=false (an index on an existing
                // collection can't be built in one).
                .sqlMigrationSuffixes(".json")
                .validateMigrationNaming(true)
                // A migration edited after it ran fails validation instead of
                // being skipped: applied migrations are history.
                .validateOnMigrate(true)
                .outOfOrder(false)
                // Databases older than the migrations (the lab's, dev's) have
                // data but no history: start their history at version 0, so
                // every migration still runs on them.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // Never on a real database: clean drops everything.
                .cleanDisabled(true);
    }

    /** mongodb[+srv]://user:pass@host/... without "user:pass@". */
    static String withoutCredentials(String uri) {
        return uri.replaceFirst("^(mongodb(?:\\+srv)?://)[^@/]*@", "$1");
    }
}
