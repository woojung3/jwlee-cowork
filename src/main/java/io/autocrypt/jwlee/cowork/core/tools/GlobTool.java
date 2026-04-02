package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Specialized tool for finding files matching glob patterns.
 */
@Component
public class GlobTool {

    @LlmTool(description = "Finds files matching specific glob patterns (e.g., '**/*.java'). " +
            "Returns a list of relative paths. " +
            "ALWAYS provide an includePath to restrict the search and avoid scanning the entire project root.")
    public List<String> glob(
            @LlmTool.Param(description = "The glob pattern to match against. Do not use './' prefix unless necessary.") String pattern,
            @org.springframework.lang.Nullable @LlmTool.Param(description = "The directory to search in. STRONGLY RECOMMENDED to provide this. Defaults to '.' (current directory) if not provided.") String includePath
    ) throws IOException {
        // Strip leading ./ if present to match Java's PathMatcher behavior
        String normalizedPattern = pattern.startsWith("./") ? pattern.substring(2) : pattern;
        
        String searchDir = (includePath == null || includePath.trim().isEmpty()) ? "." : includePath;
        Path root = Paths.get(searchDir).toAbsolutePath().normalize();
        
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        List<String> results = new ArrayList<>();
        
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return results;
        }

        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> {
                String pathStr = p.toString();
                // Always skip common noise directories
                if (pathStr.contains("/.git/") || pathStr.contains("/target/") || pathStr.contains("/build/") || pathStr.contains("/node_modules/")) {
                    return false;
                }
                Path relativeToRoot = root.relativize(p);
                return !relativeToRoot.toString().isEmpty() && matcher.matches(relativeToRoot);
            })
            .forEach(p -> results.add(projectRoot.relativize(p).toString()));
        }
        return results;
    }
}
