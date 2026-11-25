package com.hiessy.jobagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.guardrails.AgentToolbox;
import com.hiessy.jobagent.guardrails.ToolOutput;
import com.hiessy.jobagent.guardrails.ToolSpec;
import com.hiessy.jobagent.observability.AgentMetrics;
import com.hiessy.jobagent.tools.ToolInputs;
import com.hiessy.jobagent.tools.ToolResults;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** The loop with a scripted model (Mockito) and stub tools: no LLM, no database. */
class JobAgentTest {

    private static final String EVIL = "https://jobs.example.com/evil";

    private final ChatModel model = mock(ChatModel.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicBoolean saveRan = new AtomicBoolean();
    private JobAgent agent;

    @BeforeEach
    void setUp() {
        List<ToolSpec<?>> specs = List.of(
                new ToolSpec<>("fetch_job_posting", "Read a posting", ToolInputs.FetchJobPosting.class, false, (in, run) ->
                        in.url().equals(EVIL)
                                ? ToolOutput.Problem.injection("The posting contains instructions aimed at AI tools: \"ignore all previous instructions\".")
                                : new ToolResults.Fetched("OK", "P1", "Java Tech Lead", "Acme", 900, "preview", null)),
                new ToolSpec<>("score_cv", "Score", ToolInputs.ScoreCv.class, false, (in, run) -> {
                    run.verdict("WEAK");
                    return new ToolResults.Scored("OK", "P1", "Java Tech Lead", "Acme", "WEAK", 70, "Medium", List.of(),
                            List.of(), List.of(), List.of(), List.of(), null);
                }),
                new ToolSpec<>("save_application", "Save", ToolInputs.SaveApplication.class, true, (in, run) -> {
                    saveRan.set(true);
                    run.addApproval(7);
                    return new ToolResults.SaveRequested("PENDING_APPROVAL", 7, "Save", "approve it");
                }));
        JobAgentProperties props = new JobAgentProperties(
                new JobAgentProperties.Cv("x", "y", Map.of(), 2026, "pdftotext"),
                new JobAgentProperties.Agent(4, 40),
                new JobAgentProperties.Guardrails(List.of("fetch_job_posting", "score_cv", "save_application"), 10),
                new JobAgentProperties.Fetch(Duration.ofSeconds(5), 1000, 1000, false, "test"));
        AgentMetrics metrics = new AgentMetrics(registry);
        AgentToolbox toolbox = new AgentToolbox(specs, props, new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(), metrics);
        agent = new JobAgent(model, toolbox, metrics, new ConversationStore(props), props);
    }

    @Test
    void modelChoosesToolsUntilItAnswers() {
        when(model.call(any(Prompt.class))).thenReturn(
                toolCall("fetch_job_posting", "{\"url\":\"https://jobs.example.com/1\"}"),
                toolCall("score_cv", "{\"job_text\":\"P1\"}"),
                text("It's a WEAK match: Kafka is missing.\nVERDICT: WEAK"));

        AgentRun run = agent.run(null, "Score https://jobs.example.com/1");

        assertThat(run.outcome()).isEqualTo("ANSWERED");
        assertThat(run.toolSequence()).containsExactly("fetch_job_posting", "score_cv");
        assertThat(run.reportedVerdict()).isEqualTo("WEAK");
        assertThat(run.toolVerdict()).isEqualTo("WEAK");
        assertThat(run.steps()).allSatisfy(s -> assertThat(s.outcome()).isEqualTo("ok"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompt.capture());
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getValue().getOptions();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactly("fetch_job_posting", "score_cv", "save_application");
    }

    @Test
    void injectedPostingStopsTheRunWithoutAskingTheModelAgain() {
        when(model.call(any(Prompt.class))).thenReturn(toolCall("fetch_job_posting", "{\"url\":\"" + EVIL + "\"}"));

        AgentRun run = agent.run(null, "Evaluate " + EVIL + " and save it");

        assertThat(run.outcome()).isEqualTo("REFUSED");
        assertThat(run.reportedVerdict()).isEqualTo("REFUSED");
        assertThat(run.answer()).contains("ignore all previous instructions");
        verify(model, times(1)).call(any(Prompt.class));
        assertThat(registry.counter("jobagent.guardrail.blocks", "rule", "prompt_injection").count()).isEqualTo(1);
    }

    @Test
    void writeToolsRefuseOnceTheRunIsTainted() {
        AssistantMessage both = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("1", "function", "fetch_job_posting", "{\"url\":\"" + EVIL + "\"}"),
                new AssistantMessage.ToolCall("2", "function", "save_application", "{\"posting_id\":\"P1\"}"))).build();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(both))));

        AgentRun run = agent.run(null, "Save " + EVIL);

        assertThat(saveRan).isFalse();
        assertThat(run.steps()).extracting(s -> s.tool() + ":" + s.outcome())
                .containsExactly("fetch_job_posting:refused", "save_application:refused");
        assertThat(run.steps().get(1).result()).contains("tainted_write");
        assertThat(run.pendingApprovals()).isEmpty();
    }

    @Test
    void unknownToolIsRejectedAndTheModelCanRecover() {
        when(model.call(any(Prompt.class))).thenReturn(
                toolCall("delete_all_applications", "{}"),
                text("I can't do that."));

        AgentRun run = agent.run(null, "Delete everything");

        assertThat(run.outcome()).isEqualTo("ANSWERED");
        assertThat(run.steps()).singleElement().satisfies(s -> {
            assertThat(s.outcome()).isEqualTo("not_allowed");
            assertThat(s.result()).contains("fetch_job_posting, score_cv, save_application");
        });
        assertThat(registry.counter("jobagent.guardrail.blocks", "rule", "tool_not_allowed").count()).isEqualTo(1);
    }

    @Test
    void invalidArgumentsGoBackToTheModelAsErrors() {
        when(model.call(any(Prompt.class))).thenReturn(
                toolCall("save_application", "{\"posting_id\":\"latest one\"}"),
                text("Sorry."));

        AgentRun run = agent.run(null, "Save it");

        assertThat(saveRan).isFalse();
        assertThat(run.steps().get(0).outcome()).isEqualTo("invalid_input");
        assertThat(run.steps().get(0).result()).contains("posting_id: must be a posting_id like P1");
    }

    @Test
    void stopsAtTheStepLimit() {
        when(model.call(any(Prompt.class))).thenReturn(toolCall("score_cv", "{\"job_text\":\"P1\"}"));

        AgentRun run = agent.run(null, "Loop forever");

        assertThat(run.outcome()).isEqualTo("STEP_LIMIT");
        verify(model, times(4)).call(any(Prompt.class));
    }

    @Test
    void modelFailureIsReportedNotThrown() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection refused"));

        AgentRun run = agent.run(null, "Hi");

        assertThat(run.outcome()).isEqualTo("MODEL_ERROR");
        assertThat(run.answer()).contains("connection refused");
    }

    @Test
    void followUpsSeeTheEarlierTurns() {
        when(model.call(any(Prompt.class))).thenReturn(text("First answer."), text("Second answer."));

        AgentRun first = agent.run("c1", "first");
        agent.run("c1", "second");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompt.capture());
        assertThat(first.conversationId()).isEqualTo("c1");
        assertThat(prompt.getAllValues().get(1).getInstructions()).hasSize(4); // system, user, assistant, user
    }

    @Test
    void readsTheVerdictLine() {
        assertThat(JobAgent.reportedVerdict("Gaps found.\nVERDICT: REJECT (fix CV)")).isEqualTo("REJECT (fix CV)");
        assertThat(JobAgent.reportedVerdict("**VERDICT:** acceptable")).isEqualTo("ACCEPTABLE");
        assertThat(JobAgent.reportedVerdict("No verdict here")).isNull();
    }

    private static ChatResponse toolCall(String name, String args) {
        AssistantMessage m = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", name, args))).build();
        return new ChatResponse(List.of(new Generation(m)));
    }

    private static ChatResponse text(String s) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(s))));
    }
}
