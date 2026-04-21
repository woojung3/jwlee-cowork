package io.autocrypt.jwlee.cowork.bitbucketprapp;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.tools.ConfluenceService;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Agent(description = "Bitbucket PR의 변경사항을 분석하여 논리, 제품 스펙, 스타일 가이드, 테스트 충실도를 검사하는 에이전트")
@Component
public class BitbucketPrReviewAgent {

    private final BitbucketService bitbucketService;
    private final LocalRagTools localRagTools;
    private final CoreWorkspaceProvider workspaceProvider;
    private final ConfluenceService confluenceService;
    private final CoworkLogger logger;

    public BitbucketPrReviewAgent(BitbucketService bitbucketService,
                                  LocalRagTools localRagTools,
                                  CoreWorkspaceProvider workspaceProvider,
                                  ConfluenceService confluenceService,
                                  CoworkLogger logger) {
        this.bitbucketService = bitbucketService;
        this.localRagTools = localRagTools;
        this.workspaceProvider = workspaceProvider;
        this.confluenceService = confluenceService;
        this.logger = logger;
    }

    // DTOs
    public record PrReviewRequest(
            @NotBlank String repositorySlug,
            @Min(1) Long pullRequestId,
            String manualsDir,
            String standardsDir,
            @NotBlank String styleGuideUrl,
            @NotBlank String archGuideUrl
    ) {}

    public record CodeComment(
            String fileName,
            Integer lineNumber,
            String content,
            String type, // "GLOBAL" | "LINE"
            String severity, // "MUST_FIX" | "SHOULD_FIX" | "SUGGESTION"
            String criteriaId
    ) {}

    public record StyleAnalysisResult(
            List<CodeComment> comments,
            int score
    ) {}

    public record ArchAnalysisResult(
            List<CodeComment> comments,
            int score
    ) {}

    public record FinalReviewReport(
            int overallScore,
            String summary,
            List<CodeComment> globalComments,
            List<CodeComment> lineComments,
            int totalIssuesFound,
            List<String> truncatedFiles
    ) {}

    public record AllAnalysisResults(
            ReadyContext context,
            List<StyleAnalysisResult> styleResults,
            List<ArchAnalysisResult> archResults
    ) {}

    public record DiffSegment(
            String fileName,
            String diffContent,
            boolean isTruncated,
            int totalLines
    ) {}

    public record ConcatenatedDiff(
            List<String> fileNames,
            String combinedDiff,
            boolean isTruncated
    ) {}

    // States
    @State
    public record InitialState(PrReviewRequest request) {}

    @State
    public record DraftContext(
            PrReviewRequest request,
            List<DiffSegment> diffSegments,
            String manualsRagKey,
            String standardsRagKey,
            String styleGuideContent,
            String archGuideContent
    ) {}

    @State
    public record ReadyContext(
            PrReviewRequest request,
            List<ConcatenatedDiff> bundles,
            String manualsRagKey,
            String standardsRagKey,
            String styleGuideContent,
            String archGuideContent
    ) {}

