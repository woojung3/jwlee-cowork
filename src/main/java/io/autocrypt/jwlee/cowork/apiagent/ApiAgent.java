package io.autocrypt.jwlee.cowork.apiagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.apiagent.domain.ApiRequest;
import io.autocrypt.jwlee.cowork.apiagent.domain.ApiResult;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.FileReadTool;
import io.autocrypt.jwlee.cowork.core.tools.GrepTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Agent(description = "Discovers and analyzes all API endpoints in the codebase.")
@Component
public class ApiAgent {

    private final GrepTool grepTool;
    private final FileReadTool readTool;
    private final PromptProvider promptProvider;
    private final RoleGoalBackstory persona;
    private final CoworkLogger logger;
    private final com.embabel.agent.core.AgentPlatform agentPlatform;

    public ApiAgent(GrepTool grepTool, FileReadTool readTool, PromptProvider promptProvider, CoworkLogger logger, com.embabel.agent.core.AgentPlatform agentPlatform) {
        this.grepTool = grepTool;
        this.readTool = readTool;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.agentPlatform = agentPlatform;
        this.persona = promptProvider.getPersona("agents/api/persona.md");
    }

    // --- DTOs for LLM Extraction ---

    public record ParameterInfo(String name, String type, boolean required, String description) {}

    public record EndpointInfo(String method, String path, List<ParameterInfo> parameters, String returnType, String description, String sourceFile) {}

    public record ControllerBatch(String controllerName, String basePath, List<EndpointInfo> endpoints) {}

    public record ExtractedApiBatch(List<ControllerBatch> controllers) {}

    // --- States ---

    @State
    public record ContextPrimingState(ApiRequest request, String techStack, String domainContext) {}

    @State
    public record ApiDiscoveryState(ApiRequest request, List<String> controllerFiles, String techStack) {}

    @State
    public record ApiExtractionState(ApiRequest request, List<ExtractedApiBatch> batches) {}

    // --- Actions ---

    @Action(description = "Stage 0: Context Priming via ArchitectureAgent.")
    public ContextPrimingState prepareContext(ApiRequest request) {
        logger.info("ApiAgent", "Stage 0: Priming context (checking for existing architecture info)...");
        String inputContext = request.context() != null ? request.context() : "";
        
        if (inputContext.contains("Architecture Summary") || inputContext.contains("ARCHITECTURE")) {
            logger.info("ApiAgent", "Architecture context already present, skipping redundant call.");
            // Try to extract tech stack from context if possible, or leave as Unknown
            String techStack = inputContext.contains("Stack:") ? inputContext.split("Stack:")[1].split("\n")[0].trim() : "Available in context";
            return new ContextPrimingState(request, techStack, inputContext);
        }

        logger.info("ApiAgent", "No architecture info in context, invoking ArchitectureAgent...");
        String techStack = "Unknown";
        StringBuilder primedContext = new StringBuilder();
        
        try {
            var archInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport.class);
            var archReport = archInvocation.invoke(new io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest(request.path(), "General analysis for API context priming"));
            
            techStack = archReport.technicalStack();
            primedContext.append("Auto-generated Architecture Context:\n");
            primedContext.append("Summary: ").append(archReport.summary()).append("\n");
            primedContext.append("Tech Stack: ").append(techStack).append("\n");
            
            if (archReport.modules() != null && !archReport.modules().isEmpty()) {
                primedContext.append("Key Modules:\n");
                for (var mod : archReport.modules()) {
                    primedContext.append("- ").append(mod.name()).append(": ").append(mod.responsibility()).append("\n");
                }
            }
            logger.info("ApiAgent", "Context successfully primed. Tech Stack: " + techStack);
        } catch (Exception e) {
            logger.info("ApiAgent", "Architecture priming failed, proceeding with original context. Error: " + e.getMessage());
        }
        
