package io.autocrypt.jwlee.cowork.erdagent.domain;

/**
 * Request to analyze the project codebase for entities and generate an ERD.
 */
public record ErdRequest(
    String path,
    String context
) {}
