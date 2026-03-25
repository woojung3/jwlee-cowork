package io.autocrypt.jwlee.cowork.web;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import io.autocrypt.jwlee.cowork.agents.presales.PresalesAgent;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PresalesWebService {

    private final AgentPlatform agentPlatform;
    private final LocalRagTools localRagTools;
    private final Ai ai;

    private final List<String> techSpecs = new ArrayList<>();
    private final List<String> productSpecs = new ArrayList<>();
    private String inquiryContent;
    private String inquiryFileName;

    public List<String> getTechSpecs() {
        return techSpecs;
    }

    public List<String> getProductSpecs() {
        return productSpecs;
    }

    public String getInquiryContent() {
        return inquiryContent;
    }

    public String getInquiryFileName() {
        return inquiryFileName;
    }

    private final Path tempDir;
    private final String techRagName;
    private final String productRagName;

    public PresalesWebService(AgentPlatform agentPlatform, LocalRagTools localRagTools, Ai ai) throws IOException {
        this.agentPlatform = agentPlatform;
        this.localRagTools = localRagTools;
        this.ai = ai;
        this.tempDir = Files.createTempDirectory("presales-web-");
        this.techRagName = "tech-" + UUID.randomUUID().toString().substring(0, 8);
        this.productRagName = "prod-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void addTechSpec(MultipartFile file) throws IOException {
        Path path = tempDir.resolve("tech-" + UUID.randomUUID() + "-" + file.getOriginalFilename());
        Files.copy(file.getInputStream(), path);
        localRagTools.ingestUrlToMemory(path.toString(), techRagName);
        techSpecs.add(file.getOriginalFilename());
    }

    public void addProductSpec(MultipartFile file) throws IOException {
        Path path = tempDir.resolve("prod-" + UUID.randomUUID() + "-" + file.getOriginalFilename());
        Files.copy(file.getInputStream(), path);
        localRagTools.ingestUrlToMemory(path.toString(), productRagName);
        productSpecs.add(file.getOriginalFilename());
    }

    public void setInquiry(MultipartFile file) throws IOException {
        this.inquiryContent = new String(file.getInputStream().readAllBytes());
        this.inquiryFileName = file.getOriginalFilename();
    }

    public PresalesAgent.AnalysisResult runAnalysis() throws Exception {
        // 1. Detect language
        String langPrompt = "Identify the language of the following text (e.g., 'English', 'Korean'). Output ONLY the language name: \n\n" + inquiryContent;
        String language = ai.withLlmByRole("simple").generateText(langPrompt).trim();

        // 2. Phase 1: Refine Requirements
        AgentProcess process1 = AgentInvocation
                .create(agentPlatform, String.class)
                .runAsync(new PresalesAgent.RequirementRequest(inquiryContent, techRagName))
                .join();
        
        String crs = process1.resultOfType(String.class);

        // 3. Phase 2: Gap Analysis & Finalization
        AgentProcess process2 = AgentInvocation
                .create(agentPlatform, PresalesAgent.AnalysisResult.class)
                .runAsync(new PresalesAgent.GapAnalysisRequest(crs, language, productRagName))
                .join();
        
        return process2.resultOfType(PresalesAgent.AnalysisResult.class);
    }
}
