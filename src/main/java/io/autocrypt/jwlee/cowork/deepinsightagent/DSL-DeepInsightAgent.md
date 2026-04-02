# DSL-DeepInsightAgent.md

## 0. Header
이 DSL은 프로젝트의 전방위적 분석(아키텍처, DB, API, 인프라, 코드 구조)을 한 번에 수행하고 통합 리포트를 생성하는 `DeepInsightAgent`를 정의합니다.

## 1. Metadata
```yaml
agent:
  name: DeepInsightAgent
  description: "통합 시스템 분석 에이전트 (Arch + ERD + API + Ops + Structure)"
  timezone: "Asia/Seoul" # REQUIRED
  language: "Korean" # REQUIRED: 모든 리포트는 한국어로 작성합니다.
```

## 2. Dependencies
- `ArchitectureAgent` (기반 지식 생성)
- `ErdAgent` (데이터 모델 분석)
- `ApiAgent` (엔드포인트 분석)
- `OpsAgent` (운영 및 인프라 분석)
- `StructureAgent` (코드 의존성 및 무결성 분석)
- `PromptProvider` (통합 분석 프롬프트용)
- `CoworkLogger`
- `AgentPlatform` (에이전트 오케스트레이션용)

## 3. Domain Objects (DTOs)

### DeepInsightRequest
- `path`: String # 분석 대상 프로젝트 경로
- `context`: String # 사용자 추가 컨텍스트

### DeepInsightResult
- `fullReport`: String # 모든 에이전트 결과의 통합 Markdown
- `summary`: String # 전체 시스템의 한 줄 요약
- `status`: String # 완료/부분실패 여부

## 4. Workflow States

### State: GlobalAnalysisState
- `request`: DeepInsightRequest
- `archReport`: String # ArchitectureReport 요약
- `erdReport`: String # ErdResult 요약
- `apiReport`: String # ApiResult 요약
- `opsReport`: String # OpsResult 요약
- `structureReport`: String # FinalStructureReport 요약
- `failedAgents`: List<String>

## 5. Actions (Execution Strategy)

### Action: executeFullAnalysis (Stage 1 - Sequential Chaining)
- **Goal**: 최적의 순서로 개별 에이전트들을 실행하고 결과를 수집합니다.
- **순서 및 전략**:
  1. **ArchitectureAgent**: 가장 먼저 실행하여 프로젝트의 "지도"를 그립니다.
  2. **ErdAgent / ApiAgent / OpsAgent**: 위에서 얻은 결과를 `context`로 주입하여 중복 연산을 방지하며 실행합니다.
  3. **StructureAgent**: 앞선 모든 결과물(Arch, ERD, API)을 취합하여 가장 마지막에 심층 코드 분석을 수행합니다.
- **오류 처리**: 특정 에이전트 실패 시 해당 에이전트 결과에는 "분석 실패" 문구를 넣고 중단 없이 다음으로 진행합니다.

### Action: synthesizeDeepInsight (Stage 2 - Final Synthesis)
- **AchievesGoal**: 모든 결과를 결합하고 심층 교차 분석을 추가합니다.
- **Input**: `GlobalAnalysisState`
- **Output**: `DeepInsightResult`
- **Logic**: 
  1. 각 에이전트의 결과물을 섹션별로 결합합니다.
  2. 에이전트 간 결과의 모순점(예: API 문서에는 있으나 실제 코드/DB에 없는 필드 등)을 식별합니다.
  3. 전체적인 시스템의 기술적 부채와 아키텍처 성숙도를 총평합니다.
- **LLM Configuration**:
  - `role`: performant
  - `maxTokens`: 65536
  - `template`: `agents/deepinsight/final-synthesis.jinja`

## 6. Implementation Guidelines

### 6.1 Context Chaining (중복 방지)
- 후행 에이전트 호출 시, 후행 에이전트가 필요로 하는 선행 에이전트의 결과물 (예: `ArchitectureAgent`의 결과물(Summary, Tech Stack, Modules))을 `context` 파라미터에 명시적으로 주입하여, 개별 에이전트가 내부적으로 선행 에이전트를 다시 호출하지 않도록 제어합니다. 필요하다면 이 동작을 위한 옵션을 넣으세요.

### 6.2 Output Refinement
- 각 에이전트의 결과가 "보고서의 한 섹션"처럼 느껴지도록, 각 에이전트의 출력형식을 가공합니다. 현재 있는 내용을 큰 틀에서 유지하되, 형태만 "보고서의 한 섹션" 처럼 느껴지게 수정해주세요.

### 6.3 Deep Synthesis Focus
- 최종 리포트의 마지막 섹션인 **"시스템 총평 (Deep Insight)"**에서는 개별 에이전트가 단독으로는 발견할 수 없는 **계층 간(Cross-Layer) 불일치**와 **인프라-코드 간의 정합성** 등 심층 분석이 가능한 부분을 집중 분석합니다.
