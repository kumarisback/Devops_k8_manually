package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    @Id
    private String id;
    private String title;
    private String description;
    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED
    private String ownerUsername;
    private Instant createdAt = Instant.now();
}
