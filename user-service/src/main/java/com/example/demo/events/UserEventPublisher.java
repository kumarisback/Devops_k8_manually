package com.example.demo.events;

import com.example.demo.model.User;

/** Tells the rest of the system about new users: Kafka where the environment has it, nothing otherwise. */
public interface UserEventPublisher {

    void userRegistered(User user);
}
