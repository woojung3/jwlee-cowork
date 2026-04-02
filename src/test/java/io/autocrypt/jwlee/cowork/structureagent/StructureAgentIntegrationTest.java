package io.autocrypt.jwlee.cowork.structureagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.BashTool;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.structureagent.domain.StructureRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.embabel.agent.core.AgentPlatform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StructureAgentIntegrationTest {

    @Test
    @DisplayName("Should successfully execute python script on real output/v2x-pki/x509 directory")
    void testRealPythonScriptExecution() {
        // Arrange: Use the REAL BashTool instead of a mock
        BashTool realBashTool = new BashTool();
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Mock only the LLM/Agent parts that we don't need for Stage 1
        PromptProvider promptProvider = mock(PromptProvider.class);
        CoworkLogger logger = mock(CoworkLogger.class);
        AgentPlatform agentPlatform = mock(AgentPlatform.class);

        StructureAgent structureAgent = new StructureAgent(realBashTool, promptProvider, logger, agentPlatform, objectMapper);

        // Target the actual directory
        StructureRequest request = new StructureRequest("output/v2x-pki/x509", "context");
        StructureAgent.StructurePrimingState primingState = new StructureAgent.StructurePrimingState(request, "context");

        // Act
        System.out.println("Starting real python script execution via BashTool...");
        StructureAgent.RawDataExtractionState result = structureAgent.extractRawDependencies(primingState);

        // Assert
        assertNotNull(result, "Result state should not be null");
        assertNotNull(result.rawJson(), "Extracted JSON should not be null");
        assertFalse(result.rawJson().isEmpty(), "Extracted JSON should not be empty");
        
        // Ensure it looks like the new 'Architecture Insight' summary output
        assertTrue(result.rawJson().contains("\"coreHubs\":"), "JSON should contain the 'coreHubs' key");
        assertTrue(result.rawJson().contains("\"summary\":"), "JSON should contain the 'summary' key");
        
        System.out.println("✅ Integration Test Successful (Summary Mode verified)!");
        System.out.println("Extracted JSON length: " + result.rawJson().length() + " characters.");
        System.out.println("Preview: " + result.rawJson().substring(0, Math.min(200, result.rawJson().length())) + "...");
    }
}
