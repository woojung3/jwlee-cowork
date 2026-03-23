package io.autocrypt.jwlee.cowork.agents.anki;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.api.common.workflow.loop.TextFeedback;
import com.embabel.chat.AssistantMessage;

import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.tools.PdfParser;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Extracts key terms and concepts from documents to generate Anki CSV files with Korean definitions.")
@Component
public class AnkiAgent {

    private final PdfParser pdfParser;
    private final CoreFileTools fileTools;
    private final LocalRagTools localRagTools;

    public AnkiAgent(PdfParser pdfParser, CoreFileTools fileTools, LocalRagTools localRagTools) {
        this.pdfParser = pdfParser;
        this.fileTools = fileTools;
        this.localRagTools = localRagTools;
    }

    // --- DTOs ---

    public record AnkiRequest(Path filePath, String workspaceName, Path ragPath) {}

    public record AnkiOverview(String summary, List<String> initialTerms) {}

    public record RawTerms(List<String> terms) {}

    public record AnkiTerm(String term, String definition) {}

    public record AnkiCardList(List<AnkiTerm> cards) {}

    public record AnkiResult(String csvPath) {}

    @State
    public record ProcessingState(AnkiRequest request, String fullMarkdown, AnkiOverview overview, List<String> accumulatedTerms) {}

    // --- Actions ---

    /**
     * Phase 1: Initialize processing by parsing the document and extracting an overview.
     */
    @Action
    public ProcessingState initializeAndExtractOverview(AnkiRequest req, ActionContext ctx) throws IOException {
        String markdown;
        if (req.filePath().toString().toLowerCase().endsWith(".pdf")) {
            ctx.sendMessage(new AssistantMessage("Parsing PDF to Markdown..."));
            markdown = pdfParser.parsePdfToMarkdown(req.filePath().toFile(), Path.of("output/anki/temp-images"));
        } else {
            markdown = fileTools.readFile(req.filePath().toString()).content();
        }

        ctx.sendMessage(new AssistantMessage("Extracting document overview and initial key terms..."));
        
        String sample = markdown.length() > 5000 ? markdown.substring(0, 5000) : markdown;
        
        AnkiOverview overview = ctx.ai().withLlmByRole("simple")
                .creating(AnkiOverview.class)
                .fromPrompt(String.format("""
                        Analyze the following document and provide a high-level summary and a list of key technical terms or core concepts.
                        
                        # DOCUMENT CONTENT (PARTIAL)
                        %s
                        
                        # OUTPUT INSTRUCTIONS
                        1. Provide a summary for LLM reference.
                        2. Extract 5-10 initial key terms. 
                           - Format: "English Term (Korean Translation)" or just the term if it's naturally in one language.
                        """, sample));

        return new ProcessingState(req, markdown, overview, new ArrayList<>(overview.initialTerms()));
    }

    /**
     * Phase 2: Sequential Content Extraction.
     */
    @Action
    public ProcessingState extractSequentially(ProcessingState state, ActionContext ctx) {
        ctx.sendMessage(new AssistantMessage("Scanning full document for additional terms..."));
        
        List<String> chunks = splitText(state.fullMarkdown(), 4000);
        List<String> allTerms = new ArrayList<>(state.accumulatedTerms());

        for (int i = 0; i < chunks.size(); i++) {
            final int chunkIdx = i;
            String chunk = chunks.get(i);
            
            ctx.sendMessage(new AssistantMessage(String.format("Processing chunk %d/%d...", chunkIdx + 1, chunks.size())));
            
            RawTerms newTerms = ctx.ai().withLlmByRole("simple")
                    .creating(RawTerms.class)
                    .fromPrompt(String.format("""
                    # TASK
                    Extract key technical terms, acronyms, or core concepts from the provided document chunk that are NOT already in the existing list.
                    
                    # DOCUMENT OVERVIEW
                    %s
                    
                    # DOCUMENT CHUNK (%d/%d)
                    %s
                    
                    # EXISTING TERMS
                    %s
                    
                    # INSTRUCTIONS
                    1. Focus on terms essential for understanding the material.
                    2. Format: "English Term (Korean Translation)".
                    3. If no new important terms are found, return an empty list.
                    """, state.overview().summary(), chunkIdx + 1, chunks.size(), chunk, allTerms));
            
            if (newTerms.terms() != null) {
                for (String term : newTerms.terms()) {
                    if (!allTerms.contains(term)) {
                        allTerms.add(term);
                    }
                }
            }
        }

        return new ProcessingState(state.request(), state.fullMarkdown(), state.overview(), allTerms);
    }