    @Action
    public DraftContext prepareReviewContext(InitialState state) throws IOException {
        var req = state.request();
        logger.info("BitbucketPrReview", "Preparing review context for PR " + req.pullRequestId());

        // 1. RAG 인스턴스 초기화 (Optional)
        String manualsRagKey = null;
        String standardsRagKey = null;

        if (req.manualsDir() != null && !req.manualsDir().isBlank() && java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.manualsDir()))) {
            manualsRagKey = "manuals-" + workspaceProvider.toSlug(req.manualsDir());
            localRagTools.ingestDirectory(req.manualsDir(), manualsRagKey);
        }
        if (req.standardsDir() != null && !req.standardsDir().isBlank() && java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.standardsDir()))) {
            standardsRagKey = "standards-" + workspaceProvider.toSlug(req.standardsDir());
            localRagTools.ingestDirectory(req.standardsDir(), standardsRagKey);
        }

        // 2. 가이드 내용 가져오기 (Mandatory)
        String styleGuideContent = fetchGuideContent(req.styleGuideUrl(), "Style Guide");
        String archGuideContent = fetchGuideContent(req.archGuideUrl(), "Architecture Guide");

        // 3. Bitbucket PR Diff 가져오기 및 세그먼트 분리
        String workspace = "autocrypt"; 
        String repo = req.repositorySlug();
        if (req.repositorySlug().contains("/")) {
            String[] parts = req.repositorySlug().split("/");
            workspace = parts[0];
            repo = parts[1];
        }

        PullRequestData prData = bitbucketService.fetchPullRequest(workspace, repo, String.valueOf(req.pullRequestId()));
        List<DiffSegment> segments = splitDiff(prData.diff());

        return new DraftContext(req, segments, manualsRagKey, standardsRagKey, styleGuideContent, archGuideContent);
    }

    private String fetchGuideContent(String url, String label) {
        String pageId = extractPageId(url);
        if (pageId != null) {
            logger.info("BitbucketPrReview", "Fetching Confluence " + label + " for pageId: " + pageId);
            String content = confluenceService.getPageStorage(pageId);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return label + " 내용을 가져오지 못했습니다. (URL: " + url + ")";
    }

    @Action
    public ReadyContext concatenateSegments(DraftContext draft) throws IOException {
        logger.info("BitbucketPrReview", "Concatenating " + draft.diffSegments().size() + " segments into bundles hierarchically");
        
        // 1. 부모 디렉토리별로 그룹화
        Map<String, List<DiffSegment>> groupsByDir = draft.diffSegments().stream()
                .collect(Collectors.groupingBy(s -> {
                    int lastSlash = s.fileName().lastIndexOf('/');
                    return lastSlash == -1 ? "" : s.fileName().substring(0, lastSlash);
                }));

        // 2. 디렉토리 경로명으로 정렬
        List<String> sortedDirs = groupsByDir.keySet().stream().sorted().collect(Collectors.toList());

        List<ConcatenatedDiff> bundles = new ArrayList<>();
        List<DiffSegment> currentBundleSegments = new ArrayList<>();
        int currentChars = 0;
        int maxChars = 20000;

        for (String dir : sortedDirs) {
            List<DiffSegment> dirSegments = new ArrayList<>(groupsByDir.get(dir));
            dirSegments.sort(Comparator.comparing(DiffSegment::fileName));

            for (int i = 0; i < dirSegments.size(); i++) {
                DiffSegment segment = dirSegments.get(i);
                List<DiffSegment> unit = new ArrayList<>();
                unit.add(segment);

                if (i + 1 < dirSegments.size()) {
                    DiffSegment next = dirSegments.get(i + 1);
                    if (isPair(segment.fileName(), next.fileName())) {
                        unit.add(next);
                        i++;
                    }
                }

                int unitChars = unit.stream().mapToInt(s -> s.diffContent().length()).sum();

                if (currentChars + unitChars > maxChars && !currentBundleSegments.isEmpty()) {
                    bundles.add(createBundle(currentBundleSegments));
                    currentBundleSegments = new ArrayList<>();
                    currentChars = 0;
                }

                currentBundleSegments.addAll(unit);
                currentChars += unitChars;
            }
        }

        if (!currentBundleSegments.isEmpty()) {
            bundles.add(createBundle(currentBundleSegments));
        }

        return new ReadyContext(draft.request(), bundles, draft.manualsRagKey(), draft.standardsRagKey(), draft.styleGuideContent(), draft.archGuideContent());
    }

    private boolean isPair(String f1, String f2) {
        String base1 = f1.replaceAll("\\.(c|cpp|cxx|cc|h|hpp|hxx|hh)$", "");
        String base2 = f2.replaceAll("\\.(c|cpp|cxx|cc|h|hpp|hxx|hh)$", "");
        return base1.equals(base2);
    }

    private ConcatenatedDiff createBundle(List<DiffSegment> segments) {
        List<String> names = segments.stream().map(DiffSegment::fileName).collect(Collectors.toList());
        String combined = segments.stream().map(DiffSegment::diffContent).collect(Collectors.joining("\n\n"));
        boolean truncated = segments.stream().anyMatch(DiffSegment::isTruncated);
        return new ConcatenatedDiff(names, combined, truncated);
    }

    private String extractPageId(String url) {
        if (url == null) return null;
        Pattern pattern = Pattern.compile("/pages/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Short URL mapping
        if (url.contains("EwBfN")) return "878641171";
        if (url.contains("iwJOaw")) return "1800274571";
        
        return null;
    }

    @Action
    public AllAnalysisResults analyzeAllSegments(ReadyContext context, Ai ai) throws IOException {
        logger.info("BitbucketPrReview", "Starting analysis of " + context.bundles().size() + " bundles");
        List<StyleAnalysisResult> styleResults = new ArrayList<>();
        List<ArchAnalysisResult> archResults = new ArrayList<>();

        // RAG 도구 준비 (조건부)
        JsonSafeToolishRag manualsRag = null;
        JsonSafeToolishRag standardsRag = null;
        if (context.manualsRagKey() != null) {
            manualsRag = new JsonSafeToolishRag("manuals", "제품 매뉴얼 지식", localRagTools.getOrOpenInstance(context.manualsRagKey()));
        }
        if (context.standardsRagKey() != null) {
            standardsRag = new JsonSafeToolishRag("standards", "표준 문서 지식", localRagTools.getOrOpenInstance(context.standardsRagKey()));
        }

        for (ConcatenatedDiff bundle : context.bundles()) {
            logger.info("BitbucketPrReview", "Analyzing bundle: " + bundle.fileNames());
            
            // 1. Style Analysis (RAG 절대 사용 금지)
            StyleAnalysisResult styleResult = ai.withLlm(LlmOptions.withLlmForRole("normal")
                            .withoutThinking()
                            .withTemperature(0.1)
                            .withMaxTokens(65536))
                    .rendering("agents/bitbucketprapp/analyze-code")
                    .createObject(StyleAnalysisResult.class, Map.of(
                            "fileNames", bundle.fileNames(),
                            "diffContent", bundle.combinedDiff(),
                            "isTruncated", bundle.isTruncated(),
                            "analysis_type", "STYLE_GUIDE",
                            "guide_content", context.styleGuideContent()
                    ));
            styleResults.add(styleResult);

            // 2. Architecture Analysis (가이드 우선, 필요시 RAG 탐색)
            var archAi = ai.withLlm(LlmOptions.withLlmForRole("normal")
                            .withoutThinking()
                            .withTemperature(0.1)
                            .withMaxTokens(65536));
            
            if (standardsRag != null) archAi = archAi.withReference(standardsRag);
            if (manualsRag != null) archAi = archAi.withReference(manualsRag);

            ArchAnalysisResult archResult = archAi.rendering("agents/bitbucketprapp/analyze-code")
                    .createObject(ArchAnalysisResult.class, Map.of(
                            "fileNames", bundle.fileNames(),
                            "diffContent", bundle.combinedDiff(),
                            "isTruncated", bundle.isTruncated(),
                            "analysis_type", "ARCHITECTURE",
                            "guide_content", context.archGuideContent()
                    ));
            archResults.add(archResult);
        }
        return new AllAnalysisResults(context, styleResults, archResults);
    }


    @AchievesGoal(description = "모든 분석 결과를 수합하여 최종 리포트를 생성하고 코멘트를 게시함")
    @Action
    public FinalReviewReport synthesizeFinalReport(AllAnalysisResults allResults, ActionContext ctx, Ai ai) {
        var context = allResults.context();
        logger.info("BitbucketPrReview", "Synthesizing final report for PR " + context.request().pullRequestId());

        List<CodeComment> rawComments = new ArrayList<>();
        allResults.styleResults().stream().filter(r -> r.comments() != null).flatMap(r -> r.comments().stream()).forEach(rawComments::add);
        allResults.archResults().stream().filter(r -> r.comments() != null).flatMap(r -> r.comments().stream()).forEach(rawComments::add);

        // 1. 중복 제거 (내용과 위치가 완벽히 동일한 코멘트)
        Map<String, CodeComment> uniqueCommentsMap = new LinkedHashMap<>();
        for (CodeComment c : rawComments) {
            if (c == null) continue;
            String key = c.fileName() + ":" + c.lineNumber() + ":" + c.content().trim();
            uniqueCommentsMap.put(key, c);
        }
        List<CodeComment> allComments = new ArrayList<>(uniqueCommentsMap.values());

        // 2. 글로벌/라인 코멘트 분류
        List<CodeComment> globalComments = allComments.stream()
                .filter(c -> c.lineNumber() == null || "GLOBAL".equals(c.type()))
                .collect(Collectors.toList());

        List<CodeComment> lineComments = allComments.stream()
                .filter(c -> c.lineNumber() != null && !"GLOBAL".equals(c.type()))
                .sorted(Comparator.comparing(CodeComment::fileName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CodeComment::lineNumber, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        // 3. 점수 계산
        double avgStyle = allResults.styleResults().stream().mapToInt(StyleAnalysisResult::score).average().orElse(100);
        double avgArch = allResults.archResults().stream().mapToInt(ArchAnalysisResult::score).average().orElse(100);
        int totalScore = (int) ((avgStyle + avgArch) / 2);

        // 4. LLM을 사용한 전역 발견 사항(Key Findings) 수합 및 재작성 (더 강력한 performant 모델 사용)
        String synthesizedFindings = "";
        if (!globalComments.isEmpty()) {
            // LLM이 파일 정보를 인지할 수 있도록 [파일명]을 접두어로 붙여서 전달
            String rawFindingsText = globalComments.stream()
                    .map(c -> String.format("[File: %s] %s", c.fileName(), c.content()))
                    .collect(Collectors.joining("\n---\n"));
            
            synthesizedFindings = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking())
                    .rendering("agents/bitbucketprapp/synthesize-findings")
                    .generateText(Map.of("raw_findings", rawFindingsText));
        }

        List<String> truncatedFiles = context.bundles().stream()
                .filter(ConcatenatedDiff::isTruncated)
                .flatMap(b -> b.fileNames().stream())
                .distinct()
                .collect(Collectors.toList());

        // 5. 요약 리포트 생성
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("## 🤖 AI Code Review Summary\n");
        summaryBuilder.append(String.format("### 📊 Overall Score: **%d/100**\n", totalScore));
        summaryBuilder.append(String.format("- ✨ **Style**: %.1f/100\n", avgStyle));
        summaryBuilder.append(String.format("- 🏗️ **Architecture**: %.1f/100\n\n", avgArch));

        if (!synthesizedFindings.isBlank()) {
            summaryBuilder.append("### 💡 Key Findings\n");
            summaryBuilder.append(synthesizedFindings).append("\n\n");
        }

        if (!truncatedFiles.isEmpty()) {
            summaryBuilder.append("### ⚠️ Analysis Limitations\n");
            summaryBuilder.append("다음 파일들은 크기가 너무 커서 일부분만 분석되었습니다:\n");
            for (String f : truncatedFiles) {
                summaryBuilder.append(String.format("- `%s`\n", f));
            }
        }

        FinalReviewReport report = new FinalReviewReport(
                totalScore,
                summaryBuilder.toString(),
                globalComments,
                lineComments,
                allComments.size(),
                truncatedFiles
        );

        // 6. Bitbucket 게시
        String workspace = "autocrypt"; 
        String repo = context.request().repositorySlug();
        if (context.request().repositorySlug().contains("/")) {
            String[] parts = context.request().repositorySlug().split("/");
            workspace = parts[0];
            repo = parts[1];
        }

        bitbucketService.postGlobalComment(workspace, repo, String.valueOf(context.request().pullRequestId()), report.summary());

        for (CodeComment lc : lineComments) {
            if (lc.fileName() == null) continue;
            bitbucketService.postLineComment(workspace, repo, String.valueOf(context.request().pullRequestId()), 
                    lc.fileName(), lc.lineNumber() != null ? lc.lineNumber() : 1, lc.content());
        }

        for (ConcatenatedDiff bundle : context.bundles()) {
            if (bundle.isTruncated()) {
                for (String fileName : bundle.fileNames()) {
                    bitbucketService.postLineComment(workspace, repo, String.valueOf(context.request().pullRequestId()), 
                            fileName, 1, "⚠️ 이 파일은 길이가 길어 상단부만 분석되었습니다.");
                }
            }
        }

        return report;
    }

    private List<DiffSegment> splitDiff(String rawDiff) {
        List<DiffSegment> segments = new ArrayList<>();
        if (rawDiff == null || rawDiff.isBlank()) return segments;

        String[] parts = rawDiff.split("diff --git ");
        for (String part : parts) {
            if (part.isBlank()) continue;

            String[] lines = part.split("\n");
            String firstLine = lines[0];
            String fileName = "unknown";
            int bIdx = firstLine.indexOf(" b/");
            if (bIdx != -1) {
                fileName = firstLine.substring(bIdx + 3).trim();
            }

            int totalLines = lines.length;
            boolean isTruncated = false;
            String diffContent = "diff --git " + part;

            if (totalLines > 500) {
                isTruncated = true;
                diffContent = diffContent.lines().limit(500).collect(Collectors.joining("\n")) + "\n... (Truncated) ...";
            }

            segments.add(new DiffSegment(fileName, diffContent, isTruncated, totalLines));
        }
        return segments;
    }
}
