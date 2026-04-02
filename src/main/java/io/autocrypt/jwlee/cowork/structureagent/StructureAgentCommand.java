package io.autocrypt.jwlee.cowork.structureagent;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.structureagent.domain.FinalStructureReport;
import io.autocrypt.jwlee.cowork.structureagent.domain.StructureRequest;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

/**
 * CLI Command for StructureAgent.
 */
@ShellComponent
@ShellCommandGroup("Code Analysis Command")
public class StructureAgentCommand extends BaseAgentCommand {

    public StructureAgentCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(value = "Analyze code structure, dependencies, and architecture integrity.", key = "structure-analyze")
    public String analyzeStructure(
            @ShellOption(help = "Root path of the project to analyze.", defaultValue = ".") String path,
            @ShellOption(help = "Optional context for analysis.", defaultValue = "") String context,
            @ShellOption(help = "Show LLM prompts.", defaultValue = "false") boolean showPrompts,
            @ShellOption(help = "Show LLM responses.", defaultValue = "false") boolean showResponses
    ) throws ExecutionException, InterruptedException {
        
        logger.info("StructureCommand", "Starting Structure analysis for path: " + path);
        
        StructureRequest request = new StructureRequest(path, context);
        FinalStructureReport result = invokeAgent(FinalStructureReport.class, getOptions(showPrompts, showResponses), request);
        
        return result.report();
    }
}
