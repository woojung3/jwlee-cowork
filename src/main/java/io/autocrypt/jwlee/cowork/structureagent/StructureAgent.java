package io.autocrypt.jwlee.cowork.structureagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.BashTool;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.structureagent.domain.FinalStructureReport;
import io.autocrypt.jwlee.cowork.structureagent.domain.StructureRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Agent(description = "Analyzes code structure, module dependencies, and architecture integrity.")
@Component
public class StructureAgent {

    private final BashTool bashTool;
    private final PromptProvider promptProvider;
    private final RoleGoalBackstory persona;
    private final CoworkLogger logger;
    private final com.embabel.agent.core.AgentPlatform agentPlatform;
    private final ObjectMapper objectMapper;

    public StructureAgent(BashTool bashTool, PromptProvider promptProvider, CoworkLogger logger, com.embabel.agent.core.AgentPlatform agentPlatform, ObjectMapper objectMapper) {
        this.bashTool = bashTool;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.agentPlatform = agentPlatform;
        this.objectMapper = objectMapper;
        this.persona = promptProvider.getPersona("agents/structure/persona.md");
    }

    // --- DTOs ---

    public record HubClassInfo(String className, String packagePath, int incomingDependencies, int outgoingDependencies, String role) {}
    public record DependencyViolation(String source, String target, String type, String description) {}
    public record StructureAnalysisResult(Map<String, String> modules, List<HubClassInfo> coreHubs, List<DependencyViolation> violations, Map<String, String> mermaidDiagrams) {}

    // --- States ---

    @State
    public record StructurePrimingState(StructureRequest request, String domainContext) {}

    @State
    public record RawDataExtractionState(StructureRequest request, String domainContext, String rawJson) {}

    @State
    public record InterpretationState(StructureRequest request, StructureAnalysisResult analysis) {}

    // --- Actions ---

    @Action(description = "Stage 0: Context Priming via specialized agents.")
    public StructurePrimingState prepareStructureContext(StructureRequest request) {
        logger.info("StructureAgent", "Stage 0: Priming context (checking for existing analysis in context)...");
        StringBuilder context = new StringBuilder();
        String inputContext = request.context() != null ? request.context() : "";

        // 1. Architecture Context (Check if already provided by orchestrator)
        if (inputContext.contains("ARCHITECTURE") || inputContext.contains("Architecture Summary")) {
            logger.info("StructureAgent", "Architecture info already present in context, skipping call.");
            context.append(inputContext).append("\n");
        } else {
            try {
                var archInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport.class);
                var result = archInvocation.invoke(new io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest(request.path(), "Priming for Structure Analysis"));
                context.append("### ARCHITECTURE\n").append(result.summary()).append("\n");
            } catch (Exception e) { logger.info("StructureAgent", "Arch priming failed: " + e.getMessage()); }
        }

