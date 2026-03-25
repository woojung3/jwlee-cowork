package io.autocrypt.jwlee.cowork.core.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.autocrypt.jwlee.cowork.BaseIntegrationTest;

class GoogleServiceToolsTest extends BaseIntegrationTest {

    @Autowired
    private GoogleServiceTools googleServiceTools;

    @Test
    @Disabled("Requires real GOOGLE_REFRESH_TOKEN in environment variables")
    void fetchGoogleTasks_shouldReturnTasks() {
        String result = googleServiceTools.fetchGoogleTasks();
        System.out.println("Google Tasks Result:\n" + result);
        
        assertThat(result).isNotNull();
        assertThat(result).doesNotStartWith("ERROR");
        // Should be markdown list or a message
        assertThat(result).matches(s -> s.startsWith("- ") || s.contains("No tasks found"));
    }

    @Test
    void contextLoads() {
        assertThat(googleServiceTools).isNotNull();
    }
}
