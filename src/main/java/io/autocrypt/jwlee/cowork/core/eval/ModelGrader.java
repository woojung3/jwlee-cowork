package io.autocrypt.jwlee.cowork.core.eval;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;

import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;

@Service
public class ModelGrader {

    private final PromptProvider promptProvider;
    private final Ai ai;

    public ModelGrader(PromptProvider promptProvider, Ai ai) {
        this.promptProvider = promptProvider;
        this.ai = ai;
    }

    public record EvalResult(
        double score,
        String reasoning,
        List<String> strengths,
        List<String> weaknesses
    ) {}

    /**
     * Grades the output using an LLM based on rubrics.
     */
    public EvalResult grade(String inquiry, String output) {
        String prompt = promptProvider.getPrompt("shared/eval-grader.jinja", Map.of(
            "inquiry", inquiry,
            "output", output
        ));

        // Use a high-quality model for grading
        return ai.withLlm(LlmOptions.withLlmForRole("normal"))
                 .createObject(prompt, EvalResult.class);
    }
}
