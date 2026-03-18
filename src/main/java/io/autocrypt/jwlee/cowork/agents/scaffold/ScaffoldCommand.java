package io.autocrypt.jwlee.cowork.agents.scaffold;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.domain.io.UserInput;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

/**
 * Spring Shell 기반의 진입점(Command) 템플릿입니다.
 */
@ShellComponent
public class ScaffoldCommand {

    private final AgentPlatform agentPlatform;

    public ScaffoldCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @ShellMethod(value = "스캐폴딩 에이전트 데모 실행 (복사해서 새 명령어 생성에 활용)", key = "demo-scaffold")
    public String runScaffold(@ShellOption(defaultValue = "기본 테스트 작업") String request) throws ExecutionException, InterruptedException {
        System.out.println("\n[System] 스캐폴딩 에이전트 시작. 요청사항: " + request);

        // 비동기로 AgentProcess를 실행 (승인 대기 중 블로킹 방지)
        AgentProcess process = AgentInvocation
                .create(agentPlatform, ScaffoldAgent.ScaffoldResult.class)
                .runAsync(new UserInput(request))
                .get();

        // 프로세스가 완료될 때까지 대기
        while (!process.getFinished()) {
            Thread.sleep(500);
        }

        // 결과 추출
        ScaffoldAgent.ScaffoldResult result = process.resultOfType(ScaffoldAgent.ScaffoldResult.class);

        if (result != null) {
            return "[System] 작업 완료. 결과: " + result.finalOutput();
        } else {
            return "[System] 작업이 중단되었거나 결과가 없습니다.";
        }
    }
}