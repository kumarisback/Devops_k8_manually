package com.example.demo.migrations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrateTest {

    @Test
    void takesCredentialsOutOfTheAddress() {
        assertThat(Migrate.withoutCredentials("mongodb://svc:p%40ss@db-0.db:27017/app?replicaSet=rs0"))
                .isEqualTo("mongodb://db-0.db:27017/app?replicaSet=rs0");
        assertThat(Migrate.withoutCredentials("mongodb+srv://svc:secret@cluster0.example.net/app?retryWrites=true"))
                .isEqualTo("mongodb+srv://cluster0.example.net/app?retryWrites=true");
    }

    @Test
    void leavesAnAddressWithoutCredentialsAlone() {
        assertThat(Migrate.withoutCredentials("mongodb://mongodb-0.mongodb:27017/microservices?replicaSet=rs0"))
                .isEqualTo("mongodb://mongodb-0.mongodb:27017/microservices?replicaSet=rs0");
    }

    @Test
    void passesTheCredentialsToFlywayInstead() {
        var config = Migrate.configure("mongodb://svc:secret@db:27017/app", "filesystem:db/migration");

        assertThat(config.getUrl()).isEqualTo("mongodb://db:27017/app");
        assertThat(config.getUser()).isEqualTo("svc");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.isCleanDisabled()).isTrue();
    }
}
