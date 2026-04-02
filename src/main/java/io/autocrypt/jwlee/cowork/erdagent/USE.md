# ErdAgent Usage Guide

`ErdAgent`는 Java/Kotlin 백엔드 프로젝트의 코드베이스를 스캔하여 **JPA 엔티티(`@Entity`)와 DDL 스크립트(`CREATE TABLE`)를 자동으로 찾아내고, 이를 기반으로 데이터베이스 스키마와 관계를 나타내는 Mermaid ERD 다이어그램을 생성**하는 전문가 에이전트입니다.

## 1. CLI Command: `erd-gen`

주어진 경로를 탐색하여 ERD를 추출합니다.

### 문법
```bash
erd-gen [--path <directory_path>] [--context "<context_hint>"] [-p] [-r]
```

### 파라미터
- `--path` (선택 사항, 기본값: `.`): 엔티티와 DDL을 탐색할 루트 경로.
- `--context` (선택 사항, 기본값: 일반적인 추출 지시): ERD 생성 시 특별히 주목해야 할 도메인이나 추가 지시사항. (예: "주문(Order)과 결제(Payment) 도메인 위주로 그려줘.")
- `-p` / `--show-prompts`: 에이전트의 프롬프트 로그 출력.
- `-r` / `--show-responses`: 에이전트의 LLM 응답 로그 출력.

### 실행 예시
```bash
# 현재 디렉토리 전체를 스캔하여 ERD 생성
erd-gen

# 특정 도메인(모듈) 경로만 스캔하여 ERD 생성
erd-gen --path "src/main/java/io/autocrypt/order" --context "주문 상태와 결제 상태의 관계에 집중하세요"

# 에이전트 분석 과정을 보며 실행
erd-gen -p -r
```

## 2. ArchitectureAgent 통합 (Subagent)

`ErdAgent`는 단독 실행뿐만 아니라 `ArchitectureAgent`의 하위 도구(Subagent)로 등록되어 있습니다. 
따라서 `arch-analyze` 명령어를 실행할 때, 프로젝트 내부에 JPA 엔티티나 DDL 파일이 발견되면 `ArchitectureAgent`가 스스로 `ErdAgent`를 도구로 호출하여 **최종 아키텍처 리포트에 ERD 다이어그램을 포함시켜 줍니다.**

## 3. 에이전트 동작 원리 (3-Stage Workflow)

1. **EntityDiscoveryState (탐색)**: `GrepTool`을 사용하여 `*.java`, `*.kt` 파일 내의 `@Entity`, `@Table` 패턴과 확장자에 상관없이 파일 내부에 `CREATE TABLE`, `ALTER TABLE` 구문이 있는 DDL 스크립트를 찾아냅니다. (이때 `target/`, `build/` 등은 무시합니다).
2. **ModelAnalysisState (추출)**: 식별된 엔티티 및 스키마 파일들의 실제 내용을 `FileReadTool`을 사용해 메모리로 불러오고 문자열로 병합합니다.
3. **generateErd (생성)**: Performant LLM을 호출하여 DDL의 물리적 구조와 JPA의 논리적 관계(`@ManyToOne` 등)를 교차 검증하고, 정확한 `Mermaid erDiagram` 문법과 한국어 해설을 생성하여 반환합니다.