        return new ContextPrimingState(request, techStack, primedContext.toString());
    }

    @Action(description = "Stage 1: Discovering API controllers using primed context.")
    public ApiDiscoveryState discoverControllers(ContextPrimingState priming) {
        logger.info("ApiAgent", "Stage 1: Discovering controllers for stack: " + priming.techStack());
        
        List<String> rawControllerGrep = new ArrayList<>();
        String path = priming.request().path();

        // Java/Spring
        rawControllerGrep.addAll(grepTool.grep("@RestController", path));
        rawControllerGrep.addAll(grepTool.grep("@Controller", path));
        // Python/FastAPI/Flask
        rawControllerGrep.addAll(grepTool.grep("@app\\.", path));
        rawControllerGrep.addAll(grepTool.grep("@router\\.", path));
        // Node/Express
        rawControllerGrep.addAll(grepTool.grep("router\\.(get|post|put|delete)\\(", path));

        List<String> controllerFiles = extractUniqueFiles(rawControllerGrep).stream()
                .filter(this::isValidSourceFile)
                .collect(Collectors.toList());

        logger.info("ApiAgent", "Found " + controllerFiles.size() + " potential controller files.");
        
        return new ApiDiscoveryState(priming.request(), controllerFiles, priming.techStack());
    }

    @Action(description = "Stage 2: Parsing controllers with LLM in parallel batches.")
    public ApiExtractionState extractEndpoints(ApiDiscoveryState state, Ai ai) {
        logger.info("ApiAgent", "Stage 2: Parsing controllers with LLM in parallel batches...");

        int batchSize = 5;
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < state.controllerFiles().size(); i += batchSize) {
            chunks.add(state.controllerFiles().subList(i, Math.min(i + batchSize, state.controllerFiles().size())));
        }

        List<ExtractedApiBatch> allParsedBatches = chunks.parallelStream().map(chunk -> {
            StringBuilder chunkContent = new StringBuilder();
            for (String file : chunk) {
                try {
                    String content = readTool.readFile(file).content();
                    chunkContent.append("--- File: ").append(file).append(" ---\n");
                    chunkContent.append(content).append("\n\n");
                } catch (Exception ignored) {}
            }

            String prompt = promptProvider.getPrompt("agents/api/extract-endpoints-batch.jinja", Map.of(
                "sourceCode", truncate(chunkContent.toString(), 60000)
            ));

            try {
                return ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                        .creating(ExtractedApiBatch.class)
                        .fromPrompt(prompt);
            } catch (Exception e) {
                logger.info("ApiAgent", "Batch parsing failed: " + e.getMessage());
                return new ExtractedApiBatch(new ArrayList<>());
            }
        }).collect(Collectors.toList());

        return new ApiExtractionState(state.request(), allParsedBatches);
    }

    @AchievesGoal(description = "Generate the final API documentation report.")
    @Action(description = "Stage 3: Synthesizing extraction results into a final report.")
    public ApiResult synthesizeApiReport(ApiExtractionState state, Ai ai) {
        logger.info("ApiAgent", "Stage 3: Synthesizing final API report...");

        StringBuilder batchesSummary = new StringBuilder();
        for (ExtractedApiBatch batch : state.batches()) {
            if (batch.controllers() != null) {
                for (var controller : batch.controllers()) {
                    batchesSummary.append("Controller: ").append(controller.controllerName()).append(" (Base: ").append(controller.basePath()).append(")\n");
                    if (controller.endpoints() != null) {
                        for (var ep : controller.endpoints()) {
                            batchesSummary.append("- ").append(ep.method()).append(" ").append(ep.path()).append(": ").append(ep.description()).append("\n");
                        }
                    }
                }
            }
        }

        String prompt = promptProvider.getPrompt("agents/api/synthesize-report.jinja", Map.of(
            "context", state.request().context(),
            "batchesSummary", truncate(batchesSummary.toString(), 40000)
        ));

        try {
            String report = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                    .withPromptContributor(persona)
                    .generateText(prompt);
            return new ApiResult(report, "Success");
        } catch (Exception e) {
            return new ApiResult("Report generation failed: " + e.getMessage(), "Error");
        }
    }

    // --- Helpers ---

    private List<String> extractUniqueFiles(List<String> grepLines) {
        return grepLines.stream().filter(line -> line.contains(":")).map(line -> line.split(":", 2)[0]).distinct().collect(Collectors.toList());
    }

    private boolean isValidSourceFile(String path) {
        String p = path.toLowerCase();
        return !p.contains("/target/") && !p.contains("/build/") && !p.contains("\\target\\") && !p.contains("\\build\\")
               && !p.contains("/test/") && !p.contains("architecturetest");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [TRUNCATED]";
    }
}
