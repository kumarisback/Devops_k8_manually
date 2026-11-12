package com.example.demo.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 1 of the description -> details rename (roadmap step 24): both fields
 * stored, `details` read first, the API unchanged.
 */
class TaskTest {

    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final MappingMongoConverter mongo = converter();

    private static MappingMongoConverter converter() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }

    @Test
    void writesBothFieldsSoThePreviousVersionStillReadsIt() {
        Task task = new Task();
        task.setDescription("buy milk");

        Document stored = new Document();
        mongo.write(task, stored);

        assertThat(stored.getString("details")).isEqualTo("buy milk");
        assertThat(stored.getString("description")).isEqualTo("buy milk");
    }

    @Test
    void readsDetailsFirst() {
        Document stored = new Document("details", "new").append("description", "old");

        assertThat(mongo.read(Task.class, stored).getDescription()).isEqualTo("new");
    }

    @Test
    void fallsBackToDescriptionForTasksWrittenBefore() {
        Document stored = new Document("description", "written by the previous version");

        assertThat(mongo.read(Task.class, stored).getDescription()).isEqualTo("written by the previous version");
    }

    @Test
    void theApiStillSaysDescriptionOnly() throws Exception {
        Task task = new Task();
        task.setTitle("t");
        task.setDescription("d");

        String body = json.writeValueAsString(task);

        assertThat(body).contains("\"description\":\"d\"").doesNotContain("details").doesNotContain("legacyDescription");
        assertThat(json.readValue("{\"title\":\"t\",\"description\":\"from a client\"}", Task.class).getDetails())
                .isEqualTo("from a client");
    }
}
