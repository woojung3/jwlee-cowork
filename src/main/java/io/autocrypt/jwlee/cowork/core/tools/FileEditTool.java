package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced file editing tool inspired by Claude Code.
 * Performs surgical edits by replacing specific string patterns.
 */
@Component
public class FileEditTool {

    private final FileStateRegistry stateRegistry;

    public FileEditTool(FileStateRegistry stateRegistry) {
        this.stateRegistry = stateRegistry;
    }

    public record EditResult(String path, String message, String status) {
        public static EditResult success(String path, String message) {
            return new EditResult(path, message, "SUCCESS");
        }
        public static EditResult error(String path, String message) {
            return new EditResult(path, message, "ERROR");
        }
    }

    @LlmTool(description = "Surgically edits a file by replacing a specific string. " +
            "USAGE GUIDELINES:\n" +
            "1. You MUST use 'readFile' or 'readFileWithRange' before calling this tool. It will fail if the file hasn't been read.\n" +
            "2. When copying text from read output, preserve exact indentation (tabs/spaces) character-for-character. " +
            "The line numbers (e.g., '  1 | ') are NOT part of the file content; ignore them and only copy what follows.\n" +
            "3. Use the smallest 'oldString' that is clearly unique. Usually 2-4 lines of context is ideal.\n" +
            "4. The edit will FAIL if 'oldString' is not unique in the file. Add more context to make it unique.\n" +
            "5. Use 'replaceAll=true' ONLY for global renames across the entire file.\n" +
            "CRITICAL: Never claim a file is modified without calling this tool and receiving a SUCCESS result.")
    public EditResult editFile(
            @LlmTool.Param(description = "The path to the file to edit.") String filePath,
            @LlmTool.Param(description = "The exact literal text to replace. Must match the file content exactly, including whitespace.") String oldString,
            @LlmTool.Param(description = "The new text to replace 'oldString' with.") String newString,
            @LlmTool.Param(description = "If true, all occurrences of 'oldString' will be replaced.") boolean replaceAll
    ) throws IOException {
        
        Path path = Paths.get(".").toAbsolutePath().normalize().resolve(filePath).normalize();
        if (!Files.exists(path)) {
            return EditResult.error(filePath, "File does not exist. Read the file first to establish context.");
        }

        // 1. Check for Staleness
        FileStateRegistry.FileState lastState = stateRegistry.getState(path);
        if (lastState == null) {
            return EditResult.error(filePath, "File has not been read yet. You MUST use a read tool before editing to ensure you have the latest content.");
        }

        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
        if (lastModified.isAfter(lastState.lastReadTime())) {
            String currentContent = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
            if (!currentContent.equals(lastState.content())) {
                return EditResult.error(filePath, "Staleness Check Failed: The file has been modified externally since you last read it. Read the file again to refresh your context.");
            }
        }

        String fileContent = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");

        // 2. Handle Quote Normalization & Finding Actual String
        String actualOldString = StringEditUtils.findActualString(fileContent, oldString);
        if (actualOldString == null) {
            return EditResult.error(filePath, "Exact match not found. Ensure 'oldString' matches the file content EXACTLY, including all spaces and tabs. " +
                    "Do NOT include the line number prefixes from the read output.");
        }

        // 3. Ambiguity Check
        int firstIndex = fileContent.indexOf(actualOldString);
        int lastIndex = fileContent.lastIndexOf(actualOldString);
        if (firstIndex != lastIndex && !replaceAll) {
            return EditResult.error(filePath, "Ambiguity Error: Multiple occurrences of the same string found. Provide more surrounding context in 'oldString' to target only one location.");
        }

        // 4. Preserve Typography and Replace
        String actualNewString = StringEditUtils.preserveQuoteStyle(oldString, actualOldString, newString);
        String updatedContent = replaceAll 
                ? fileContent.replace(actualOldString, actualNewString) 
                : fileContent.replaceFirst(Pattern.quote(actualOldString), Matcher.quoteReplacement(actualNewString));

        if (updatedContent.equals(fileContent)) {
             return EditResult.error(filePath, "No changes detected: The resulting content would be identical to the current content.");
        }

        // 5. Write to Disk
        Files.writeString(path, updatedContent, StandardCharsets.UTF_8);
        
        // 6. Update Registry
        stateRegistry.recordRead(path, updatedContent);

        return EditResult.success(filePath, "Successfully updated " + filePath);
    }
}
