package io.autocrypt.jwlee.cowork.agents.readme;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.domain.io.UserInput;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

/**
 * README.md 업데이트 에이전트를 실행하는 Spring Shell 명령어입니다.
 */
@ShellComponent
public class ReadmeUpdaterCommand {

    private final AgentPlatform agentPlatform;

    public ReadmeUpdaterCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @ShellMethod(value = "README.md 파일을 업데이트합니다.", key = "update-readme")
    public String runReadmeUpdater(@ShellOption(defaultValue = "내용을 요약해서 개선해줘") String request) throws ExecutionException, InterruptedException {
        System.out.println("\n[System] ReadmeUpdater 에이전트 시작. 요청사항: " + request);

        // 비동기로 AgentProcess를 실행 (승인 대기 중 블로킹 방지)
        AgentProcess process = AgentInvocation
                .create(agentPlatform, ReadmeUpdaterAgent.ReadmeUpdateResult.class)
                .runAsync(new UserInput(request))
                .get();

        // 프로세스가 완료될 때까지 대기
        while (!process.getFinished()) {
            Thread.sleep(500);
        }

        // 결과 추출
        ReadmeUpdaterAgent.ReadmeUpdateResult result = process.resultOfType(ReadmeUpdaterAgent.ReadmeUpdateResult.class);

        if (result != null) {
            return "[System] 작업 완료. 결과: " + result.message();
        } else {
            return "[System] 작업이 중단되었거나 결과가 없습니다.";
        }
    }
}
