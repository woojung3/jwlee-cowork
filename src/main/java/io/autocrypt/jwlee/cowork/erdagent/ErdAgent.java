package io.autocrypt.jwlee.cowork.erdagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.FileReadTool;
import io.autocrypt.jwlee.cowork.core.tools.GrepTool;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdRequest;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Agent(description = "Analyzes DB Schemas using 100% LLM-based Map-Reduce Extraction.")
@Component
public class ErdAgent {

    private final GrepTool grepTool;
    private final FileReadTool readTool;
    private final PromptProvider promptProvider;
    private final RoleGoalBackstory persona;
    private final CoworkLogger logger;
    private final com.embabel.agent.core.AgentPlatform agentPlatform;

    public ErdAgent(GrepTool grepTool, FileReadTool readTool, PromptProvider promptProvider, CoworkLogger logger, com.embabel.agent.core.AgentPlatform agentPlatform) {
        this.grepTool = grepTool;
        this.readTool = readTool;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.agentPlatform = agentPlatform;
        this.persona = promptProvider.getPersona("agents/erd/persona.md");
    }

    // --- DTOs for LLM Extraction ---
    
    // Output format for Stage 2 (Map phase)
    public record ExtractedSchemaBatch(List<TableModel> tables, List<RelationModel> relations) {
        public record TableModel(String name, List<String> columns) {}
        public record RelationModel(String fromTable, String toTable, String type, String description) {}
    }

    // Output format for Stage 3 (Reduce phase)
    public record FinalModelData(List<ExtractedSchemaBatch.TableModel> tables, List<ExtractedSchemaBatch.RelationModel> relations, String explanation) {}

    // --- States ---

    @State
    public record ContextPrimingState(ErdRequest request) {}

    @State
    public record EntityDiscoveryState(ErdRequest request, List<String> entityFiles, List<String> ddlFiles) {}

    @State
    public record ParsedAnalysisState(ErdRequest request, List<ExtractedSchemaBatch> parsedBatches) {}

    // --- Actions ---

    @Action(description = "Stage 0: Context Priming via ArchitectureAgent.")
    public EntityDiscoveryState prepareContext(ErdRequest request) {
        String finalContext = request.context();
        
        if (finalContext == null || finalContext.trim().length() < 10) {
            logger.info("ErdAgent", "Stage 0: Context is empty or too short. Invoking ArchitectureAgent for structural priming...");
            try {
                var archInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport.class);
                var archReport = archInvocation.invoke(new io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest(request.path(), "General analysis for ERD context priming"));
                
                StringBuilder primedContext = new StringBuilder("Auto-generated Architecture Context:\n");
                primedContext.append("Summary: ").append(archReport.summary()).append("\n");
                primedContext.append("Tech Stack: ").append(archReport.technicalStack()).append("\n");
                
                if (archReport.modules() != null && !archReport.modules().isEmpty()) {
                    primedContext.append("Key Modules:\n");
                    for (var mod : archReport.modules()) {
                        primedContext.append("- ").append(mod.name()).append(": ").append(mod.responsibility()).append("\n");
                    }
                }
                finalContext = primedContext.toString();
                logger.info("ErdAgent", "Context successfully primed by ArchitectureAgent.");
            } catch (Exception e) {
                logger.info("ErdAgent", "Architecture priming failed, proceeding with original context. Error: " + e.getMessage());
            }
        }
        
