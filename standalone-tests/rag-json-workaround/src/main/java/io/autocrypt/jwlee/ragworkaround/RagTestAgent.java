package io.autocrypt.jwlee.ragworkaround;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.tools.ResultsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Agent(description = "Agent for testing JSON workaround")
@Component
public class RagTestAgent {

    private static final Logger logger = LoggerFactory.getLogger(RagTestAgent.class);
    private final JsonSafeToolishRag toolishRag;

    public RagTestAgent(JsonSafeToolishRag toolishRag) {
        this.toolishRag = toolishRag;
    }

    @AchievesGoal(description = "Provides the user with a string answer")
    @Action
    public String search(UserInput input, ActionContext ctx) {
        // 1. 리스너를 달아서 검색 이벤트가 발생하면 Blackboard에 저장하도록 함
        var ragWithListener = toolishRag.withListener(event -> {
            ctx.addObject(event);
        });

        // 2. 검색 실행
        String answer = ctx.ai().withDefaultLlm()
                .withReference(ragWithListener)
                .generateText(String.format("Answer the user's question using the 'docs' tool. Question: %s", input.getContent()));

        // 3. Blackboard에서 ResultsEvent를 꺼내서 결정론적으로 출력
        List<ResultsEvent> events = ctx.objectsOfType(ResultsEvent.class);
        
        for (ResultsEvent event : events) {
            System.out.println("\n[DETERMINISTIC RAG CAPTURE]");
            System.out.println("Captured Query: " + event.getQuery());
            System.out.println("Result Count: " + event.getResults().size());
            System.out.println("============================\n");
            
            logger.info("RagTest", "Query: {}, Count: {}", event.getQuery(), event.getResults().size());
        }

        return answer;
    }
}
