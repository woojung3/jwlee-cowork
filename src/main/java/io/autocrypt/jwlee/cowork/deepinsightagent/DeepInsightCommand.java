package io.autocrypt.jwlee.cowork.deepinsightagent;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.core.tools.CoreWorkspaceProvider;
import io.autocrypt.jwlee.cowork.deepinsightagent.domain.DeepInsightRequest;
import io.autocrypt.jwlee.cowork.deepinsightagent.domain.DeepInsightResult;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

@ShellComponent
@ShellCommandGroup("Code Analysis Command")
public class DeepInsightCommand extends BaseAgentCommand {

    private final CoreWorkspaceProvider workspaceProvider;
    private static final String AGENT_NAME = "deepinsight";

    public DeepInsightCommand(AgentPlatform agentPlatform, CoreWorkspaceProvider workspaceProvider) {
        super(agentPlatform);
        this.workspaceProvider = workspaceProvider;
    }

    @ShellMethod(key = "deep-insight", value = "Perform a full system analysis (Arch + ERD + API + Ops + Structure).")
    public String deepInsight(
            @ShellOption(defaultValue = ".", help = "Path to analyze") String path,
            @ShellOption(defaultValue = "Full system deep analysis", help = "Context for analysis") String context,
            @ShellOption(value = {"-p", "--show-prompts"}, help = "Show LLM prompts", defaultValue = "false") boolean showPrompts,
            @ShellOption(value = {"-r", "--show-responses"}, help = "Show LLM responses", defaultValue = "false") boolean showResponses
    ) throws ExecutionException, InterruptedException, IOException {
        var request = new DeepInsightRequest(path, context);

        System.out.println("🚀 Starting COMPREHENSIVE system analysis for: " + path);
        System.out.println("   (This will invoke 5 specialized agents sequentially. Please wait...)");

        DeepInsightResult result = invokeAgent(
                DeepInsightResult.class,
                getOptions(showPrompts, showResponses),
                request
        );

        String finalReport = formatResult(result);
        
        // Save to file
        String projectName = Path.of(path).getFileName().toString();
        if (projectName.equals(".") || projectName.isEmpty()) {
            projectName = "root";
        }
        
        Path reportDir = workspaceProvider.getSubPath(AGENT_NAME, projectName, CoreWorkspaceProvider.SubCategory.ARTIFACTS);
        Files.createDirectories(reportDir);
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path reportFile = reportDir.resolve("deep_insight_report_" + timestamp + ".md");
        Files.writeString(reportFile, result.fullReport());

        return finalReport + "\n\n✅ Report saved to: " + reportFile.toAbsolutePath();
    }

    private String formatResult(DeepInsightResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n======================================================================\n");
        sb.append("🔍 DEEP INSIGHT ANALYSIS REPORT\n");
        sb.append("======================================================================\n\n");
        
        sb.append("✨ SUMMARY:\n").append(result.summary()).append("\n\n");
        sb.append(result.fullReport()).append("\n");
        
        sb.append("\n======================================================================\n");
        sb.append("Status: ").append(result.status()).append("\n");
        sb.append("======================================================================\n");
        
        return sb.toString();
    }
}
