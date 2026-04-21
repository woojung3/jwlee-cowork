# Bitbucket PR Review Agent 사용 가이드

이 에이전트는 Bitbucket PR의 Diff를 분석하여 스타일 가이드 준수 여부와 소프트웨어 아키텍처 설계를 검토합니다.

## 1. 주요 기능 및 개선 사항
- **계층적 번들링 (Hierarchical Bundling)**: 5,000 토큰 단위로 Diff를 묶어 분석 효율을 높였습니다.
- **분석 이원화 및 RAG 정책**:
    - **Style Review**: RAG를 사용하지 않고 오직 스타일 가이드 문서와 코드만으로 분석합니다.
    - **Architecture Review**: Confluence 아키텍처 가이드를 우선 참조하며, 추가 정보(표준/매뉴얼)가 필요한 경우에만 RAG를 탐색합니다.
- **가이드 필수화**: 스타일 및 아키텍처 가이드 URL은 필수 입력 사항입니다.
- **RAG 선택화**: 표준 문서 및 제품 매뉴얼 경로는 선택 사항이며, 비어있을 경우 RAG 기능을 사용하지 않습니다.

## 2. CLI 사용법

```bash
pr-review \
  --prId 2256 \
  --styleGuide "https://auto-jira.atlassian.net/wiki/x/EwBfN" \
  --archGuide "https://auto-jira.atlassian.net/wiki/x/iwJOaw" \
  --standards "output/00.materials/v2x-ee/tech" \
  --manuals "output/00.materials/v2x-ee/prod"
```

### 파라미터 상세
- `--prId`: (Mandatory) Bitbucket Pull Request ID
- `--styleGuide`: (Mandatory) 스타일 가이드 Confluence URL
- `--archGuide`: (Mandatory) 아키텍처 가이드 Confluence URL
- `--repo`: 저장소 슬러그 (기본값: `autocrypt/securityplatform`)
- `--standards`: (Optional) 표준 문서 폴더 경로. 비워두면 RAG 미사용.
- `--manuals`: (Optional) 제품 매뉴얼 폴더 경로. 비워두면 RAG 미사용.
- `-p`, `--showPrompts`: 프롬프트 출력
- `-r`, `--showResponses`: AI 응답 출력

## 3. 분석 기준
- **Style 분석**: 코딩 컨벤션, 가독성, 명명법.
- **Arch 분석**: SOLID 원칙, 계층 분리, 아키텍처 가이드라인 준수.

## 4. 출력 결과
- Bitbucket PR에 라인별 코멘트 및 전역 요약 리포트가 게시됩니다.
- CLI 화면에 통합 품질 점수가 출력됩니다.
