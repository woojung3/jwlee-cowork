package io.autocrypt.jwlee.cowork.deepinsightagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.apiagent.domain.ApiRequest;
import io.autocrypt.jwlee.cowork.apiagent.domain.ApiResult;
import io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport;
import io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest;
import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.deepinsightagent.domain.DeepInsightRequest;
import io.autocrypt.jwlee.cowork.deepinsightagent.domain.DeepInsightResult;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdRequest;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdResult;
import io.autocrypt.jwlee.cowork.opsagent.domain.OpsRequest;
import io.autocrypt.jwlee.cowork.opsagent.domain.OpsResult;
import io.autocrypt.jwlee.cowork.structureagent.domain.FinalStructureReport;
import io.autocrypt.jwlee.cowork.structureagent.domain.StructureRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Agent(description = "Integrated system analysis agent (Arch + ERD + API + Ops + Structure)")
@Component
public class DeepInsightAgent {

    private final AgentPlatform agentPlatform;
    private final PromptProvider promptProvider;
    private final RoleGoalBackstory persona;
    private final CoworkLogger logger;

    public DeepInsightAgent(AgentPlatform agentPlatform, PromptProvider promptProvider, CoworkLogger logger) {
        this.agentPlatform = agentPlatform;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.persona = promptProvider.getPersona("agents/deepinsight/persona.md");
    }

    @State
    public record GlobalAnalysisState(
        DeepInsightRequest request,
        String archReport,
        String erdReport,
        String apiReport,
        String opsReport,
        String structureReport,
        List<String> failedAgents
    ) {}

    @Action(description = "Stage 1: Execute individual agents sequentially and collect results.")
    public GlobalAnalysisState executeFullAnalysis(DeepInsightRequest request) {
        logger.info("DeepInsightAgent", "🚀 Starting full system analysis for: " + request.path());
        
        List<String> failedAgents = new ArrayList<>();
        String path = request.path();
        String userContext = request.context();

        // 1. ArchitectureAgent (The Blueprint)
        String archReport = "분석 실패";
        ArchitectureReport archObj = null;
        try {
            logger.info("DeepInsightAgent", "[1/5] Invoking ArchitectureAgent...");
            var invocation = AgentInvocation.create(agentPlatform, ArchitectureReport.class);
            archObj = invocation.invoke(new ArchitectureRequest(path, userContext));
            archReport = formatArchReport(archObj);
        } catch (Exception e) {
            logger.info("DeepInsightAgent", "ArchitectureAgent failed: " + e.getMessage());
            failedAgents.add("ArchitectureAgent");
        }

        // Build rolling context to avoid redundant calls in sub-agents
        StringBuilder rollingContext = new StringBuilder(userContext);
        rollingContext.append("\n\n[Architecture Summary]\n").append(archReport);

        // 2. ErdAgent
        String erdReport = "분석 실패";
        try {
            logger.info("DeepInsightAgent", "[2/5] Invoking ErdAgent...");
            var invocation = AgentInvocation.create(agentPlatform, ErdResult.class);
            var result = invocation.invoke(new ErdRequest(path, rollingContext.toString()));
            erdReport = result.markdownContent();
            rollingContext.append("\n\n[Data Model & ERD]\n").append(erdReport);
        } catch (Exception e) {
            logger.info("DeepInsightAgent", "ErdAgent failed: " + e.getMessage());
            failedAgents.add("ErdAgent");
        }

        // 3. ApiAgent
        String apiReport = "분석 실패";
        try {
            logger.info("DeepInsightAgent", "[3/5] Invoking ApiAgent...");
            var invocation = AgentInvocation.create(agentPlatform, ApiResult.class);
            var result = invocation.invoke(new ApiRequest(path, rollingContext.toString()));
            apiReport = result.report();
            rollingContext.append("\n\n[API & Interfaces]\n").append(apiReport);
        } catch (Exception e) {
            logger.info("DeepInsightAgent", "ApiAgent failed: " + e.getMessage());
            failedAgents.add("ApiAgent");
        }

        // 4. OpsAgent
        String opsReport = "분석 실패";
        try {
            logger.info("DeepInsightAgent", "[4/5] Invoking OpsAgent...");
            var invocation = AgentInvocation.create(agentPlatform, OpsResult.class);
            var result = invocation.invoke(new OpsRequest(path, rollingContext.toString()));
            opsReport = result.report();
            rollingContext.append("\n\n[Ops & Infrastructure]\n").append(opsReport);
        } catch (Exception e) {
            logger.info("DeepInsightAgent", "OpsAgent failed: " + e.getMessage());
            failedAgents.add("OpsAgent");
        }

        // 5. StructureAgent (Receives full context with Arch, ERD, API, Ops to prevent recursive calls)
        String structureReport = "분석 실패";
        try {
            logger.info("DeepInsightAgent", "[5/5] Invoking StructureAgent...");
            var invocation = AgentInvocation.create(agentPlatform, FinalStructureReport.class);
            var result = invocation.invoke(new StructureRequest(path, rollingContext.toString()));
            structureReport = result.report();
        } catch (Exception e) {
            logger.info("DeepInsightAgent", "StructureAgent failed: " + e.getMessage());
            failedAgents.add("StructureAgent");
        }

        return new GlobalAnalysisState(request, archReport, erdReport, apiReport, opsReport, structureReport, failedAgents);
    }

