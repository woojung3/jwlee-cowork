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

    @LlmTool(description = "Finds files matching specific glob patterns (e.g., 'src/**/*.java'). " +
            "If you want to list files in the current directory, use '*' or '**/*'. " +
            "Returns a list of relative paths.")
    public List<String> glob(
            @LlmTool.Param(description = "The glob pattern to match against. Do not use './' prefix unless necessary.") String pattern
    ) throws IOException {
        // Strip leading ./ if present to match Java's PathMatcher behavior
        String normalizedPattern = pattern.startsWith("./") ? pattern.substring(2) : pattern;
        
        Path root = Paths.get(".").toAbsolutePath().normalize();
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        List<String> results = new ArrayList<>();
        
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> {
                Path relative = root.relativize(p);
                return !relative.toString().isEmpty() && matcher.matches(relative);
            })
            .forEach(p -> results.add(root.relativize(p).toString()));
        }
        return results;
    }
}
