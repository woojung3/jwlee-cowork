package io.autocrypt.jwlee.cowork.weeklyreport.controller;

import io.autocrypt.jwlee.cowork.weeklyreport.event.AgentStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class WeeklyReportSseController {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportSseController.class);
    
    // processId 별로 SseEmitter 관리
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/events/{processId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String processId) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30분 만료
        
        emitters.put(processId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(processId));
        emitter.onTimeout(() -> emitters.remove(processId));
        emitter.onError((e) -> emitters.remove(processId));
        
        log.info("New SSE subscription for process: {}", processId);
        
        // 연결 직후 핑(ping) 이벤트 전송 (연결 확인용)
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException e) {
            log.error("Error sending initial SSE event", e);
        }
        
        return emitter;
    }

    @EventListener
    public void handleStatusChange(AgentStatusChangedEvent event) {
        SseEmitter emitter = emitters.get(event.processId());
        if (emitter != null) {
            try {
                log.info("Pushing SSE status change for {}: {}", event.processId(), event.status());
                // HTMX sse-swap을 위해 이벤트 이름을 "status-changed"로 지정
                emitter.send(SseEmitter.event()
                        .name("status-changed")
                        .data(event.processId())); // 데이터로는 processId만 보냄 (HTMX가 이걸 이용해 GET /status/{id} 호출)
            } catch (IOException e) {
                log.error("Error pushing SSE event", e);
                emitters.remove(event.processId());
            }
        }
    }
}
