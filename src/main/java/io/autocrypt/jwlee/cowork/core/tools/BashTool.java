package io.autocrypt.jwlee.cowork.core.tools;

import com.embabel.agent.api.annotation.LlmTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Advanced Bash execution tool inspired by Claude Code.
 * Executes shell commands with timeout handling, real-time output capture, and strict security validation.
 */
@Component
public class BashTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LlmTool(description = "Executes a given bash command and returns its output in JSON format.\n" +
            "\n" +
            "IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. Instead, use the appropriate dedicated tool (readFile, editFile, grep, glob) as this will provide a much better experience.\n" +
            "\n" +
            "# USAGE GUIDELINES:\n" +
            "1. Quiet Flags: Always prefer silent or quiet flags (e.g., `npm install --silent`, `git --no-pager`) to reduce output volume.\n" +
            "2. Pagination: Always disable terminal pagination (e.g., use `git --no-pager` or set `PAGER=cat`).\n" +
            "3. Direnv: In directories containing a '.envrc' file, you MUST wrap the command with 'direnv exec . <command>'.\n" +
            "4. Git Safety: Prefer to create a new commit rather than amending an existing commit. Never skip hooks (--no-verify) unless requested.\n" +
            "5. Interactive: Do NOT use interactive commands (e.g. vim, commands waiting for y/n).\n" +
            "6. Security: Strictly prohibit reading, printing, or searching the contents of '.envrc' files.\n" +
            "7. Parallelism: If commands are independent, make multiple tool calls in parallel. If they depend, chain them with '&&'.\n" +
            "8. Directory Context: If you need to run a command in a specific directory, prepend 'cd <path> && ' to your command.")
    public String execute(
            @LlmTool.Param(description = "The full bash command to execute. (e.g., 'cd my_dir && ls -la')") String command
    ) {
        System.out.println("\n[BashTool] 🚀 Executing command: " + command);
        Map<String, Object> resultMap = new HashMap<>();
        
        // --- 🛡️ 런타임 보안 검증 (Security Validation) ---
        try {
            validateSecurityConstraints(command);
        } catch (SecurityException e) {
            System.out.println("[BashTool] 🛑 Security Block: " + e.getMessage());
            resultMap.put("exitCode", -1);
            resultMap.put("stdout", "");
            resultMap.put("message", e.getMessage());
            try { return objectMapper.writeValueAsString(resultMap); } 
            catch (Exception ex) { return "{\"error\":\"Serialization failed\"}"; }
        }

        int actualTimeout = 120000; // Default 2 minutes
        
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr for stability
        
        File workingDir = new File(System.getProperty("user.dir"));
        if (workingDir.exists() && workingDir.isDirectory()) {
            processBuilder.directory(workingDir);
        }

        // Environment variables for consistent output
        processBuilder.environment().put("PAGER", "cat");
        processBuilder.environment().put("TERM", "dumb"); 
        processBuilder.environment().put("LANG", "en_US.UTF-8");
        processBuilder.environment().put("LC_ALL", "en_US.UTF-8");
        
        StringBuilder output = new StringBuilder();
        Process process = null;
        Thread readerThread = null;

        try {
            process = processBuilder.start();
            process.getOutputStream().close(); // No interactive input

            final Process p = process;
            readerThread = new Thread(() -> {
                try (InputStream is = p.getInputStream();
                     InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {
                    
                    char[] buffer = new char[4096];
                    int charsRead;
                    while ((charsRead = reader.read(buffer)) != -1) {
                        output.append(buffer, 0, charsRead);
                    }
                } catch (IOException e) {
                    // Stream closed or process died. Ignored safely.
                }
            });
            readerThread.setDaemon(true); 
            readerThread.start();

            boolean finished = process.waitFor(actualTimeout, TimeUnit.MILLISECONDS);
            
            if (!finished) {
                System.out.println("[BashTool] ⏰ Command timed out!");
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                readerThread.join(1000);
                
                resultMap.put("exitCode", -1);
                resultMap.put("stdout", output.toString());
                resultMap.put("message", "Command timed out after " + actualTimeout + "ms");
                return objectMapper.writeValueAsString(resultMap);
            }

            readerThread.join(2000);
            int exitCode = process.exitValue();
            System.out.println("[BashTool] ✅ Command finished with exit code: " + exitCode);
            
            resultMap.put("exitCode", exitCode);
            resultMap.put("stdout", output.toString());
            resultMap.put("message", (exitCode == 0) ? "Command executed successfully" : "Command failed with exit code " + exitCode);
            
            return objectMapper.writeValueAsString(resultMap);

        } catch (Exception e) {
            System.out.println("[BashTool] ❌ Exception: " + e.getMessage());
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (readerThread != null) {
                readerThread.interrupt();
            }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            
            resultMap.put("exitCode", -1);
            resultMap.put("stdout", output.toString());
            resultMap.put("message", "Error executing command: " + e.getMessage());
            
            try {
                return objectMapper.writeValueAsString(resultMap);
            } catch (Exception serializationException) {
                return "{\"error\":\"Failed to serialize error response\"}";
            }
        }
    }

    /**
     * 런타임 명령어 보안 검증
     * Claude Code의 pathValidation.ts 로직을 정규식 기반으로 자바에 이식
     */
    private void validateSecurityConstraints(String command) throws SecurityException {
        // 1. Directory Traversal (../) 차단 (README.md 규정)
        if (command.contains("../") || command.contains("..\\")) {
            throw new SecurityException("SECURITY BLOCK: Directory traversal (../) is strictly prohibited. You must operate within the current workspace.");
        }

        // 2. 홈(~) 또는 루트(/) 디렉토리로의 명시적 탈출 차단
        if (command.matches("(?i).*\\bcd\\s+(/|~).*")) {
            throw new SecurityException("SECURITY BLOCK: Changing directory to root (/) or home (~) is prohibited.");
        }

        // 3. 민감한 크레덴셜 및 환경변수 파일 접근 차단 (GEMINI.md 규정)
        Pattern sensitivePattern = Pattern.compile("(?i).*\\b(\\.env|\\.envrc|\\.ssh|id_rsa)\\b.*");
        if (sensitivePattern.matcher(command).matches()) {
            throw new SecurityException("SECURITY BLOCK: Accessing sensitive credentials (.env, .envrc, .ssh) is strictly prohibited.");
        }

        // 4. 시스템 절대 경로 접근 차단 (허용된 기본 bin 폴더 외)
        Pattern sysPathPattern = Pattern.compile("(?i).*\\s(/etc|/var|/root|/boot|/sys|/dev)(/|\\s|$).*");
        if (sysPathPattern.matcher(command).matches()) {
            throw new SecurityException("SECURITY BLOCK: Access to system-level directories is prohibited.");
        }
    }
}
