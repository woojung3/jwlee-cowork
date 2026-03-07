# AI Agent: 주간보고 자동화 PoC 발표 자료 구성안 (PLAN)

[Info]
이 문서는 Embabel Framework를 활용한 AI Agent 주간보고 자동 생성 PoC의 30분 발표 자료 구성을 정의함

- 대상: 문과 80% (기획/관리/영업), 이과 20% (SW 개발자)
- 목표: AI Agent의 실무적 유용성 전파 및 Embabel 기반 개발 방식 이해

---

## 1. 발표 개요 및 목차 (3P)
- **1P. 표지**: AI Agent와 함께하는 스마트한 업무 혁신 (Embabel PoC 사례 중심)
- **2P. 발표자 소개 및 문제 제기**: "우리는 왜 매주 금요일 퇴근 전 고통받는가?" (주간보고 작성의 비효율성)
  - [Guide] 세션 시작과 동시에 **주간보고 에이전트를 미리 구동**하세요. 1단계(데이터 수집/분석)는 약 1~2분 정도 소요되므로, 3부 데모 시점에 결과를 바로 보여줄 수 있도록 미리 'Generate' 버튼을 클릭해둡니다.
  - 실행 명령어: `cd sample-weekly-report && ../mvnw spring-boot:run`
- **3P. 목차**:
  - 1부: AI Agent, 단순 챗봇과 무엇이 다른가?
  - 2부: Embabel Framework - Java 개발자를 위한 AI 날개
  - 3부: [PoC 사례] 주간보고 자동 생성 시스템
  - 4부: AI-Native 개발의 미래 및 Q&A

---

## 2. 1부: AI Agent의 개념 (5P)
- **4P. AI 챗봇 vs AI Agent**: 시키는 일만 하는 '앵무새' vs 스스로 목표를 세우고 도구를 쓰는 '비서'
- **5P. AI Agent의 3요소**:
  - Brain (LLM: 추론 및 판단)
  - Hands (Tools: API 호출, DB 조회, 문서 파싱)
  - Memory (Context/Blackboard: 대화 맥락 및 상태 저장)
- **6P. 왜 지금 AI Agent인가?**: LLM의 한계(할루시네이션, 최신 정보 부재)를 외부 도구 연결로 극복
- **7P. 실무 적용 시나리오**: 이메일 정리, 일정 관리, 복잡한 데이터 분석 및 보고서 작성
- **8P. [문과용 비유]**: 레스토랑의 서빙 로봇(챗봇)과 총괄 지배인(Agent)의 차이 설명

---

## 3. 2부: Embabel Framework 핵심 원리 (7P)
- **9P. Embabel 소개**: Java 환경에서 AI Agent를 쉽고 안전하게 만드는 프레임워크
- **10P. GOAP (Goal-Oriented Action Planning)**:
  - 목적지만 알려주면 AI가 알아서 경로(Action)를 계산함
  - [개발자용]: 타입 기반의 자동 체이닝 원리 설명
- **11P. DICE (Domain-Integrated Context Engineering)**:
  - "너의 역할을 알아라" - 비즈니스 도메인과 AI 프롬프트의 결합
- **12P. HITL (Human-in-the-Loop)**:
  - AI가 마음대로 결정하게 두지 않음
  - 중요 단계에서 인간의 승인/피드백을 기다리는 구조
- **13P. [Working Code 1] Agent 선언**:
  ```java
  @Agent(description = "주간보고 전문가")
  public class WeeklyReportAgent {
      // @Action과 @AchievesGoal의 조합으로 흐름이 자동 생성됨
  }
  ```
- **14P. 프롬프트와 코드의 분리**: Jinja 템플릿을 통한 프롬프트 관리의 유연성
- **15P. 신뢰성 보장**: JSR-380(Bean Validation)을 활용한 AI 출력 데이터 검증

---

## 4. 3부: [PoC] 주간보고 자동 생성 시스템 (8P)
- **16P. 시스템 개요**: Confluence(OKR, 회의록) + Jira(이슈) 데이터를 AI가 분석하여 보고서 초안 생성
- **17P. 전체 아키텍처**: Spring Boot + Embabel + Google Gemini 기반 구성
- **18P. 워크플로우 (Step 1)**: 팀별 데이터 수집 및 '총무 AI'의 엄격한 발췌 과정
- **19P. 워크플로우 (Step 2)**: '연구소장 AI'의 전략적 분석 의견 도출
- **20P. 워크플로우 (Step 3 - HITL)**: 사용자의 팀별 분석 내용 검토 및 반려/수정 지시
- **21P. 워크플로우 (Step 4)**: 최종 HTML 포매팅 및 저장
- **22P. [Working Code 2] State-Machine**:
  ```java
  @State
  public interface Stage {} // AnalyzeTeams -> Finalize -> Finished 로 이어지는 상태 전이
  ```
- **23P. 실제 UI 화면**: 생성 프로세스 시각화 및 승인 폼 데모 (HTMX 활용)

---

## 5. 4부: 기대 효과 및 마무리 (4P)
- **24P. 도입 전/후 비교**: 2시간 걸리던 작성이 5분(검토 중심)으로 단축됨
- **25P. 데이터 기반의 정확성**: 누락되거나 왜곡된 보고 방지 (Jira 데이터 대조)
- **26P. 향후 확장성**: 사내 RAG(지식 베이스) 연결을 통한 답변 정확도 향상
- **27P. 맺음말**: AI Agent는 개발자만의 전유물이 아닌, 업무 방식의 새로운 표준임

---

## [참고] 코드 샘플 배치 전략
- 문과 대상: "프롬프트가 코드처럼 관리되어 AI가 똑똑하게 일한다"는 점을 강조
- 개발자 대상: 복잡한 `if-else`나 `switch-case` 없이 객체 지향적으로 Agent를 정의하는 방식(Embabel의 장점)을 노출함

[Warning]
발표 시 코드 블록은 폰트를 키우고 핵심 어노테이션(@Action, @State 등)에 강조색을 사용할 것
