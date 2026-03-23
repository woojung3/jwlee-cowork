package io.autocrypt.jwlee.cowork.agents.presales;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.api.common.workflow.loop.TextFeedback;

import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;

@Agent(description = "Presales Engineering Agent for requirement analysis and gap assessment")
@Component
public class PresalesAgent {

    private final LocalRagTools localRagTools;

    public PresalesAgent(LocalRagTools localRagTools) {
        this.localRagTools = localRagTools;
    }

    public record RequirementRequest(String sourceContent, Path techRagPath) {}

    public record GapAnalysisRequest(String crsContent, String originalLanguage, Path productRagPath) {}

    public record AnalysisResult(String gapAnalysis, String questions, String finalReport) {}

    /**
     * Phase 1: Refine customer inquiry (email, chat, transcript) into a technical CRS using technical reference RAG.
     */
    @AchievesGoal(description = "Refined CRS in Markdown")
    @Action
    public String refineRequirements(RequirementRequest req, ActionContext ctx) throws IOException {
        var techSearch = localRagTools.getOrOpenInstance("tech-ref", req.techRagPath());
        var techRag = new JsonSafeToolishRag("tech_knowledge", "Standard technical specifications and industry knowledge", techSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(techRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

        // 1. Gatherer-Critic Loop to collect sufficient technical context
        String techContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(3)
                .withScoreThreshold(0.7)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";
                    
                    String prompt = String.format("""
                        # TASK
                        Search 'tech_knowledge' to find technical specifications and industry standards related to the customer inquiry.
                        
                        # OBJECTIVE
                        Analyze the provided inquiry (email, chat log, or meeting transcript) and gather specific technical protocols, security standards, and terms (e.g., IEEE 1609.2, V2X).
                        Accumulate knowledge from each iteration. Do NOT discard previous findings.
                        
                        # CUSTOMER INQUIRY
                        %s
                        
                        # PREVIOUS FINDINGS
                        %s
                        
                        # CRITIC FEEDBACK
                        %s
                        
                        # OUTPUT
                        Return a comprehensive, aggregated technical summary including both previous findings and new discoveries.
                        """, req.sourceContent(), lastFindings, feedback);
                    
                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = String.format("""
                        Review the gathered technical context against the customer inquiry.
                        Is the information sufficient to draft a detailed Customer Requirements Specification (CRS)?
                        
                        # Customer Inquiry:
                        %s
                        
                        # Gathered Context:
                        %s
                        
                        Return a score and specific feedback on what technical details are still missing.
                        """, req.sourceContent(), loopCtx.getResultToEvaluate());
                    
                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Normal AI Worker drafts the final CRS
        String finalPrompt = String.format("""
            You are a Senior Solutions Architect. Using the provided technical context, refine the customer inquiry into a detailed Customer Requirements Specification (CRS) in Markdown format.
            
            # Customer Inquiry:
            %s
            
            # Technical Context:
            %s
            
            # Instructions:
            1. Structure the CRS clearly with sections: Introduction, Functional Requirements, Non-Functional Requirements, and Technical Constraints.
            2. Use industry-standard terminology found in the context.
            3. Do NOT include any analysis of product capabilities or gaps.
            4. Output ONLY the Markdown content.
            """, req.sourceContent(), techContext);

        return normalAi.generateText(finalPrompt);
    }

    /**
     * Phase 2: Perform gap analysis and generate an internal technical review report.
     */
    @AchievesGoal(description = "Internal Technical Review Report completed")
    @Action
    public AnalysisResult analyzeGapAndFinalize(GapAnalysisRequest req, ActionContext ctx) throws IOException {
        var productSearch = localRagTools.getOrOpenInstance("product-spec", req.productRagPath());
        var productRag = new JsonSafeToolishRag("product_knowledge", "Internal product features, roadmap, and technical specifications", productSearch);

        var simpleAi = ctx.ai().withLlmByRole("simple").withReference(productRag);
        var normalAi = ctx.ai().withLlmByRole("normal");

        // 1. Gatherer-Critic Loop to collect product capability information
        String productContext = RepeatUntilAcceptableBuilder
                .returning(String.class)
                .withMaxIterations(3)
                .withScoreThreshold(0.7)
                .repeating(loopCtx -> {
                    var lastAttempt = loopCtx.lastAttempt();
                    String lastFindings = lastAttempt != null ? lastAttempt.getResult() : "No previous findings.";
                    String feedback = lastAttempt != null ? lastAttempt.getFeedback().toString() : "Initial search.";

                    String prompt = String.format("""
                        # TASK
                        Search 'product_knowledge' to find internal product capabilities matching the CRS.
                        
                        # OBJECTIVE
                        You must accumulate knowledge. Do NOT discard previous findings.
                        Address the critic's feedback to investigate specific gaps or features more deeply.
                        
                        # CRS
                        %s
                        
                        # PREVIOUS FINDINGS (Preserve and Expand this)
                        %s
                        
                        # CRITIC FEEDBACK
                        %s
                        
                        # OUTPUT
                        Return a comprehensive, aggregated capability summary including both previous findings and new discoveries.
                        """, req.crsContent(), lastFindings, feedback);

                    return simpleAi.generateText(prompt);
                })
                .withEvaluator(loopCtx -> {
                    String prompt = String.format("""
                        Review the gathered product context against the CRS.
                        Is the information sufficient to perform a detailed gap analysis (Supported/Partial/Unsupported) and effort estimation?
                        
                        # CRS:
                        %s
                        
                        # Gathered Product Context:
                        %s
                        """, req.crsContent(), loopCtx.getResultToEvaluate());

                    return normalAi.createObject(prompt, TextFeedback.class);
                })
                .build()
                .asSubProcess(ctx, String.class);

        // 2. Worker: Generate the final Internal Review Report
        String analysisPrompt = String.format("""
            Based on the product context and CRS, perform a deep technical gap analysis.
            For each requirement, provide:
            1. Support Status (Supported/Partially Supported/Unsupported)
            2. Detailed technical justification.
            3. Estimated development effort in Man-Months (M/M).
            
            # CRS:
            %s
            
            # Product Context:
            %s
            """, req.crsContent(), productContext);

        String rawAnalysis = normalAi.generateText(analysisPrompt);

        String finalReportPrompt = String.format("""
            You are the Head of Research reporting to the Business Unit (BU) Manager.
            Convert the following analysis into a formal **Internal Technical Review Report** in Korean.
            
            # SECTION STRUCTURE (REQUIRED):
            1. **개요**: 프로젝트의 핵심 목표와 고객의 핵심 요구사항 요약.
            2. **제품 현황 및 기능 비교**: Markdown 표 형식을 사용 (구분 | 요구 기능 | 지원 현황 | 분석 내용). 지원 현황은 '지원', '미지원', '부분 지원' 등으로 표기.
            3. **주요 개발 항목 및 예상 공수**: 각 개발 항목별 예상 공수(M/M)와 구체적인 기술적 배경 설명.
            4. **추가 확인 필요 사항**: 프로젝트 범위 확정 및 견적 산출을 위해 고객사 또는 사업부에 확인이 필요한 기술적/비즈니스적 리스트.
            
            # CONTEXT (Analysis Result):
            %s
            
            # INSTRUCTIONS:
            - Maintain a professional, objective, and analytical tone suitable for internal management.
            - Do NOT draft an email to the customer. Focus on technical assessment and internal decision-making data.
            - Ensure all M/M estimates from the context are clearly presented.
            """, rawAnalysis);

        String finalReport = normalAi.generateText(finalReportPrompt);

        String questionPrompt = "Extract ONLY the list of 'Additional Clarification Items' (추가 확인 필요 사항) from the report: \n\n" + finalReport;
        String questions = normalAi.generateText(questionPrompt);

        return new AnalysisResult(rawAnalysis, questions, finalReport);
    }
}
