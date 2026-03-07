# 2부 상세 설계: Embabel Framework 핵심 원리 (Code-First)

[Info]
이 문서는 9P~15P에 해당하는 Embabel의 기술적 구현 상세를 다룸.
모든 코드 블록에서 `import` 구문은 생략함.

---

## 9P. Persona: AI에게 인격과 역할을 부여하는 법 (Ch 0)
- **개념**: YAML 설정만으로 AI의 말투, 목표, 배경지식을 정의하고 Bean으로 주입함.

```yaml
# application.yml
embabel:
  identities:
    weekly-report:
      analyst:
        role: "연구소장"
        goal: "팀별 데이터를 분석하여 전략적 인사이트 도출"
        backstory: "30년 경력의 베테랑 엔지니어 출신. 꼼꼼하고 논리적임."
```

```java
// Configuration: YAML 설정을 RoleGoalBackstory Bean으로 매핑
@Bean
public RoleGoalBackstory analystPersona(AnalystProps props) {
    return new RoleGoalBackstory(props.role(), props.goal(), props.backstory());
}

// Usage: 에이전트 액션에서 특정 페르소나 호출
@Action
public String analyze(UserInput input, OperationContext ctx) {
    return ctx.ai()
        .withLlmByRole("analystPersona") // Bean 이름과 매핑
        .generateText("다음 데이터를 분석해줘: " + input.getContent());
}
```

---

## 10P. GOAP: 목적지만 정하면 길은 에이전트가 찾는다 (Ch 1)
- **개념**: 순차적 프로그래밍이 아닌, 입력/출력 타입을 기반으로 한 자동 워크플로우 설계.

```java
@Agent(description = "스토리 생성 및 분석 에이전트")
public class ContentAgent {

    // Action 1: UserInput을 받아 Story 객체 생성
    @Action
    public Story writeStory(UserInput input, OperationContext ctx) {
        return ctx.ai().withDefaultLlm()
                .withTemperature(0.8) // 창의성을 위해 높게 설정
                .createObject("주제: " + input.getContent(), Story.class);
    }

    // Action 2: Story 객체가 생기면 자동으로 실행됨 (Chaining)
    @AchievesGoal(description = "분석 완료")
    @Action
    public Analysis analyzeStory(Story story, OperationContext ctx) {
        return ctx.ai().withLlmByRole("analyst")
                .withTemperature(0.1) // 분석을 위해 낮게 설정
                .createObject("분석 대상: " + story.text(), Analysis.class);
    }
}
```

---

## 11P. Ai API: 타입 안전한 구조적 출력 (Ch 1)
- **개념**: 단순 텍스트가 아닌, Java 객체(POJO)를 직접 반환받으며 실패 시 자동 재시도.

```java
@Action
public Report generateReport(Data input, Ai ai) {
    // createObject: LLM 결과가 JSON 형식이 아니거나 스키마 위반 시 자동 Retry (최대 15회)
    return ai.withLlm(LlmOptions.withAutoLlm())
            .createObject("다음 데이터를 리포트 객체로 변환해줘: " + input, Report.class);
}

// Temperature 조절: 창의성 vs 결정론적 응답
ai.withTemperature(0.9); // 시, 소설, 아이디어 브레인스토밍
ai.withTemperature(0.0); // 데이터 추출, 논리 분석, 코드 생성
```

---

## 12P. Validation: AI의 실수를 코드로 검증하다 (Ch 1)
- **개념**: JSR-380(Jakarta Validation)을 활용해 AI의 출력을 강제하고 스스로 교정하게 함.

```java
public class UserProfile {
    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @Min(value = 19, message = "성인만 가입 가능합니다")
    private int age;

    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;
}

// 에이전트 동작 시:
// 1. LLM이 결과 생성 -> 2. Validation 체크 -> 3. 실패 시 에러 메시지를 포함해 재요청(Self-Correction)
```

---

## 13P. DICE: AI에게 비즈니스 도구를 쥐어주다 (Ch 2)
- **개념**: 기존 Java 서비스나 도메인 객체를 AI가 직접 호출할 수 있는 '손'으로 제공.

```java
@Component
public class CatFactService {
    @LlmTool(description = "고양이에 대한 흥미로운 사실을 외부 API에서 가져옵니다.")
    public List<String> getFacts(int count) { ... }
}

@Action
public Response entertain(CatLover lover, CatFactService service, Ai ai) {
    return ai.withDefaultLlm()
            .withToolObject(service) // 서비스 주입
            .withToolObject(lover)   // 도메인 객체 주입 (lover.getGreeting() 등 호출 가능)
            .createObject("고객에게 인사를 하고 고양이 사실 3개를 알려줘", Response.class);
}
```

---

## 14P. State & Looping: 복잡한 상태 머신 제어 (Ch 3)
- **개념**: `@State`와 `clearBlackboard`를 사용해 승인/반려 루프나 복잡한 분기 구현.

```java
@State
public record DraftingState(String draft) {
    
    @Action
    public ReviewOutcome review(Ai ai) {
        // ReviewPassed 또는 ReviewFailed 반환 (sealed interface 활용)
        return ai.createObject("원고 검토: " + draft, ReviewOutcome.class);
    }

    @Action(clearBlackboard = true) // 루프 백 시 이전 상태 소거
    public DraftingState retry(ReviewFailed failed) {
        return new DraftingState(failed.feedback()); // 수정 단계로 복귀
    }
}
```

---

## 15P. Agentic RAG: 똑똑한 지식 검색 (Ch 4)
- **개념**: 수천 페이지를 다 읽히지 않고, 필요할 때만 검색 도구(`ToolishRag`)를 사용하여 답변.

```java
// Config: 지식 베이스(Lucene) 설정
@Bean
public ToolishRag technicalDocs(SearchOperations luceneSearch) {
    return new ToolishRag("docs", "기술 문서 및 장애 이력", luceneSearch);
}

// Usage: 에이전트에서 참조
@Action
public Report analyzeIncident(String incident, Ai ai) {
    return ai.withReference(technicalDocs) // AI가 'docs'라는 도구를 사용해 스스로 검색
            .createObject("""
                'docs'에서 유사한 과거 사례를 찾아 분석해줘.
                반드시 원본 파일명(URI)을 인용(Citation)할 것.
                """, Report.class);
}
```
