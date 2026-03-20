package io.autocrypt.jwlee.cowork.core.hitl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class TeamsApprovalListener {

    private static final Logger log = LoggerFactory.getLogger(TeamsApprovalListener.class);

    private final String webhookUrl;
    private final HttpClient httpClient;

    public TeamsApprovalListener(@Value("${app.teams.webhookUrl:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    @EventListener
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("app.teams.webhookUrl is not set. Skipping Teams notification.");
            return;
        }

        try {
            String payload = String.format("""
                {
                    "@type": "MessageCard",
                    "@context": "http://schema.org/extensions",
                    "themeColor": "0076D7",
                    "summary": "Agent Approval Required",
                    "sections": [{
                        "activityTitle": "🤖 Agent Approval Required",
                        "activitySubtitle": "Process ID: %s",
                        "facts": [{
                            "name": "Message",
                            "value": "%s"
                        }, {
                            "name": "Plan",
                            "value": "%s"
                        }],
                        "markdown": true
                    }]
                }
                """,
                escapeJson(event.processId()),
                escapeJson(event.message()),
                escapeJson(event.planDescription()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log.info("Successfully sent Teams notification for process: {}", event.processId());
                        } else {
                            log.warn("Failed to send Teams notification. Status: {}, Body: {}", response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Error sending Teams notification", ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to construct or send Teams notification", e);
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "")
                    .replace("\t", "\\t");
    }
}
