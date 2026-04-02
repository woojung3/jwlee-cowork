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
        logger.info("StructureAgent", "Stage 0: Priming context from Architecture, ERD, and API agents...");
        StringBuilder context = new StringBuilder();

        // 1. Architecture Context
        try {
            var archInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport.class);
            var result = archInvocation.invoke(new io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest(request.path(), "Priming for Structure Analysis"));
            context.append("### ARCHITECTURE\n").append(result.summary()).append("\n");
        } catch (Exception e) { logger.info("StructureAgent", "Arch priming failed: " + e.getMessage()); }

        // 2. ERD Context
        try {
            var erdInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.erdagent.domain.ErdResult.class);
            var result = erdInvocation.invoke(new io.autocrypt.jwlee.cowork.erdagent.domain.ErdRequest(request.path(), "Identify Core Entities"));
            context.append("### CORE ENTITIES\n").append(result.markdownContent()).append("\n");
        } catch (Exception e) { logger.info("StructureAgent", "ERD priming failed: " + e.getMessage()); }

        // 3. API Context
        try {
            var apiInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.apiagent.domain.ApiResult.class);
            var result = apiInvocation.invoke(new io.autocrypt.jwlee.cowork.apiagent.domain.ApiRequest(request.path(), "Identify Entry Points"));
            context.append("### ENTRY POINTS\n").append(result.report()).append("\n");
        } catch (Exception e) { logger.info("StructureAgent", "API priming failed: " + e.getMessage()); }

        return new StructurePrimingState(request, context.toString());
    }

    @Action(description = "Stage 1: Extracting raw dependency data via Python script.")
    public RawDataExtractionState extractRawDependencies(StructurePrimingState state) {
        logger.info("StructureAgent", "Stage 1: Running python dependency analyzer...");
        
        // Execute python script using venv
        String command = String.format(".venv/bin/python scripts/structure_analyzer.py %s", state.request().path());
        String bashJson = bashTool.execute(command);
        
        try {
            JsonNode root = objectMapper.readTree(bashJson);
            int exitCode = root.get("exitCode").asInt();
            String stdout = root.get("stdout").asText();
            
            if (exitCode != 0) {
                logger.info("StructureAgent", "Python script failed: " + stdout);
                return new RawDataExtractionState(state.request(), state.domainContext(), "{\"error\": \"Extraction failed\"}");
            }

            return new RawDataExtractionState(state.request(), state.domainContext(), stdout);
        } catch (Exception e) {
            logger.info("StructureAgent", "Failed to parse BashTool output: " + e.getMessage());
            return new RawDataExtractionState(state.request(), state.domainContext(), "{\"error\": \"Parsing failed\"}");
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