        // 2. ERD Context
        if (inputContext.contains("CORE ENTITIES") || inputContext.contains("Data Model & ERD")) {
            logger.info("StructureAgent", "ERD info already present in context, skipping call.");
        } else {
            try {
                var erdInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.erdagent.domain.ErdResult.class);
                var result = erdInvocation.invoke(new io.autocrypt.jwlee.cowork.erdagent.domain.ErdRequest(request.path(), "Identify Core Entities"));
                context.append("### CORE ENTITIES\n").append(result.markdownContent()).append("\n");
            } catch (Exception e) { logger.info("StructureAgent", "ERD priming failed: " + e.getMessage()); }
        }

        // 3. API Context
        if (inputContext.contains("ENTRY POINTS") || inputContext.contains("API & Interfaces")) {
            logger.info("StructureAgent", "API info already present in context, skipping call.");
        } else {
            try {
                var apiInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.apiagent.domain.ApiResult.class);
                var result = apiInvocation.invoke(new io.autocrypt.jwlee.cowork.apiagent.domain.ApiRequest(request.path(), "Identify Entry Points"));
                context.append("### ENTRY POINTS\n").append(result.report()).append("\n");
            } catch (Exception e) { logger.info("StructureAgent", "API priming failed: " + e.getMessage()); }
        }

        return new StructurePrimingState(request, context.toString());
    }

    @Action(description = "Stage 1: Extracting raw dependency data via Python script.")
    public RawDataExtractionState extractRawDependencies(StructurePrimingState state) {
        logger.info("StructureAgent", "Stage 1: Running python dependency analyzer...");

        // Defensive check: Ensure python venv and script exist
        String venvPath = ".venv/bin/python";
        String scriptPath = "scripts/structure_analyzer.py";

        java.io.File venvFile = new java.io.File(venvPath);
        java.io.File scriptFile = new java.io.File(scriptPath);

        if (!venvFile.exists() || !scriptFile.exists()) {
            String errorMsg = String.format("Missing required components: venv=%b, script=%b. " +
                    "Make sure you have deployed the full package (including .venv and scripts folder).",
                    venvFile.exists(), scriptFile.exists());
            logger.info("StructureAgent", "ERROR: " + errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // Execute python script using venv
        String command = String.format("%s %s %s", venvPath, scriptPath, state.request().path());
        String bashJson = bashTool.execute(command);
        
        try {
            JsonNode root = objectMapper.readTree(bashJson);
            
            // Check if this is the BashTool wrapper format
            if (root.has("exitCode") && root.has("stdout")) {
                int exitCode = root.path("exitCode").asInt(-1);
                String stdout = root.path("stdout").asText("");
                String stderr = root.path("stderr").asText("");
                
                if (exitCode != 0) {
                    logger.info("StructureAgent", "Python script failed with exit code " + exitCode);
                    logger.info("StructureAgent", "STDERR: " + stderr);
                    throw new RuntimeException("Dependency analysis failed (exit " + exitCode + "): " + stderr);
                }
                bashJson = stdout; // Extracted the actual python output
            } else if (root.has("error")) {
                throw new RuntimeException("BashTool serialization error: " + bashJson);
            }
            // Else: Assume bashJson is ALREADY the pure python output (e.g. { "classes": {...} })
            // This happens if the tool framework unwraps JSON or if BashTool behavior varies.

            if (bashJson == null || bashJson.trim().isEmpty() || bashJson.equals("{}")) {
                throw new RuntimeException("Dependency analysis returned empty result. Analysis aborted to prevent hallucination.");
            }

            return new RawDataExtractionState(state.request(), state.domainContext(), bashJson);
        } catch (Exception e) {
            logger.info("StructureAgent", "Failed to process dependency data. Exception: " + e.getMessage());
            // logger.info("StructureAgent", "Raw returned string was: " + (bashJson.length() > 500 ? bashJson.substring(0, 500) + "..." : bashJson));
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("Parsing failed: " + e.getMessage());
        }
    }

    @Action(description = "Stage 2: Interpreting dependency graph and detecting violations.")
    public InterpretationState interpretStructure(RawDataExtractionState state, Ai ai) {
        logger.info("StructureAgent", "Stage 2: Interpreting graph data with LLM...");

        String prompt = promptProvider.getPrompt("agents/structure/interpret-graph.jinja", Map.of(
            "domainContext", state.domainContext(),
            "rawJson", truncate(state.rawJson(), 60000)
        ));

        StructureAnalysisResult result = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                .creating(StructureAnalysisResult.class)
                .fromPrompt(prompt);

        return new InterpretationState(state.request(), result);
    }

    @AchievesGoal(description = "Stage 3: Synthesizing the final Architecture Structure Report.")
    @Action(description = "Compiling the final structural integrity report.")
    public FinalStructureReport synthesizeStructureReport(InterpretationState state, Ai ai) {
        logger.info("StructureAgent", "Stage 3: Synthesizing final report...");

        String prompt = promptProvider.getPrompt("agents/structure/synthesize-report.jinja", Map.of(
            "analysis", state.analysis()
        ));

        String report = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                .withPromptContributor(persona)
                .generateText(prompt);

        return new FinalStructureReport(report, "Success");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [TRUNCATED]";
    }
}
