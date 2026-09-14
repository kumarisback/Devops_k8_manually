package com.example.demo.controller;

import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("user-service.business");
    private static final Logger auditLogger = LoggerFactory.getLogger("audit.user-service.tasks");

    @Autowired
    private TaskRepository taskRepository;

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping
    public ResponseEntity<List<Task>> getAllUserTasks() {
        String username = getCurrentUsername();
        Span span = tracer.spanBuilder("tasks.list").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", username);
            List<Task> tasks = taskRepository.findByOwnerUsername(username);
            span.setAttribute("tasks.count", tasks.size());
            auditLogger.info("tasks_listed {} {}", kv("enduser.id", username), kv("tasks.count", tasks.size()));
            return ResponseEntity.ok(tasks);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable String id) {
        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
        }
        
        Task task = taskOpt.get();
        if (!task.getOwnerUsername().equals(getCurrentUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this task");
        }
        
        return ResponseEntity.ok(task);
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task taskRequest) {
        String username = getCurrentUsername();
        Span span = tracer.spanBuilder("tasks.create").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", username);
            Task task = new Task();
            task.setTitle(taskRequest.getTitle());
            task.setDescription(taskRequest.getDescription());
            if (taskRequest.getStatus() != null) {
                task.setStatus(taskRequest.getStatus());
            }
            task.setOwnerUsername(username);
            task.setCreatedAt(Instant.now());

            Task savedTask = taskRepository.save(task);
            span.setAttribute("task.id", savedTask.getId());
            span.setAttribute("task.status", String.valueOf(savedTask.getStatus()));
            auditLogger.info("task_created {} {} {}",
                    kv("enduser.id", username),
                    kv("task.id", savedTask.getId()),
                    kv("task.status", savedTask.getStatus()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable String id, @RequestBody Task taskRequest) {
        String username = getCurrentUsername();
        Span span = tracer.spanBuilder("tasks.update").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", username);
            span.setAttribute("task.id", id);
            Optional<Task> taskOpt = taskRepository.findById(id);
            if (taskOpt.isEmpty()) {
                span.setAttribute("task.found", false);
                auditLogger.warn("task_update_missing {} {}", kv("enduser.id", username), kv("task.id", id));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
            }

            Task task = taskOpt.get();
            if (!task.getOwnerUsername().equals(username)) {
                span.setAttribute("authorization.denied", true);
                auditLogger.warn("task_update_denied {} {} {}",
                        kv("enduser.id", username),
                        kv("task.id", id),
                        kv("task.owner", task.getOwnerUsername()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this task");
            }

            task.setTitle(taskRequest.getTitle());
            task.setDescription(taskRequest.getDescription());
            if (taskRequest.getStatus() != null) {
                task.setStatus(taskRequest.getStatus());
            }

            Task updatedTask = taskRepository.save(task);
            span.setAttribute("task.status", String.valueOf(updatedTask.getStatus()));
            auditLogger.info("task_updated {} {} {}",
                    kv("enduser.id", username),
                    kv("task.id", updatedTask.getId()),
                    kv("task.status", updatedTask.getStatus()));
            return ResponseEntity.ok(updatedTask);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable String id) {
        String username = getCurrentUsername();
        Span span = tracer.spanBuilder("tasks.delete").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("enduser.id", username);
            span.setAttribute("task.id", id);
            Optional<Task> taskOpt = taskRepository.findById(id);
            if (taskOpt.isEmpty()) {
                span.setAttribute("task.found", false);
                auditLogger.warn("task_delete_missing {} {}", kv("enduser.id", username), kv("task.id", id));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
            }

            Task task = taskOpt.get();
            if (!task.getOwnerUsername().equals(username)) {
                span.setAttribute("authorization.denied", true);
                auditLogger.warn("task_delete_denied {} {} {}",
                        kv("enduser.id", username),
                        kv("task.id", id),
                        kv("task.owner", task.getOwnerUsername()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have access to this task");
            }

            taskRepository.delete(task);
            auditLogger.info("task_deleted {} {}", kv("enduser.id", username), kv("task.id", id));
            return ResponseEntity.ok("Task deleted successfully");
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            throw ex;
        } finally {
            span.end();
        }
    }
}
