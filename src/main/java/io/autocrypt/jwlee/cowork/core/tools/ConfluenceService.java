package io.autocrypt.jwlee.cowork.core.tools;

import java.util.List;

public interface ConfluenceService {
    
    record ConfluencePageInfo(String id, String title, String content) {
        public boolean isEmpty() {
            return content == null || content.isEmpty();
        }
    }

    record RagSearchRequest(
        String keyword,        // 핵심 검색어
        String excludeKeyword, // 제외할 단어 (선택)
        String fromDate,       // 검색 시작일 yyyy-MM-dd (선택)
        int limit              // 최대 반환 문서 개수
    ) {}

    ConfluencePageInfo getCurrentOkr();
    ConfluencePageInfo getCurrentWeeklyReport();
    String getPageStorage(String pageId);
    
    // RAG 전용 검색 함수
    List<ConfluencePageInfo> searchForRag(RagSearchRequest request);
}
