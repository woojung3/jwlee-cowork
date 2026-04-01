package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Specialized tool for writing new files or overwriting content.
 * Should be used sparingly; prefer FileEditTool for surgical updates.
 */
@Component
public class FileWriteTool {

    private final FileStateRegistry stateRegistry;

    public FileWriteTool(FileStateRegistry stateRegistry) {
        this.stateRegistry = stateRegistry;
    }

    public record WriteResult(String path, String message, String status) {}

    @LlmTool(description = "Writes complete content to a file. Overwrites existing content. " +
            "Automatically creates missing parent directories.")
    public WriteResult writeFile(
            @LlmTool.Param(description = "The path to the file.") String filePath,
            @LlmTool.Param(description = "The complete content to write.") String content
    ) throws IOException {
        Path path = Paths.get(".").toAbsolutePath().normalize().resolve(filePath).normalize();
        
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);
        
        // Update registry state
        stateRegistry.recordRead(path, content);
        
        return new WriteResult(filePath, "Successfully written " + content.length() + " chars", "SUCCESS");
    }
}
