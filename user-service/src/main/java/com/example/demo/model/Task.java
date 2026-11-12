package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "tasks")
@Data
@NoArgsConstructor
public class Task {
    @Id
    private String id;
    private String title;

    // Expand-contract rename, stage 1 of 3 (roadmap step 24, gitops
    // docs/MIGRATIONS.md): the stored field `description` becomes `details`.
    // This version writes both and reads `details`, falling back to
    // `description` for tasks written before it; migration V2 copies
    // `description` into `details` for those. The version before this one
    // reads only `description`, so it keeps working while this one rolls out
    // and after a rollback. Stage 2 stops reading `description`, stage 3
    // stops writing it and removes it. The API keeps calling it
    // `description` throughout: the stored name and the API's are separate
    // contracts.
    @JsonIgnore
    @Field("details")
    private String details;

    @JsonIgnore
    @Field("description")
    private String legacyDescription;

    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED
    private String ownerUsername;
    private Instant createdAt = Instant.now();

    @JsonProperty("description")
    public String getDescription() {
        return details != null ? details : legacyDescription;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.details = description;
        this.legacyDescription = description;
    }
}
