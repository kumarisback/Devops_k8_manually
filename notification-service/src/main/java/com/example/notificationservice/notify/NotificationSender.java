package com.example.notificationservice.notify;

import com.example.notificationservice.events.IncomingEvent;

/** Delivers a notification. Throws {@link TransientNotificationException} when a retry may succeed. */
public interface NotificationSender {

    void send(IncomingEvent event);
}
