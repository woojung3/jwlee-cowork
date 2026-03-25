package io.autocrypt.jwlee.cowork.core.hitl;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.chat.ChatSession;
import com.embabel.chat.UserMessage;
import org.jline.terminal.Terminal;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.style.TemplateExecutor;
import org.springframework.stereotype.Component;

import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.SelectItem;

import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;

import java.util.Arrays;
import java.util.Optional;
import java.time.Instant;

/**
 * Central listener for approval events.
 * It handles terminal UI locking to prevent overlapping prompts from multiple agents.
 */
@Component
public class TerminalApprovalListener {

    private final Optional<Terminal> terminal;
    private final CoworkLogger logger;
    private final ResourceLoader resourceLoader;
    private final TemplateExecutor templateExecutor;
    private final AgentPlatform platform;
    private final ComponentFlow.Builder componentFlowBuilder;
    private ChatSession currentSession;

    @Value("${spring.shell.interactive.enabled:true}")
    private boolean interactive;

    public TerminalApprovalListener(Optional<Terminal> terminal, CoworkLogger logger, ResourceLoader resourceLoader, 
                                    TemplateExecutor templateExecutor, AgentPlatform platform,
                                    ComponentFlow.Builder componentFlowBuilder) {
        this.terminal = terminal;
        this.logger = logger;
        this.resourceLoader = resourceLoader;
        this.templateExecutor = templateExecutor;
        this.platform = platform;
        this.componentFlowBuilder = componentFlowBuilder;
    }

    public void setCurrentSession(ChatSession session) {
        this.currentSession = session;
    }

    @Async
    @EventListener
    public synchronized void onApprovalRequested(ApprovalRequestedEvent event) {
        if (terminal.isEmpty() || !interactive) {
            logger.info("Approval", "Auto-approving (Non-interactive or No terminal).");
            AgentProcess process = platform.getAgentProcess(event.processId());
            process.getBlackboard().addObject(new ApprovalDecision(true, "Auto-approved in non-interactive mode"));
            process.run();
            return;
        }

        Terminal t = terminal.get();
        var writer = t.writer();
        if (writer == null) return;

        AgentProcess process = platform.getAgentProcess(event.processId());
        
        int retries = 0;
        while (process.getStatus() != AgentProcessStatusCode.WAITING && retries < 50) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            retries++;
        }

        writer.println("\n========================================");
        writer.println("[!] Agent Approval Required");
        writer.println("Message: " + event.message());
        writer.println("Plan: " + event.planDescription());
        writer.println("========================================");
        writer.flush();

        ComponentFlow flow = componentFlowBuilder.clone().reset()
                .withSingleItemSelector("decision")
                    .name("Approve this plan?")
                    .selectItems(Arrays.asList(
                            SelectItem.of("Approve", "true"),
                            SelectItem.of("Reject", "false")
                    ))
                    .and()
                .build();
        
        ComponentFlow.ComponentFlowResult result = flow.run();
        String decisionStr = result.getContext().get("decision");
        boolean approved = Boolean.parseBoolean(decisionStr);

        String comment = "Approved via shell";
        if (!approved) {
            StringInput commentInput = new StringInput(t, "Reason for rejection: ", "No comment");
            commentInput.setResourceLoader(resourceLoader);
            commentInput.setTemplateExecutor(templateExecutor);
            comment = commentInput.run(StringInput.StringInputContext.empty()).getResultValue();
        }

        process.getBlackboard().addObject(new ApprovalDecision(approved, comment));
        
        if (currentSession != null) {
            currentSession.onUserMessage(new UserMessage("Decision submitted: " + approved, "system", Instant.now()));
        } else {
            process.run();
        }
    }
}
