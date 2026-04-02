package io.autocrypt.jwlee.cowork.erdagent.domain;

/**
 * Result containing the Markdown report with Mermaid ERD and explanation.
 */
public record ErdResult(
    String markdownContent,
    String statusMessage
) {}
