# DeepInsightAgent 사용 가이드

`DeepInsightAgent`는 프로젝트의 아키텍처, 데이터 모델(ERD), API, 운영 인프라(Ops), 그리고 코드 구조 및 의존성 무결성(Structure)을 통합적으로 분석하여 심층적인 통찰을 제공하는 마스터 에이전트입니다.

## 1. 주요 기능
- **통합 분석**: 5개의 전문 에이전트(Architecture, Erd, Api, Ops, Structure)를 순차적으로 호출하여 데이터를 수집합니다.
- **심층 교차 계층 분석**: 각 영역 간의 불일치(예: API와 DB 스키마 간의 정합성)를 식별합니다.
- **종합 전략 제언**: 시스템의 성숙도를 진단하고 기술적 개선 방향을 제시합니다.

## 2. CLI 명령어 사용법

Spring Shell 환경에서 `deep-insight` 명령어를 사용합니다.

### 기본 사용법
현재 프로젝트 경로를 분석합니다.
```bash
deep-insight
```

### 특정 경로 분석
분석하고자 하는 프로젝트의 절대 경로 또는 상대 경로를 지정합니다.
```bash
deep-insight --path /path/to/your/project
```

### 컨텍스트 추가
분석에 참고할 추가 정보를 제공합니다.
```bash
deep-insight --context "이 프로젝트는 MSA 기반의 금융 시스템입니다."
```

### 디버그 옵션
- `-p` 또는 `--show-prompts`: LLM에 전달되는 프롬프트를 표시합니다.
- `-r` 또는 `--show-responses`: LLM의 원본 응답을 표시합니다.

```bash
deep-insight -p -r
```

## 3. 출력 결과
분석이 완료되면 다음과 같은 섹션이 포함된 Markdown 형식의 통합 보고서가 출력됩니다.

1. **개요 (Overview)**: 전체 시스템의 한 줄 요약 및 핵심 특징.
2. **개별 영역 분석 결과**: 아키텍처, 데이터 모델, API, 운영 환경, 코드 구조 요약.
3. **심층 교차 계층 분석**: 계층 간 불일치, 아키텍처 성숙도, 기술 부채 진단.
4. **종합 전략 제언**: 단기 및 장기 개선 방향.
5. **분석 프로세스 요약**: 성공 여부 및 개별 에이전트 상태.

## 4. 사전 요구 사항
- **Python 환경**: `StructureAgent`가 사용하는 `scripts/structure_analyzer.py`를 실행하기 위해 `.venv` 환경에 필요한 라이브러리(예: `networkx`)가 설치되어 있어야 합니다.
- **API Key**: `GEMINI_API_KEY` 환경 변수가 설정되어 있어야 합니다.

## 5. 분석 순서 및 전략
`DeepInsightAgent`는 다음과 같은 전략적인 순서로 분석을 수행합니다.
1. **ArchitectureAgent**: 시스템의 전체 지도를 먼저 그립니다.
2. **Erd/Api/OpsAgent**: 아키텍처 결과를 컨텍스트로 활용하여 각 전문 영역을 분석합니다.
3. **StructureAgent**: 앞선 모든 분석 결과를 바탕으로 심층적인 코드 의존성 및 무결성을 검증합니다.
4. **최종 합성**: 모든 데이터를 취합하여 최종 Deep Insight를 도출합니다.
