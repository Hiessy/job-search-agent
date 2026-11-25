package com.hiessy.jobagent.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hiessy.jobagent.agent.AgentRun;
import com.hiessy.jobagent.agent.JobAgent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Talk to the agent: {@code POST /api/agent/runs {"message": "..."}}. */
@RestController
@RequestMapping("/api/agent")
class AgentController {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RunRequest(@NotBlank @Size(max = 20000) String message, @Size(max = 100) String conversationId) {
    }

    private final JobAgent agent;

    AgentController(JobAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/runs")
    AgentRun run(@Valid @RequestBody RunRequest request) {
        return agent.run(request.conversationId(), request.message());
    }
}
