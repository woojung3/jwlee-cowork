package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Specialized tool for reading files with state tracking.
 * Supports reading full content or specific line ranges.
 */
@Component
public class FileReadTool {

    private final FileStateRegistry stateRegistry;
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB limit for full read

    public FileReadTool(FileStateRegistry stateRegistry) {
        this.stateRegistry = stateRegistry;
    }

    public record ReadResult(String path, String content, String status, int totalLines) {}

    @LlmTool(description = "Reads the complete content of a file. Records the state for future edits. " +
            "For very large files, use readFileWithRange instead.")
    public ReadResult readFile(
            @LlmTool.Param(description = "The path to the file to read.") String filePath
    ) throws IOException {
        return readFileWithRange(filePath, 1, Integer.MAX_VALUE);
    }

    @LlmTool(description = "Reads a specific line range of a file (1-indexed, inclusive). " +
            "Use this for large files to avoid context overflow.")
    public ReadResult readFileWithRange(
            @LlmTool.Param(description = "The path to the file.") String filePath,
            @LlmTool.Param(description = "Start line number (inclusive, 1-indexed).") int startLine,
            @LlmTool.Param(description = "End line number (inclusive, 1-indexed).") int endLine
    ) throws IOException {
        Path path = Paths.get(".").toAbsolutePath().normalize().resolve(filePath).normalize();
        if (!Files.exists(path)) {
            return new ReadResult(filePath, null, "ERROR: File not found", 0);
        }

        long size = Files.size(path);
        if (size > MAX_FILE_SIZE && endLine == Integer.MAX_VALUE) {
            return new ReadResult(filePath, null, "ERROR: File too large (" + size + " bytes). Use range-based reading.", 0);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;

        // Record state for FileEditTool (Always record full content state if possible for edit consistency)
        stateRegistry.recordRead(path, content);

        if (startLine <= 1 && endLine >= totalLines) {
            return new ReadResult(filePath, content, "SUCCESS", totalLines);
        }

        StringBuilder sb = new StringBuilder();
        int actualStart = Math.max(1, startLine);
        int actualEnd = Math.min(endLine, totalLines);
        
        for (int i = actualStart - 1; i < actualEnd; i++) {
            sb.append(lines[i]);
            if (i < actualEnd - 1) sb.append("\n");
        }

        return new ReadResult(filePath, sb.toString(), "SUCCESS_PARTIAL", totalLines);
    }
}
