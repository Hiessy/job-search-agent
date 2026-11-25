package com.hiessy.jobagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.applications.ApprovalService;
import com.hiessy.jobagent.applications.PendingAction;
import com.hiessy.jobagent.support.FixturePostingSource;
import com.hiessy.jobagent.support.IntegrationTest;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** An MCP client (the official Java SDK) gets the same tools, with the same guardrails and approval step. */
class McpServerIT extends IntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private McpSyncClient client;

    @Autowired
    private ApprovalService approvals;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp").build())
                .requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    @Test
    void listsExactlyTheAllowedTools() {
        assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "fetch_job_posting", "score_cv", "draft_cover_note", "save_application", "list_applications",
                "compare_cv_variants");
    }

    @Test
    void fetchScoreAndSaveGoThroughTheApprovalStep() throws Exception {
        long pendingBefore = approvals.pendingCount();
        JsonNode fetched = call("fetch_job_posting", Map.of("url", FixturePostingSource.urlOf("li-aplazo-tech-lead-manager.txt")));
        String id = fetched.get("posting_id").asText();

        JsonNode scored = call("score_cv", Map.of("job_text", id));
        JsonNode saved = call("save_application", Map.of("posting_id", id));

        assertThat(fetched.get("company").asText()).isEqualTo("Aplazo");
        assertThat(scored.get("verdict").asText()).isEqualTo("STRONG");
        assertThat(saved.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(approvals.pendingCount()).isEqualTo(pendingBefore + 1);
        assertThat(approvals.list(PendingAction.Status.PENDING)).anySatisfy(a -> assertThat(a.getRequestedBy()).isEqualTo("mcp"));
    }

    @Test
    void guardrailsApplyToMcpCallsToo() throws Exception {
        assertThat(call("fetch_job_posting", Map.of("url", "http://127.0.0.1:" + port + "/actuator/health"))
                .get("status").asText()).isEqualTo("INVALID_INPUT");
        assertThat(call("fetch_job_posting", Map.of("url", FixturePostingSource.urlOf("inject-role-tokens.txt")))
                .get("rule").asText()).isEqualTo("prompt_injection");
        assertThat(call("score_cv", Map.of("job_text", "")).get("errors").toString()).contains("job_text: must not be blank");
    }

    private JsonNode call(String tool, Map<String, Object> args) throws Exception {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        return mapper.readTree(text);
    }
}
