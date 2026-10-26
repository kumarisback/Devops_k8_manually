package com.example.notificationservice.events;

/**
 * The schema registry couldn't be reached to read a record's schema. Nothing
 * is wrong with the record: it is retried, and dead-lettered only if the
 * registry stays away longer than the retries last.
 */
public class SchemaRegistryUnavailableException extends RuntimeException {

    public SchemaRegistryUnavailableException(Throwable cause) {
        super("schema registry unreachable", cause);
    }
}
