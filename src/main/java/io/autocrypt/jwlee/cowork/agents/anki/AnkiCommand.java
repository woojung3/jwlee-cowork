package io.autocrypt.jwlee.cowork.agents.anki;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

@ShellComponent
public class AnkiCommand {

    private final AgentPlatform agentPlatform;
    private final LocalRagTools localRagTools;

    public AnkiCommand(AgentPlatform agentPlatform, LocalRagTools localRagTools) {
        this.agentPlatform = agentPlatform;
        this.localRagTools = localRagTools;
    }

    @ShellMethod(value = "Generate Anki cards from a document (Full Process)", key = "anki-gen")
    public String generateAnki(
            @ShellOption(help = "Path to the document (PDF or Markdown)") String filePath,
            @ShellOption(help = "Workspace/Deck name", defaultValue = "anki_default") String wsName,
            @ShellOption(help = "Number of final cards to keep", defaultValue = "30") int maxCards) throws ExecutionException, InterruptedException {
        
        Path path = Paths.get(filePath);
        Path ragPath = Paths.get("output/anki/rag", wsName);

        // Ingest into RAG first for Phase 4 definitions
        System.out.println("[System] Ingesting document into RAG for context...");
        localRagTools.ingestUrlAt(path.toString(), wsName, ragPath);

        AnkiAgent.AnkiStartRequest request = new AnkiAgent.AnkiStartRequest(path, wsName, ragPath, maxCards);

        System.out.println("[System] Starting Anki Generation Agent for: " + filePath);

        AgentProcess process = AgentInvocation
                .create(agentPlatform, AnkiAgent.AnkiResult.class)
                .runAsync(request)
                .get();

        while (!process.getFinished()) {
            Thread.sleep(1000);
        }

        AnkiAgent.AnkiResult result = process.resultOfType(AnkiAgent.AnkiResult.class);
        if (result != null) {
            return "[System] Anki CSV generated: " + result.csvPath();
        } else {
            return "[System] Anki generation failed or was interrupted.";
        }
    }

    @ShellMethod(value = "Resume Anki card generation from filtering phase", key = "anki-resume")
    public String resumeAnki(
            @ShellOption(help = "Workspace/Deck name") String wsName,
            @ShellOption(help = "Number of final cards to keep", defaultValue = "30") int maxCards) throws ExecutionException, InterruptedException {

        AnkiAgent.AnkiResumeRequest request = new AnkiAgent.AnkiResumeRequest(wsName, maxCards);

        System.out.println("[System] Resuming Anki Generation for workspace: " + wsName);

        AgentProcess process = AgentInvocation
                .create(agentPlatform, AnkiAgent.AnkiResult.class)
                .runAsync(request)
                .get();

        while (!process.getFinished()) {
            Thread.sleep(1000);
        }

        AnkiAgent.AnkiResult result = process.resultOfType(AnkiAgent.AnkiResult.class);
        if (result != null) {
            return "[System] Anki CSV regenerated: " + result.csvPath();
        } else {
            return "[System] Anki resume failed or was interrupted.";
        }
    }
}
