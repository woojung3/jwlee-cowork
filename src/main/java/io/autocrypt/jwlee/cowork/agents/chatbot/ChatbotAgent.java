package io.autocrypt.jwlee.cowork.agents.chatbot;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.chat.Conversation;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Agent(description = "Main Orchestrator Agent for general chat and task coordination")
@Component
public class ChatbotAgent {

    private final RoleGoalBackstory mainOrchestratorPersona;
    private final CoreFileTools coreFileTools;

    // Safety limit: 500KB (Approx. 500,000 characters)
    private static final long MAX_FILE_SIZE = 500 * 1024; 

    public ChatbotAgent(RoleGoalBackstory mainOrchestratorPersona, CoreFileTools coreFileTools) {
        this.mainOrchestratorPersona = mainOrchestratorPersona;
        this.coreFileTools = coreFileTools;
    }

    /**
     * DTO to ensure safe JSON serialization for the LLM.
     */
    public record FileResult(String path, String content, String status) {}

    /**
     * Tool: readFile
     * Reads the entire file if it's within the safety limit.
     */
    @LlmTool(description = "Reads the complete content of a specified file. Returns a FileResult containing the content.")
    public FileResult readFile(String path) throws IOException {
        Path filePath = Paths.get(path);
        
        // 1. Check if file exists and get size
        if (!Files.exists(filePath)) {
            return new FileResult(path, null, "ERROR: File not found.");
        }

        long size = Files.size(filePath);
        if (size > MAX_FILE_SIZE) {
            return new FileResult(path, null, 
                String.format("ERROR: File too large (%d bytes). Limit is %d bytes.", size, MAX_FILE_SIZE));
        }

        // 2. Read content and wrap it in a record for safe JSON transport
        String content = coreFileTools.readFile(path);
        return new FileResult(path, content, "SUCCESS");
    }

    /**
     * Responds to user messages.
     */
    @Action(canRerun = true, trigger = UserMessage.class)
    public void chat(Conversation conversation, ActionContext context, Ai ai) {
        List<Message> messages = conversation.getMessages();
        List<Message> contextMessages = messages.size() > 10 
                ? messages.subList(messages.size() - 10, messages.size()) 
                : messages;

        var response = ai.withLlmByRole("cheapest")
                .withPromptContributor(mainOrchestratorPersona)
                .withToolObject(this) 
                .respond(contextMessages);

        context.sendMessage(conversation.addMessage(response));
    }
}
