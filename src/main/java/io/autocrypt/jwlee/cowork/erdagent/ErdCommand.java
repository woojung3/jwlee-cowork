package io.autocrypt.jwlee.cowork.erdagent;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdRequest;
import io.autocrypt.jwlee.cowork.erdagent.domain.ErdResult;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

@ShellComponent
@ShellCommandGroup("Code Analysis Command")
public class ErdCommand extends BaseAgentCommand {

    public ErdCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(key = "erd-gen", value = "Analyzes JPA entities and DDL to generate a Mermaid ERD.")
    public String generateErd(
            @ShellOption(defaultValue = ".", help = "Path to analyze") String path,
            @ShellOption(defaultValue = "Extract the complete database schema including primary and foreign keys.", help = "Context or instructions for the ERD generation") String context,
            @ShellOption(value = {"-p", "--show-prompts"}, help = "Show LLM prompts", defaultValue = "false") boolean showPrompts,
            @ShellOption(value = {"-r", "--show-responses"}, help = "Show LLM responses", defaultValue = "false") boolean showResponses
    ) throws ExecutionException, InterruptedException {

        System.out.println("🚀 Starting ERD generation for: " + path);
        System.out.println("   (Scanning for JPA entities and DDL scripts...)");

        ErdRequest request = new ErdRequest(path, context);

        ErdResult result = invokeAgent(
                ErdResult.class,
                getOptions(showPrompts, showResponses),
                request
        );

        StringBuilder sb = new StringBuilder();
        sb.append("\n==================================================\n");
        sb.append("📊 ERD ANALYSIS REPORT\n");
        sb.append("==================================================\n\n");
        
        sb.append(result.markdownContent()).append("\n");
        
        sb.append("\n==================================================\n");
        
        return sb.toString();
    }
}
