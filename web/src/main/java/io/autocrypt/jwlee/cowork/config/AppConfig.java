package io.autocrypt.jwlee.cowork.config;

import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public RoleGoalBackstory simple() {
        return new RoleGoalBackstory(
            "Quick Technical Assistant",
            "Provide fast, accurate data extraction and search from technical documents.",
            "You are an expert at scanning documents for specific information without adding unnecessary commentary."
        );
    }

    @Bean
    public RoleGoalBackstory normal() {
        return new RoleGoalBackstory(
            "Senior Presales Engineer",
            "Synthesize complex technical requirements and product capabilities into professional reports.",
            "You have years of experience in technical consulting and can identify subtle gaps between customer needs and product features."
        );
    }
}
