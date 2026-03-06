package io.autocrypt.jwlee.cowork.weeklyreport.event;

import com.embabel.agent.core.AgentProcessStatusCode;

public record AgentStatusChangedEvent(String processId, AgentProcessStatusCode status) {}
