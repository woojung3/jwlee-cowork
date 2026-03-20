package io.autocrypt.jwlee.cowork.core.hitl;

/**
 * General notification event that is not necessarily tied to a HITL approval process.
 */
public record NotificationEvent(String title, String message) {
}