        ErdRequest updatedRequest = new ErdRequest(request.path(), finalContext);
        return discoverFilesInternal(updatedRequest);
    }

    private EntityDiscoveryState discoverFilesInternal(ErdRequest request) {
        logger.info("ErdAgent", "Stage 1: Discovering files (excluding tests/builds)...");
        
        List<String> rawEntityGrep = new ArrayList<>();
        rawEntityGrep.addAll(grepTool.grep("@Entity", request.path()));
        rawEntityGrep.addAll(grepTool.grep("@Table", request.path()));
        
        List<String> entityFiles = extractUniqueFiles(rawEntityGrep).stream()
                .filter(f -> (f.endsWith(".java") || f.endsWith(".kt")) && isValidSourceFile(f))
                .collect(Collectors.toList());

        List<String> rawDdlGrep = new ArrayList<>();
        rawDdlGrep.addAll(grepTool.grep("CREATE TABLE", request.path()));
        rawDdlGrep.addAll(grepTool.grep("ALTER TABLE", request.path()));

        List<String> ddlFiles = extractUniqueFiles(rawDdlGrep).stream()
                .filter(f -> !f.endsWith(".java") && !f.endsWith(".kt") && !f.endsWith(".class") && isValidSourceFile(f))
                .collect(Collectors.toList());

        logger.info("ErdAgent", "Found " + entityFiles.size() + " entity files and " + ddlFiles.size() + " DDL files.");
        return new EntityDiscoveryState(request, entityFiles, ddlFiles);
    }

    @Action(description = "Stage 2: Parallel 100% LLM Extraction (Map Phase).")
    public ParsedAnalysisState parseWithLlm(EntityDiscoveryState state, Ai ai) {
        logger.info("ErdAgent", "Stage 2: Parsing all raw files with LLM in parallel batches...");

        List<String> allFiles = new ArrayList<>();
        allFiles.addAll(state.ddlFiles());
        allFiles.addAll(state.entityFiles());

        int batchSize = 10;
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < allFiles.size(); i += batchSize) {
            chunks.add(allFiles.subList(i, Math.min(i + batchSize, allFiles.size())));
        }

        List<ExtractedSchemaBatch> allParsedBatches = chunks.parallelStream().map(chunk -> {
            StringBuilder chunkContent = new StringBuilder();
            for (String file : chunk) {
                try {
                    String content = readTool.readFile(file).content();
                    chunkContent.append("--- File: ").append(file).append(" ---\n");
                    chunkContent.append(content).append("\n\n");
                } catch (Exception ignored) {}
            }

            String prompt = promptProvider.getPrompt("agents/erd/extract-schema-batch.jinja", Map.of(
                "sourceCode", truncate(chunkContent.toString(), 60000)
            ));

            try {
                // Use performant model to deeply analyze Java generic types (@OneToMany List<T>) and raw DDL constraints
                return ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(8192))
                        .creating(ExtractedSchemaBatch.class)
                        .fromPrompt(prompt);
            } catch (Exception e) {
                logger.info("ErdAgent", "Batch parsing failed: " + e.getMessage());
                return new ExtractedSchemaBatch(new ArrayList<>(), new ArrayList<>());
            }
        }).collect(Collectors.toList());

        return new ParsedAnalysisState(state.request(), allParsedBatches);
    }

    @AchievesGoal(description = "Generate the final Markdown ERD report.")
    @Action(description = "Stage 3: Final Synthesis and Java Rendering (Reduce Phase).")
    public ErdResult synthesizeAndGenerate(ParsedAnalysisState state, Ai ai) {
        logger.info("ErdAgent", "Stage 3: Synthesizing parsed batches and inferring global relationships...");

        StringBuilder tablesDump = new StringBuilder();
        StringBuilder relationsDump = new StringBuilder();
        
        for (ExtractedSchemaBatch batch : state.parsedBatches()) {
            if (batch.tables() != null) {
                for (var table : batch.tables()) {
                    tablesDump.append("[").append(table.name()).append("] ");
                    if (table.columns() != null) tablesDump.append(String.join(", ", table.columns()));
                    tablesDump.append("\n");
                }
            }
            if (batch.relations() != null) {
                for (var rel : batch.relations()) {
                    relationsDump.append(rel.fromTable()).append(" -> ").append(rel.toTable())
                                 .append(" (").append(rel.type()).append("): ").append(rel.description()).append("\n");
                }
            }
        }

        String prompt = promptProvider.getPrompt("agents/erd/synthesize-model.jinja", Map.of(
            "context", state.request().context(),
            "tables", truncate(tablesDump.toString(), 80000),
            "relations", truncate(relationsDump.toString(), 40000)
        ));

        FinalModelData finalData = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(32768))
                .withPromptContributor(persona)
                .creating(FinalModelData.class)
                .fromPrompt(prompt);

        String mermaidCode = renderMermaid(finalData);
        return new ErdResult(finalData.explanation() + "\n\n```mermaid\n" + mermaidCode + "\n```\n", "Success");
    }

    // --- Java Mermaid Renderer ---
    
    private String renderMermaid(FinalModelData data) {
        StringBuilder sb = new StringBuilder("erDiagram\n");
        
        // Find all tables that have at least one relationship
        java.util.Set<String> connectedTables = new java.util.HashSet<>();
        if (data.relations() != null) {
            for (var rel : data.relations()) {
                connectedTables.add(rel.fromTable());
                connectedTables.add(rel.toTable());
            }
        }

        if (data.tables() != null) {
            for (var table : data.tables()) {
                // Skip orphan tables from being drawn in the Mermaid graph
                if (!connectedTables.contains(table.name())) {
                    continue;
                }

                sb.append("    ").append(table.name()).append(" {\n");
                if (table.columns() != null) {
                    for (String col : table.columns()) {
                        String clean = col.trim().replaceAll("[^\\w\\s\\(\\)\\[\\]]", " ");
                        
                        // Fix PK/FK formatting for Mermaid compatibility
                        if (clean.toUpperCase().contains(" PK") && clean.toUpperCase().contains(" FK")) {
                            clean = clean.toUpperCase().replace(" PK", "").replace(" FK", "").trim() + " PK \"FK\"";
                        } else if (clean.toUpperCase().contains(" FK")) {
                            clean = clean.toUpperCase().replace(" FK", "").trim() + " \"FK\"";
                        }

                        if (!clean.contains(" ")) clean = "TYPE " + clean;
                        sb.append("        ").append(clean).append("\n");
                    }
                }
                sb.append("    }\n\n");
            }
        }
        if (data.relations() != null) {
            for (var rel : data.relations()) {
                String type = rel.type() != null ? rel.type() : "1:N";
                String arrow = switch (type.toUpperCase()) {
                    case "ONETOONE", "1:1" -> "||--||";
                    case "MANYTOMANY", "N:M" -> "}o--o{";
                    case "MANYTOONE", "N:1" -> "}o--||";
                    default -> "||--o{"; 
                };
                String desc = rel.description() != null ? rel.description().replace("\"", "'") : "relates_to";
                sb.append("    ").append(rel.fromTable()).append(" ").append(arrow).append(" ").append(rel.toTable())
                  .append(" : \"").append(desc).append("\"\n");
            }
        }
        return sb.toString();
    }

    // --- Helpers ---

    private List<String> extractUniqueFiles(List<String> grepLines) {
        return grepLines.stream().filter(line -> line.contains(":")).map(line -> line.split(":", 2)[0]).distinct().collect(Collectors.toList());
    }

    private boolean isValidSourceFile(String path) {
        String p = path.toLowerCase();
        // Ignore builds, targets, and specifically TEST folders which often contain mock entities
        return !p.contains("/target/") && !p.contains("/build/") && !p.contains("\\target\\") && !p.contains("\\build\\")
               && !p.contains("/test/") && !p.contains("architecturetest");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [TRUNCATED DUE TO SIZE LIMIT]";
    }
}
