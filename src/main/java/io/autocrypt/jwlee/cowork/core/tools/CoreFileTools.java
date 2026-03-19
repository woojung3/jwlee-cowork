package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core file manipulation tools for agents, inspired by Gemini CLI.
 */
@Component
public class CoreFileTools {

    private static final long MAX_FILE_SIZE = 500 * 1024; // 500KB safety limit
    private final Terminal terminal;

    public CoreFileTools(Terminal terminal) {
        this.terminal = terminal;
    }

    public record FileResult(String path, String content, String status) {
        public static FileResult success(String path, String content) {
            return new FileResult(path, content, "SUCCESS");
        }
        public static FileResult error(String path, String message) {
            return new FileResult(path, null, "ERROR: " + message);
        }
    }

    private Path resolveAndCheckPath(String pathStr) {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        Path target = root.resolve(pathStr).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Access denied: Path " + pathStr + " is outside the working directory.");
        }
        return target;
    }

    @LlmTool(description = "Reads the complete content of a specified file. Returns a FileResult.")
    public FileResult readFile(String path) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (!Files.exists(filePath)) return FileResult.error(path, "File not found.");
        
        long size = Files.size(filePath);
        if (size > MAX_FILE_SIZE) return FileResult.error(path, "File too large.");

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return FileResult.success(path, content);
    }

    @LlmTool(description = "Writes content to a NEW file. Fails if file exists. Returns FileResult.")
    public FileResult writeFile(String path, String content) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (Files.exists(filePath)) return FileResult.error(path, "File already exists.");

        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return FileResult.success(path, "Successfully created and wrote to " + path);
    }

    @LlmTool(description = "Replaces ONE occurrence of a literal string. Shows a diff to the user. Returns FileResult.")
    public FileResult replace(String path, String oldString, String newString) throws IOException {
        Path filePath = resolveAndCheckPath(path);
        if (!Files.exists(filePath)) return FileResult.error(path, "File not found.");

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        if (!content.contains(oldString)) return FileResult.error(path, "Target string not found.");
        
        if (content.indexOf(oldString) != content.lastIndexOf(oldString)) {
            return FileResult.error(path, "Multiple occurrences found. Provide more context to make it unique.");
        }
        
        String newContent = content.replace(oldString, newString);
        Files.writeString(filePath, newContent, StandardCharsets.UTF_8);

        // --- Visual Diff Output ---
        showDiff(path, oldString, newString);
        
        return FileResult.success(path, "Successfully updated " + path);
    }

    private void showDiff(String path, String oldText, String newText) {
        var writer = terminal.writer();
        writer.println();
        writer.println(new AttributedString("--- Diff: " + path + " ---", 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)).toAnsi());
        
        // Simple line-based diff for the changed part
        writer.println(new AttributedString("- " + oldText, 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).toAnsi());
        writer.println(new AttributedString("+ " + newText, 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)).toAnsi());
        
        writer.println(new AttributedString("-----------------------", 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)).toAnsi());
        writer.flush();
    }

    @LlmTool(description = "Lists files in a directory. Returns a list of names.")
    public List<String> listDirectory(String path) throws IOException {
        Path dirPath = resolveAndCheckPath(path);
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toList());
        }
    }

    @LlmTool(description = "Finds files matching glob patterns.")
    public List<String> glob(String pattern) throws IOException {
        Path root = Paths.get(".").toAbsolutePath().normalize();
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> matcher.matches(root.relativize(p)))
                    .map(p -> root.relativize(p).toString()).collect(Collectors.toList());
        }
    }
}
