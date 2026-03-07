# 1부 상세 설계: AI Agent의 개념과 필요성

[Info]
이 문서는 발표 자료의 1부(4P~8P)에 들어갈 상세 내용과 스피치 가이드를 포함함

---

## 4P. AI 챗봇 vs AI Agent: "단순 응답"에서 "자율 해결"로
- **주요 내용**: 
  - **챗봇 (Passive)**: 사용자가 준 텍스트 내에서만 답변. 지식이 멈춰 있고 도구가 없음.
  - **에이전트 (Active)**: 스스로 도구(Tools)를 선택하고 실행함.
- **Embabel 포인트**: **Agentic RAG (Search as a Tool)**
  - 모든 문서를 프롬프트에 때려 넣는 방식(Stateless RAG)이 아니라, AI가 필요할 때만 검색 도구를 호출하여 '찾아보고 답변'함
- **문과용 코멘트**: "챗봇은 백과사전이고, 에이전트는 일을 시키면 자료를 찾아보고 보고서를 써오는 비서입니다."
- **이과용 코멘트**: "단순한 Prompt-in, Text-out이 아니라 도구 호출(Tool Calling)과 루프(Loop)가 포함된 자율 실행 단위입니다."

---

## 5P. AI Agent의 3요소: Brain, Hands, Memory
- **1. Brain (LLM - 추론)**: Embabel의 `Ai` 인터페이스. 상황을 판단하고 다음 할 일을 결정.
- **2. Hands (Tools - 실행)**: **DICE (Domain-Integrated Context Engineering)**
  - AI에게 '손'을 달아주는 법. `@LlmTool` 어노테이션을 통해 기존 Java 메서드를 AI가 호출 가능한 도구로 변신시킴.
- **3. Memory (Context - 맥락)**: **Blackboard (공유 메모리)**
  - 대화 이력뿐 아니라, 앞 단계에서 만든 '보고서 객체', '사용자 정보' 등을 기억하고 다음 단계에 전달.
- **비주얼**: 사람의 형상에 뇌(LLM), 손(DICE), 일기장(Blackboard)을 매핑한 인포그래픽.

---

## 6P. 왜 지금 AI Agent인가? (LLM의 한계 극복)
- **1. 할루시네이션(환각) 방지**: 
  - 비즈니스 로직을 AI가 '상상'하게 두지 않고, 검증된 Java 코드(`@LlmTool`)를 호출하게 하여 정확도 보장.
- **2. 컨텍스트 윈도우 한계 극복**:
  - 수천 장의 매뉴얼을 다 읽히는 대신, 필요한 부분만 발췌하는 **Agentic RAG** 사용.
- **3. 데이터 신뢰성 (Validation)**:
  - **JSR-380 (Bean Validation)**: AI가 내뱉은 데이터가 형식에 맞는지 자동으로 검사하고, 틀리면 스스로 수정(Self-Correction)하게 함.

---

## 7P. 실무 적용 시나리오 (Embabel 사례 중심)
- **시나리오 A: 기업용 스마트 이메일 (Persona)**
  - 감정적인 초안을 전문적인 이메일로 변환하고, 컴플라이언스 팀장(Persona)이 승인하는 다중 역할 수행.
- **시나리오 B: 전문가급 장애 원인 분석 (RCA Agent)**
  - 장애 상황이 발생하면 기술 문서와 이전 장애 이력을 뒤져서(pgvector/Lucene) 해결책과 근거(Citation)를 제시.
- **시나리오 C: 개인화된 고객 서비스 (Cat Lover 사례)**
  - 고객의 성향(Domain Object)에 맞춰서 외부 API에서 사실을 가져와(Service Tool) 최적의 제안을 구성.

---

## 8P. [Metaphor] 레스토랑의 총괄 지배인 (Floor Manager)
- **비유 구성**:
  - **고객 (User)**: "오늘 기분에 맞는 코스 요리 추천해줘"
  - **지배인 (Agent)**: 고객의 선호도를 기억하고(Blackboard), 요리사에게 주문을 넣고(Action), 재고가 있는지 확인(Tools)함.
  - **GOAP (Planning)**: "추천 -> 재고확인 -> 조리 -> 서빙"의 과정을 지배인이 상황에 맞춰 실시간으로 설계함.
- **결론**: 에이전트는 복잡한 비즈니스 프로세스(State Machine)를 인간 대신 관리해주는 **프로세스 코디네이터**임.
