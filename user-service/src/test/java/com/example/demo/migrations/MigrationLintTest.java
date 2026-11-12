package com.example.demo.migrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** CI's migration check (roadmap step 24): the real migrations, then each rule. */
class MigrationLintTest {

    private final MigrationLint lint = new MigrationLint();

    @TempDir
    Path dir;

    @Test
    void theServicesMigrationsPass() throws IOException {
        assertThat(lint.problems(Path.of("db/migration"))).isEmpty();
    }

    @Test
    void refusesADropOutsideTheContractPhase() throws IOException {
        write("V1__remove_old_tasks.json", "{\"drop\": \"tasks\"}");

        assertThat(lint.problems(dir)).singleElement().asString().contains("only in a contract_ migration");
    }

    @Test
    void refusesRemovingAFieldOutsideTheContractPhase() throws IOException {
        write("V1__tidy.json", "{\"update\": \"tasks\", \"updates\": [{\"q\": {}, \"u\": {\"$unset\": {\"description\": \"\"}}, \"multi\": true}]}");

        assertThat(lint.problems(dir)).singleElement().asString().contains("removes or renames fields");
    }

    @Test
    void allowsItInAContractMigration() throws IOException {
        write("V1__contract_tasks_description.json", "{\"update\": \"tasks\", \"updates\": [{\"q\": {}, \"u\": {\"$unset\": {\"description\": \"\"}}, \"multi\": true}]}");

        assertThat(lint.problems(dir)).isEmpty();
    }

    @Test
    void wantsIndexBuildsOutsideATransaction() throws IOException {
        write("V1__tasks_index.json", "{\"createIndexes\": \"tasks\", \"indexes\": [{\"key\": {\"a\": 1}, \"name\": \"a\"}]}");

        assertThat(lint.problems(dir)).singleElement().asString().contains("executeInTransaction=false");

        write("V1__tasks_index.json.conf", "executeInTransaction=false\n");
        assertThat(lint.problems(dir)).isEmpty();
    }

    @Test
    void checksNamesNumbersAndContent() throws IOException {
        write("V1__first.json", "{\"insert\": \"a\", \"documents\": [{}]}");
        write("V3__skips_two.json", "{\"insert\": \"a\", \"documents\": [{}]}");
        write("V4__Bad-Name.json", "{}");
        write("V5__not_json.json", "{oops");
        write("V6__orphan.json.conf", "executeInTransaction=false\n");

        // V4 is also missing: a file with a bad name isn't a migration.
        assertThat(lint.problems(dir)).hasSize(5).anySatisfy(p -> assertThat(p).contains("V2: missing"))
                .anySatisfy(p -> assertThat(p).contains("V4: missing"))
                .anySatisfy(p -> assertThat(p).contains("V4__Bad-Name.json: not V<n>"))
                .anySatisfy(p -> assertThat(p).contains("not valid JSON"))
                .anySatisfy(p -> assertThat(p).contains("no such migration"));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }
}
