package io.autocrypt.jwlee.cowork.agents.presales;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.autocrypt.jwlee.cowork.core.eval.ModelGrader;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;

@ActiveProfiles({"gemini", "cron"})
public class PresalesPromptEvalTest extends EmbabelMockitoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PresalesPromptEvalTest.class);

    @Autowired
    private PromptProvider promptProvider;

    @Autowired
    private ModelGrader modelGrader;

    @Autowired
    private Ai ai;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record TestCase(String inquiry) {}

    @Test
    public void evaluateCrsGenerationPrompt() throws IOException {
        // 1. Load Dataset
        List<TestCase> dataset = objectMapper.readValue(
            new ClassPathResource("evals/presales/dataset.json").getInputStream(),
            new TypeReference<List<TestCase>>() {}
        );

        List<ModelGrader.EvalResult> results = new ArrayList<>();

        log.info("Starting Prompt Evaluation for PresalesAgent CRS Generation...");

        for (TestCase tc : dataset) {
            // 2. Generate Prompt using PromptProvider
            String techContext = "Standard technical specifications related to " + tc.inquiry();
            String prompt = promptProvider.getPrompt("agents/presales/refine-requirements-final.jinja", Map.of(
                "sourceContent", tc.inquiry(),
                "techContext", techContext
            ));

            // 3. Get LLM Response
            String output = ai.withLlmByRole("normal").generateText(prompt);

            // 4. Grade the result
            ModelGrader.EvalResult grade = modelGrader.grade(tc.inquiry(), output);
            results.add(grade);

            log.info("Test Case: {}", tc.inquiry());
            log.info("Score: {}", grade.score());
            log.info("Reasoning: {}", grade.reasoning());
            log.info("Strengths: {}", grade.strengths());
            log.info("Weaknesses: {}", grade.weaknesses());
            log.info("--------------------------------------------------");
        }

        // 5. Final Report
        double averageScore = results.stream().mapToDouble(ModelGrader.EvalResult::score).average().orElse(0.0);
        log.info("==================================================");
        log.info("FINAL EVALUATION REPORT");
        log.info("Average Score: {} / 10.0", String.format("%.2f", averageScore));
        log.info("==================================================");
    }
}
