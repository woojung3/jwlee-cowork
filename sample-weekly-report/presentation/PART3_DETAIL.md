# 3부 상세 설계: [PoC] 주간보고 자동 생성 시스템

[Info]
이 문서는 16P~23P에 해당하는 주간보고 PoC의 실제 구현과 워크플로우를 다룸.
모든 코드 블록에서 `import` 구문은 생략함.

---

## 16P~17P. 시스템 아키텍처 및 데이터 흐름
- **핵심 목표**: 흩어진 데이터(Confluence OKR/회의록 + Jira 이슈)를 하나로 모아 인사이트를 추출.
- **기술 스택**: Spring Boot 3 + Embabel + Google Gemini + HTMX.
- **데이터 흐름**: 
  1. Confluence REST API -> JSoup 파싱 (Raw 데이터)
  2. Jira JQL -> 이슈 리스트 수집
  3. **WeeklyReportAgent** -> 단계별 분석 및 승인 절차 수행

---

## 18P. [Step 1] Collector AI: 엄격한 데이터 발췌
- **코드 포인트**: 각 팀별로 데이터를 독립적으로 병렬 처리하며, 단순 요약에 집중하는 '행정 총무' 페르소나 사용.

```java
// WeeklyReportAgent.java 의 일부
@Action
public AnalyzeTeamsState start(RawWeeklyData rawData, JiraIssueList jiraIssueList, Ai ai, ActionContext ctx) {
    List<String> targetTeams = List.of("EE팀", "BE팀", "PKI팀", "PnC팀", "FE팀", "Engineering팀");
    
    List<TeamAnalysis> collectedData = targetTeams.parallelStream().map(team -> {
        // Collector AI: 불필요한 추측 없이 데이터에서 '해당 팀' 내용만 발췌
        String collectPrompt = String.format("당신은 [%s] 행정 총무입니다. 제공된 데이터에서 [%s] 내용만 추출하세요.", team, team);

        TeamSummary summary = ai.withLlmByRole("simple") // 저비용/고속 모델(Gemini Flash-Lite) 사용
                .withPromptContributor(collectorPersona)
                .createObject(collectPrompt, TeamSummary.class);

        return new TeamAnalysis(team, summary.currentOkr(), summary.currentMeetingIssues(), jiraIssues, "");
    }).toList();
    
    // 다음 단계(분석)로 전이
    return new AnalyzeTeamsState(rawData, jiraIssueList, collectedData, analystPersona);
}
```

---

## 19P. [Step 2] Analyst AI: 전략적 성과 진단
- **코드 포인트**: 수집된 데이터를 바탕으로 연구소장 페르소나(`performant` 모델)가 OKR 기여도를 평가.

```java
private List<TeamAnalysis> evaluateTeams(List<TeamAnalysis> extractedData, Ai ai) {
    String prompt = """
        연구소장으로서 각 팀의 주간 성과를 진단하세요.
        1. 전략적 정합성: 팀의 활동이 OKR 달성에 기여하는가?
        2. 실행력 및 병목: 지연되는 과제의 원인이 무엇인가?
        """;

    TeamOpinionList opinionList = ai.withLlmByRole("performant") // 고성능 모델 사용
            .withPromptContributor(analystPersona)
            .createObject(prompt, TeamOpinionList.class);

    // AI 의견을 각 팀 분석 데이터에 매핑
    return mapOpinionsToTeams(extractedData, opinionList);
}
```

---

## 20P. [Step 3] HITL: 인간의 개입과 승인 프로세스
- **코드 포인트**: AI가 마음대로 보고서를 완성하지 않고, `WaitFor`를 통해 사용자의 피드백을 기다림.

```java
@State
public record AnalyzeTeamsState(RawWeeklyData rawData, List<TeamAnalysis> analyses) implements Stage {
    
    @Action
    public HumanFeedback waitForApproval() {
        // 이 시점에 에이전트 프로세스는 'WAITING' 상태로 일시정지됨
        // 사용자가 UI(HTMX 폼)를 통해 데이터를 입력할 때까지 대기
        return WaitFor.formSubmission("분석 내용을 검토해주세요.", HumanFeedback.class);
    }

    @Action(clearBlackboard = true) // 피드백 반영 시 이전 맥락을 청소하여 혼선 방지
    public Stage processFeedback(HumanFeedback feedback, Ai ai) {
        if (feedback.approved()) {
            return new FinalizeReportState(...); // 승인 시 최종 생성 단계로
        }
        // 반려 시 사용자의 코멘트를 반영하여 재분석 수행
        return new AnalyzeTeamsState(..., evaluateTeams(analyses, feedback.comments(), ai));
    }
}
```

---

## 21P. [Step 4] 최종 보고서 생성 (HTML 포매팅)
- **코드 포인트**: 여러 팀의 중복 데이터를 통합하고 조직 구조에 맞춰 HTML로 구조화.

```java
@State
public record FinalizeReportState(FinalWeeklyReport report) implements Stage {
    
    @Action
    public Stage finalize(HumanFeedback feedback, Ai ai) {
        if (feedback.approved()) {
            return new FinishedState(report);
        }
        // 마지막 순간까지 사용자의 수정을 수용하는 유연성
        FinalWeeklyReport revised = ai.withLlmByRole("normal")
                .createObject("피드백을 반영해 HTML 수정: " + feedback.comments(), FinalWeeklyReport.class);
        return new FinalizeReportState(revised);
    }
}
```

---

## 22P. State-Machine 구조의 이점
- **선언적 워크플로우**: 복잡한 `if/else` 대신 `State` 레코드의 반환값에 따라 흐름이 결정됨.
- **데이터 격리**: `@State` 전환 시 이전 상태의 데이터가 보이지 않게 하여 LLM의 혼란을 방지(Context Pruning).
- **재시작 가능성**: 각 상태가 데이터와 함께 저장되어 있어, 서버가 재시작되어도 중단된 시점부터 재개 가능.

---

## 23P. UI/UX: 사용자와 AI의 협업 인터페이스
- **HTMX 연동**: `every 2s` 폴링을 통해 에이전트의 현재 상태(`ANALYZE`, `WAIT_HITL_1` 등)를 실시간 반영.
- **Mermaid Graph**: 에이전트의 내부 GOAP 상태를 시각화하여 사용자가 "현재 무엇을 기다리는지" 알게 함.
- **결과**: AI는 '초안 작성자', 인간은 '최종 승인자'로서의 명확한 역할 분담.
