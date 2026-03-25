package io.autocrypt.jwlee.cowork.core.ui;

import java.util.Optional;

import org.jline.terminal.Terminal;

public class TerminalSpinner {
    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private final Optional<Terminal> terminal;
    private volatile boolean running = false;
    private Thread thread;

    public TerminalSpinner(Optional<Terminal> terminal) {
        this.terminal = terminal;
    }

    public void start(String message) {
        if (terminal == null) return;
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            int i = 0;
            while (running) {
                try {
                    terminal.get().writer().print("\r" + FRAMES[i % FRAMES.length] + " " + message);
                    terminal.get().writer().flush();
                    Thread.sleep(100);
                    i++;
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (terminal == null) return;
        running = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        terminal.get().writer().print("\r" + " ".repeat(40) + "\r");
        terminal.get().writer().flush();
    }
}