    @AchievesGoal(description = "Stage 2: Synthesize all agent reports into a Deep Insight report.")
    @Action(description = "Synthesizing final deep insight report.")
    public DeepInsightResult synthesizeDeepInsight(GlobalAnalysisState state, Ai ai) {
        logger.info("DeepInsightAgent", "Finalizing analysis and synthesizing deep insights...");

        // 1. Prepare context for LLM (Section 3 & 4 generation only)
        String prompt = promptProvider.getPrompt("agents/deepinsight/final-synthesis.jinja", Map.of(
            "path", state.request().path(),
            "archReport", state.archReport(),
            "erdReport", state.erdReport(),
            "apiReport", state.apiReport(),
            "opsReport", state.opsReport(),
            "structureReport", state.structureReport(),
            "status", state.failedAgents().isEmpty() ? "전체 성공" : "부분 성공 (일부 에이전트 실패)",
            "failedAgents", state.failedAgents()
        ));

        // 2. LLM generates Section 3 (Insights) and Section 4 (Recommendations)
        String masterInsights = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                .withPromptContributor(persona)
                .generateText(prompt);

        // 3. Assemble the full report manually to ensure NO information from sub-agents is lost
        StringBuilder fullReport = new StringBuilder();
        fullReport.append("# 통합 시스템 심층 분석 보고서 (Deep Insight Report)\n\n");
        fullReport.append("## 1. 개요 (Overview)\n");
        fullReport.append("시스템 전반의 아키텍처, 데이터 모델, API, 운영 환경 및 코드 구조를 통합 분석한 보고서입니다.\n\n---\n\n");
        
        fullReport.append("## 2. 영역별 상세 분석 (Detailed Domain Analysis)\n\n");
        fullReport.append("### 2.1 아키텍처 및 모듈 구조 (Architecture & Modules)\n").append(state.archReport()).append("\n\n");
        fullReport.append("### 2.2 데이터 모델 및 ERD (Data Model & ERD)\n").append(state.erdReport()).append("\n\n");
        fullReport.append("### 2.3 API 및 인터페이스 (API & Interfaces)\n").append(state.apiReport()).append("\n\n");
        fullReport.append("### 2.4 운영 환경 및 인프라 (Ops & Infrastructure)\n").append(state.opsReport()).append("\n\n");
        fullReport.append("### 2.5 코드 의존성 및 무결성 (Code Structure & Integrity)\n").append(state.structureReport()).append("\n\n");
        
        fullReport.append("---\n\n");
        fullReport.append(masterInsights).append("\n\n");
        
        fullReport.append("## 5. 분석 메타데이터 (Analysis Metadata)\n");
        fullReport.append("- **분석 대상 경로**: `").append(state.request().path()).append("`\n");
        fullReport.append("- **분석 상태**: ").append(state.failedAgents().isEmpty() ? "전체 성공" : "부분 성공 (일부 에이전트 실패)").append("\n");
        if (!state.failedAgents().isEmpty()) {
            fullReport.append("- **실패 항목**: ").append(String.join(", ", state.failedAgents())).append("\n");
        }
        fullReport.append("\n---\n*본 보고서는 DeepInsightAgent에 의해 자동 생성되었으며, 개별 전문 에이전트의 정밀 분석 결과를 바탕으로 작성되었습니다.*\n");

        // One-line summary extraction
        String summaryPrompt = "위 리포트를 바탕으로 전체 시스템의 아키텍처 성숙도와 특징을 한 문장으로 요약해줘. (한국어)\n\n리포트 내용:\n" + masterInsights;
        String summary = ai.withLlm(LlmOptions.withLlmForRole("normal").withMaxTokens(1024))
                .generateText(summaryPrompt);

        // Publish notification (Teams, etc.)
        ApplicationContextHolder.getPublisher().publishEvent(
                new NotificationEvent("🔍 DeepInsight 분석 완료", "프로젝트: " + state.request().path() + "\n\n요약: " + summary)
        );

        return new DeepInsightResult(fullReport.toString(), summary, state.failedAgents().isEmpty() ? "SUCCESS" : "PARTIAL_FAILURE");
    }

    private String formatArchReport(ArchitectureReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("#### 아키텍처 개요 (Summary)\n").append(report.summary()).append("\n\n");
        sb.append("#### 기술 스택 (Technical Stack)\n- ").append(report.technicalStack()).append("\n\n");
        sb.append("#### 아키텍처 패턴 (Pattern)\n- ").append(report.architecturePattern()).append("\n\n");
        
        if (report.modules() != null && !report.modules().isEmpty()) {
            sb.append("#### 주요 모듈 (Modules)\n");
            for (var mod : report.modules()) {
                sb.append("- **").append(mod.name()).append("**: ").append(mod.responsibility()).append("\n");
            }
            sb.append("\n");
        }
        
        if (report.recommendations() != null && !report.recommendations().isBlank()) {
            sb.append("#### 아키텍처 권고 사항 (Recommendations)\n").append(report.recommendations()).append("\n");
        }
        return sb.toString();
    }
}
