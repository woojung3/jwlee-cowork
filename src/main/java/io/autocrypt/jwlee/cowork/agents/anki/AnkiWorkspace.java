package io.autocrypt.jwlee.cowork.agents.anki;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class AnkiWorkspace {

    private final ObjectMapper objectMapper;
    private static final Path BASE_PATH = Paths.get("output/anki/workspaces");

    public AnkiWorkspace(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path initWorkspace(String workspaceName) throws IOException {
        Path wsPath = BASE_PATH.resolve(workspaceName);
        if (!Files.exists(wsPath)) {
            Files.createDirectories(wsPath);
        }
        return wsPath;
    }

    public void saveState(Path wsPath, AnkiAgent.ExtractedState state) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(wsPath.resolve("state.json").toFile(), state);
    }

    public AnkiAgent.ExtractedState loadState(Path wsPath) throws IOException {
        return objectMapper.readValue(wsPath.resolve("state.json").toFile(), AnkiAgent.ExtractedState.class);
    }

    public void saveAllTerms(Path wsPath, List<AnkiAgent.ScoredTerm> terms) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(wsPath.resolve("all_extracted_terms.json").toFile(), terms);
    }
}
