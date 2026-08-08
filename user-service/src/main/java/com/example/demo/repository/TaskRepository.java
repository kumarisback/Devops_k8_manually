package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByOwnerUsername(String ownerUsername);
}
