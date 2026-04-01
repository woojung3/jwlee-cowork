package io.autocrypt.jwlee.cowork.core.tools;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the state of files read by the agent to ensure safe, 
 * atomic-like edits by checking if a file was modified since it was last read.
 */
@Component
public class FileStateRegistry {

    public record FileState(String content, Instant lastReadTime) {}

    private final Map<Path, FileState> fileStates = new ConcurrentHashMap<>();

    /**
     * Records the state of a file after it's been read.
     */
    public void recordRead(Path path, String content) {
        fileStates.put(path.toAbsolutePath().normalize(), new FileState(content, Instant.now()));
    }

    /**
     * Gets the last recorded state of a file.
     */
    public FileState getState(Path path) {
        return fileStates.get(path.toAbsolutePath().normalize());
    }

    /**
     * Clears state after a successful write or to force a re-read.
     */
    public void invalidate(Path path) {
        fileStates.remove(path.toAbsolutePath().normalize());
    }
}