    /**
     * Phase 3: Refinement & Validation.
     */
    @Action
    public RawTerms validateAndRefine(ProcessingState state, ActionContext ctx) {
        ctx.sendMessage(new AssistantMessage("Refining and validating the extracted term list..."));

        return RepeatUntilAcceptableBuilder
                .returning(RawTerms.class)
                .withMaxIterations(3)
                .withScoreThreshold(0.8)
                .repeating(loopCtx -> {
                    RawTerms lastResult = loopCtx.lastAttempt() != null ? (RawTerms) loopCtx.lastAttempt().getResult() : new RawTerms(state.accumulatedTerms());
                    String feedback = loopCtx.lastAttempt() != null ? loopCtx.lastAttempt().getFeedback().toString() : "Initial refinement.";

                    return ctx.ai().withLlmByRole("simple")
                            .creating(RawTerms.class)
                            .fromPrompt(String.format("""
                            # TASK
                            Refine the following list of terms extracted from a document.
                            1. Remove duplicates or near-duplicates.
                            2. Consolidate overlapping concepts.
                            3. Ensure the format "English Term (Korean Translation)" is consistent.
                            - <example>Confidentiality (기밀성)</example>
                            
                            # DOCUMENT SUMMARY
                            %s
                            
                            # CURRENT TERM LIST
                            %s
                            
                            # FEEDBACK FROM PREVIOUS ITERATION
                            %s
                            """, state.overview().summary(), lastResult.terms(), feedback));
                })
                .withEvaluator(loopCtx -> {
                    RawTerms result = (RawTerms) loopCtx.getResultToEvaluate();
                    return ctx.ai().withLlmByRole("simple")
                            .creating(TextFeedback.class)
                            .fromPrompt(String.format("""
                            Review the refined list of terms for Anki cards.
                            1. Are the terms core to the document's subject?
                            2. Are translations accurate and consistent?
                            
                            # DOCUMENT SUMMARY
                            %s
                            
                            # REFINED TERMS
                            %s
                            
                            Return a score (0.0 to 1.0) and specific feedback.
                            """, state.overview().summary(), result.terms()));
                })
                .build()
                .asSubProcess(ctx, RawTerms.class);
    }

    /**
     * Phase 4: Definition Finalization (Korean).
     */
    @Action
    public AnkiCardList finalizeDefinitions(RawTerms refinedTerms, ProcessingState state, ActionContext ctx) throws IOException {
        ctx.sendMessage(new AssistantMessage(String.format("Finalizing Korean definitions for %d terms using RAG...", refinedTerms.terms().size())));

        var searchOps = localRagTools.getOrOpenInstance(state.request().workspaceName(), state.request().ragPath());
        var rag = new JsonSafeToolishRag("doc_knowledge", "Knowledge base from the source document", searchOps);

        List<AnkiTerm> finalCards = new ArrayList<>();
        String fullTermList = String.join(", ", refinedTerms.terms());

        for (String term : refinedTerms.terms()) {
            ctx.sendMessage(new AssistantMessage("Defining: " + term));
            
            AnkiTerm definedTerm = ctx.ai().withLlmByRole("simple")
                    .withReference(rag)
                    .creating(AnkiTerm.class)
                    .fromPrompt(String.format("""
                    # TASK
                    Provide a clear, concise definition in **Korean** for the following term.
                    
                    # TERM TO DEFINE
                    %s
                    
                    # ALL TARGET TERMS (For terminology consistency)
                    %s
                    
                    # DOCUMENT OVERVIEW
                    %s
                    
                    # INSTRUCTIONS
                    1. The term should remain same (<example>English Term (Translated Korean)</example>)The definition MUST be in **Korean**.
                    2. Maintain consistency with the translations in the "ALL TARGET TERMS" list.
                    3. Use the 'doc_knowledge' tool to find specific context within the document.
                    4. Definition should be concise and suitable for an Anki flashcard.
                    """, term, fullTermList, state.overview().summary()));
            
            finalCards.add(definedTerm);
        }

        return new AnkiCardList(finalCards);
    }

    /**
     * Phase 5: Anki Conversion.
     */
    @AchievesGoal(description = "Anki CSV file with Korean definitions generated successfully")
    @Action
    public AnkiResult generateAnkiCsv(AnkiCardList cards, ProcessingState state, ActionContext ctx) throws IOException {
        ctx.sendMessage(new AssistantMessage("Generating final Anki CSV..."));

        StringBuilder csv = new StringBuilder();
        for (AnkiTerm card : cards.cards()) {
            String front = escapeCsv(card.term());
            String back = escapeCsv(card.definition());
            csv.append(front).append(",").append(back).append("\n");
        }

        String filename = String.format("anki/%s_cards_ko.csv", state.request().workspaceName());
        String savedPath = fileTools.saveGeneratedContent(filename, csv.toString());

        return new AnkiResult(savedPath);
    }

    // --- Helpers ---

    private List<String> splitText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }
        return chunks;
    }

    private String escapeCsv(String text) {
        if (text == null) return "";
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
