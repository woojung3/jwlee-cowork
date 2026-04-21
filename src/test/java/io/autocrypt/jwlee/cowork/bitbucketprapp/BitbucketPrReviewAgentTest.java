package io.autocrypt.jwlee.cowork.bitbucketprapp;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import io.autocrypt.jwlee.cowork.core.tools.ConfluenceService;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class BitbucketPrReviewAgentTest {

    @Test
    void testConcatenateSegments_SmallFilesGrouping() throws IOException {
        ConfluenceService confluenceService = mock(ConfluenceService.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(null, null, null, confluenceService, logger);
        
        // 순서를 섞어서 입력
        List<BitbucketPrReviewAgent.DiffSegment> segments = List.of(
            new BitbucketPrReviewAgent.DiffSegment("src/util.c", "content-util", false, 20),
            new BitbucketPrReviewAgent.DiffSegment("src/main.h", "content-h", false, 5),
            new BitbucketPrReviewAgent.DiffSegment("src/main.c", "content-c", false, 10)
        );
        
        BitbucketPrReviewAgent.DraftContext draft = new BitbucketPrReviewAgent.DraftContext(
            new BitbucketPrReviewAgent.PrReviewRequest("repo", 1L, null, null, "style-url", "arch-url"),
            segments, "manuals-key", "standards-key", "style-guide", "arch-guide-content"
        );

        // Act
        BitbucketPrReviewAgent.ReadyContext ready = agent.concatenateSegments(draft);

        // Assert: 전체 크기가 작으므로 1개의 번들로 묶여야 함
        assertEquals(1, ready.bundles().size());
        BitbucketPrReviewAgent.ConcatenatedDiff bundle = ready.bundles().get(0);
        
        // 정렬 확인: main.c, main.h, util.c 순서여야 함
        assertEquals("src/main.c", bundle.fileNames().get(0));
        assertEquals("src/main.h", bundle.fileNames().get(1));
        assertEquals("src/util.c", bundle.fileNames().get(2));
        
        assertEquals("arch-guide-content", ready.archGuideContent());
    }

    @Test
    void testAnalyzeAllSegments_PromptEval() throws IOException {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();

        // Dependencies
        LocalRagTools localRagTools = mock(LocalRagTools.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        when(localRagTools.getOrOpenInstance(anyString())).thenReturn(mock(LuceneSearchOperations.class));

        // 1. Arrange: Expecting two responses (Style and Arch) for one bundle
        context.expectResponse(new BitbucketPrReviewAgent.StyleAnalysisResult(List.of(), 90));
        context.expectResponse(new BitbucketPrReviewAgent.ArchAnalysisResult(List.of(), 85));

        BitbucketPrReviewAgent.ReadyContext readyContext = new BitbucketPrReviewAgent.ReadyContext(
            new BitbucketPrReviewAgent.PrReviewRequest("repo", 1L, null, null, "style-url", "arch-url"),
            List.of(new BitbucketPrReviewAgent.ConcatenatedDiff(List.of("file.c"), "diff", false)),
            "m-key", "s-key", "style-content", "arch-content"
        );

        BitbucketPrReviewAgent agent = new BitbucketPrReviewAgent(null, localRagTools, null, null, logger);
        
        // 2. Act
        BitbucketPrReviewAgent.AllAnalysisResults results = agent.analyzeAllSegments(readyContext, context.ai());

        // 3. Assert
        assertEquals(1, results.styleResults().size());
        assertEquals(1, results.archResults().size());
        assertEquals(90, results.styleResults().get(0).score());
        assertEquals(85, results.archResults().get(0).score());

        // Verify Prompt Parameters
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(2, invocations.size());
        
        // First should be STYLE_GUIDE
        assertTrue(invocations.get(0).getPrompt().contains("STYLE_GUIDE"));
        // Second should be ARCHITECTURE
        assertTrue(invocations.get(1).getPrompt().contains("ARCHITECTURE"));
    }
}
