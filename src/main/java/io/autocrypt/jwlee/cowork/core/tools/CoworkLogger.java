package io.autocrypt.jwlee.cowork.core.tools;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Interface to decouple business logic from Terminal UI.
 * Uses Optional<Terminal> to gracefully fallback to SLF4J in non-terminal environments.
 */
@Component
public class CoworkLogger {
    private static final Logger log = LoggerFactory.getLogger(CoworkLogger.class);
    private final Optional<Terminal> terminal;

    public CoworkLogger(Optional<Terminal> terminal) {
        this.terminal = terminal;
    }

    public void info(String prefix, String message) {
        terminal.ifPresentOrElse(
            t -> {
                t.writer().println(new AttributedString(prefix + ": " + message, 
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)).toAnsi());
                t.writer().flush();
            },
            () -> log.info("[{}] {}", prefix, message)
        );
    }

    public void error(String prefix, String message) {
        terminal.ifPresentOrElse(
            t -> {
                t.writer().println(new AttributedString(prefix + " Error: " + message, 
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).toAnsi());
                t.writer().flush();
            },
            () -> log.error("[{}] {}", prefix, message)
        );
    }
}
