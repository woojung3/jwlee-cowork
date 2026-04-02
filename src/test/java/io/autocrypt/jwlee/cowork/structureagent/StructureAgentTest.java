package io.autocrypt.jwlee.cowork.structureagent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.BashTool;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.structureagent.domain.StructureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.embabel.agent.core.AgentPlatform;

class StructureAgentTest {

    private StructureAgent structureAgent;
    private BashTool bashTool;
    private CoworkLogger logger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bashTool = mock(BashTool.class);
        PromptProvider promptProvider = mock(PromptProvider.class);
        logger = mock(CoworkLogger.class);
        AgentPlatform agentPlatform = mock(AgentPlatform.class);
        objectMapper = new ObjectMapper();

        structureAgent = new StructureAgent(bashTool, promptProvider, logger, agentPlatform, objectMapper);
    }

    @Test
    @DisplayName("Should parse standard BashTool wrapper JSON correctly")
    void testParseWrapperJson() {
        // Arrange: Mock BashTool returning the standard wrapper
        String wrapperJson = "{\"exitCode\": 0, \"stdout\": \"{\\\"classes\\\": {}}\", \"stderr\": \"\"}";
        when(bashTool.execute(anyString())).thenReturn(wrapperJson);

        StructureRequest request = new StructureRequest(".", "context");
        StructureAgent.StructurePrimingState primingState = new StructureAgent.StructurePrimingState(request, "context");

        // Act
        var result = structureAgent.extractRawDependencies(primingState);

        // Assert
        assertNotNull(result);
        assertEquals("{\"classes\": {}}", result.rawJson());
    }

    @Test
    @DisplayName("Should handle raw Python JSON (NPE Fix check)")
    void testParseRawPythonJson() {
        // Arrange: Mock BashTool returning raw JSON (This caused NPE before the fix)
        String rawJson = "{\"classes\": {\"com.Test\": {\"name\": \"Test\"}}}";
        when(bashTool.execute(anyString())).thenReturn(rawJson);

        StructureRequest request = new StructureRequest(".", "context");
        StructureAgent.StructurePrimingState primingState = new StructureAgent.StructurePrimingState(request, "context");

        // Act
        var result = structureAgent.extractRawDependencies(primingState);

        // Assert
        assertNotNull(result);
        assertEquals(rawJson, result.rawJson());
    }

    @Test
    @DisplayName("Should throw RuntimeException on non-zero exit code")
    void testExitCodeFailure() {
        // Arrange: Mock failure
        String errorJson = "{\"exitCode\": 1, \"stdout\": \"\", \"stderr\": \"Something went wrong\"}";
        when(bashTool.execute(anyString())).thenReturn(errorJson);

        StructureRequest request = new StructureRequest(".", "context");
        StructureAgent.StructurePrimingState primingState = new StructureAgent.StructurePrimingState(request, "context");

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            structureAgent.extractRawDependencies(primingState);
        });
        assertTrue(exception.getMessage().contains("Dependency analysis failed"));
    }
}
