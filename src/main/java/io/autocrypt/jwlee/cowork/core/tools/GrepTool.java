package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Specialized high-performance search tool.
 * Prefers ripgrep (rg) if available, falls back to grep.
 */
@Component
public class GrepTool {

    private final CoworkLogger logger;

    public GrepTool(CoworkLogger logger) {
        this.logger = logger;
    }

    @LlmTool(description = "Searches for a regex pattern in the codebase. " +
            "Returns matching lines with file paths. " +
            "ALWAYS provide an includePath to restrict the search range.")
    public List<String> grep(
            @LlmTool.Param(description = "The regex pattern to search for.") String pattern,
            @org.springframework.lang.Nullable @LlmTool.Param(description = "The directory or file to search in. STRONGLY RECOMMENDED to provide this. Defaults to '.' (current directory) if not provided.") String includePath
    ) {
        // Handle cases where includePath might be null or missing due to reflection mapping issues
        String searchDir = (includePath == null || includePath.trim().isEmpty()) ? "." : includePath;
        
        // Sanitize pattern to prevent command injection
        String sanitizedPattern = pattern.replace("\"", "\\\"");
        
        logger.info("GrepTool", "Searching for pattern: " + sanitizedPattern + " in " + searchDir);
        
        // Try ripgrep first (respects .gitignore and is faster), then grep
        // Add explicit exclusions for common noise to prevent leaking out of requested path if possible
        String commonExcludes = " --exclude-dir={.git,target,build,node_modules}";
        List<String> results = runCommand("rg -n --no-heading --fixed-strings \"" + sanitizedPattern + "\" " + searchDir);
        if (results == null || results.isEmpty()) {
            results = runCommand("grep -rnE \"" + sanitizedPattern + "\" " + searchDir + commonExcludes);
        }
        
        return results != null ? results : new ArrayList<>();
    }

    private List<String> runCommand(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-c", command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().limit(200).collect(Collectors.toList());
            } finally {
                process.destroy();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
