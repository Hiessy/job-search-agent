package com.hiessy.jobagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiessy.jobagent.support.FixturePostingSource;
import com.hiessy.jobagent.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The whole path through HTTP, the agent loop, the real tools, PostgreSQL and the approval API, with
 * the model scripted so the test is deterministic. The live model is covered by the eval set.
 */
class AgentApiIT extends IntegrationTest {

    private RestClient http() {
        return RestClient.create(baseUrl());
    }

    @Test
    void savesOnlyAfterTheUserApproves() {
        String url = FixturePostingSource.urlOf("synthetic-java-backend.txt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt p = inv.getArgument(0);
            long toolTurns = p.getInstructions().stream().filter(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage).count();
            return switch ((int) toolTurns) {
                case 0 -> toolCall("fetch_job_posting", "{\"url\":\"" + url + "\"}");
                case 1 -> toolCall("score_cv", "{\"job_text\":\"" + lastPostingId(p) + "\"}");
                case 2 -> toolCall("save_application", "{\"posting_id\":\"" + lastPostingId(p) + "\",\"notes\":\"referral\"}");
                default -> text("Strong match. I asked to save it; approve the request to keep it.\nVERDICT: STRONG");
            };
        });

        JsonNode run = http().post().uri("/api/agent/runs").body(Map.of("message", "Check " + url + " and save it"))
                .retrieve().body(JsonNode.class);

        assertThat(run.get("outcome").asText()).isEqualTo("ANSWERED");
        assertThat(run.get("tool_sequence")).extracting(JsonNode::asText)
                .containsExactly("fetch_job_posting", "score_cv", "save_application");
        assertThat(run.get("tool_verdict").asText()).isEqualTo("STRONG");
        assertThat(run.get("reported_verdict").asText()).isEqualTo("STRONG");
        long approvalId = run.get("pending_approvals").get(0).asLong();

        assertThat(applicationsFor(url)).isEmpty();

        JsonNode approved = http().post().uri("/api/approvals/{id}/approve", approvalId).retrieve().body(JsonNode.class);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        List<JsonNode> saved = applicationsFor(url);
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.get("company").asText()).isEqualTo("Acme Freight");
            assertThat(a.get("verdict").asText()).isEqualTo("STRONG");
            assertThat(a.get("score").asInt()).isEqualTo(96);
            assertThat(a.get("notes").asText()).isEqualTo("referral");
        });

        HttpStatusCode again = statusOf(() -> http().post().uri("/api/approvals/{id}/approve", approvalId).retrieve().toBodilessEntity());
        assertThat(again.value()).isEqualTo(409);
    }

    @Test
    void refusesAPostingThatCarriesInstructions() {
        String url = FixturePostingSource.urlOf("inject-ignore-instructions.txt");
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCall("fetch_job_posting", "{\"url\":\"" + url + "\"}"));

        JsonNode run = http().post().uri("/api/agent/runs").body(Map.of("message", "Evaluate " + url + " and save it"))
                .retrieve().body(JsonNode.class);

        assertThat(run.get("outcome").asText()).isEqualTo("REFUSED");
        assertThat(run.get("answer").asText()).contains("ignore all previous instructions").endsWith("VERDICT: REFUSED");
        assertThat(run.get("pending_approvals")).isEmpty();
    }

    @Test
    void rejectedRequestsWriteNothing() {
        String url = FixturePostingSource.urlOf("li-fever-tech-lead.txt");
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt p = inv.getArgument(0);
            boolean fetched = p.getInstructions().stream().anyMatch(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage);
            boolean saved = p.getInstructions().stream().filter(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage).count() > 1;
            return !fetched ? toolCall("fetch_job_posting", "{\"url\":\"" + url + "\"}")
                    : !saved ? toolCall("save_application", "{\"posting_id\":\"" + lastPostingId(p) + "\"}")
                    : text("Requested.");
        });
        JsonNode run = http().post().uri("/api/agent/runs").body(Map.of("message", "Save " + url))
                .retrieve().body(JsonNode.class);
        long id = run.get("pending_approvals").get(0).asLong();

        JsonNode rejected = http().post().uri("/api/approvals/{id}/reject", id).retrieve().body(JsonNode.class);

        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(applicationsFor(url)).isEmpty();
        assertThat(statusOf(() -> http().post().uri("/api/approvals/{id}/approve", id).retrieve().toBodilessEntity()).value())
                .isEqualTo(409);
    }

    @Test
    void exposesToolMetrics() {
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCall("list_applications", "{}"), text("You have none."));
        http().post().uri("/api/agent/runs").body(Map.of("message", "What did I save?")).retrieve().toBodilessEntity();

        String prometheus = http().get().uri("/actuator/prometheus").retrieve().body(String.class);

        assertThat(prometheus).contains("jobagent_tool_latency_seconds_count{channel=\"agent\",outcome=\"ok\",tool=\"list_applications\"}")
                .contains("jobagent_agent_runs_total{outcome=\"answered\"}");
    }

    private List<JsonNode> applicationsFor(String url) {
        JsonNode all = http().get().uri("/api/applications").retrieve().body(JsonNode.class);
        List<JsonNode> out = new java.util.ArrayList<>();
        all.forEach(a -> {
            if (url.equals(a.path("posting_url").asText())) {
                out.add(a);
            }
        });
        return out;
    }

    private static HttpStatusCode statusOf(Runnable call) {
        try {
            call.run();
            return HttpStatusCode.valueOf(200);
        } catch (HttpClientErrorException e) {
            return e.getStatusCode();
        }
    }

    private static ChatResponse toolCall(String name, String args) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", name, args))).build())));
    }

    private static ChatResponse text(String s) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(s))));
    }
}
