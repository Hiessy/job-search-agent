package com.hiessy.jobagent.agent;

import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.guardrails.AgentRunContext;
import com.hiessy.jobagent.guardrails.AgentToolbox;
import com.hiessy.jobagent.observability.AgentMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;

/**
 * The agent loop. The model sees the tool definitions and decides on its own which to call; this
 * class runs each requested call through the {@link AgentToolbox} (allowlist and guardrails), hands
 * the results back, and repeats until the model answers in plain text or the step limit is reached.
 *
 * <p>Spring AI's built-in tool execution is turned off so the loop can enforce a step limit, keep a
 * trace, and stop at once when a posting turns out to carry a prompt injection.
 */
@Service
public class JobAgent {

    private static final Logger log = LoggerFactory.getLogger(JobAgent.class);
    private static final Pattern VERDICT = Pattern.compile(
            "VERDICT:\\s*\\**\\s*(REJECT \\(fix CV\\)|STRONG|ACCEPTABLE|WEAK|REJECT|REFUSED)", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final AgentToolbox toolbox;
    private final AgentMetrics metrics;
    private final ConversationStore conversations;
    private final int maxSteps;

    public JobAgent(ChatModel chatModel, AgentToolbox toolbox, AgentMetrics metrics, ConversationStore conversations,
                    JobAgentProperties props) {
        this.chatModel = chatModel;
        this.toolbox = toolbox;
        this.metrics = metrics;
        this.conversations = conversations;
        this.maxSteps = props.agent().maxSteps();
    }

    public AgentRun run(String conversationId, String userMessage) {
        String convId = conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId;
        long start = System.nanoTime();
        AgentRunContext run = AgentRunContext.forAgent();
        List<Message> history = conversations.history(convId);
        history.add(new UserMessage(userMessage));
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolbox.callbacks())
                .internalToolExecutionEnabled(false)
                .build();

        String outcome = "STEP_LIMIT";
        String answer = "I stopped after " + maxSteps + " steps without finishing. Try a narrower request.";
        String model = "unknown";
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            for (int step = 0; step < maxSteps; step++) {
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(AgentPrompts.SYSTEM));
                messages.addAll(history);
                long t0 = System.nanoTime();
                ChatResponse response = chatModel.call(new Prompt(messages, options));
                model = modelName(response, model);
                Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
                metrics.llmCall(model, System.nanoTime() - t0, usage);
                if (usage != null) {
                    promptTokens += nz(usage.getPromptTokens());
                    completionTokens += nz(usage.getCompletionTokens());
                }
                AssistantMessage reply = response.getResult().getOutput();
                history.add(reply);
                if (!reply.hasToolCalls()) {
                    outcome = "ANSWERED";
                    answer = reply.getText() == null ? "" : reply.getText().strip();
                    break;
                }
                List<ToolResponseMessage.ToolResponse> results = new ArrayList<>();
                for (AssistantMessage.ToolCall call : reply.getToolCalls()) {
                    log.debug("Run {} step {}: {}({})", run.runId(), step, call.name(), call.arguments());
                    String result = toolbox.execute(call.name(), call.arguments(), run);
                    results.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
                }
                history.add(ToolResponseMessage.builder().responses(results).build());
                if (run.tainted()) {
                    // Refusal path: answer without asking the model, so injected text can't steer the reply.
                    outcome = "REFUSED";
                    answer = AgentPrompts.refusal(run.taintReason());
                    history.add(new AssistantMessage(answer));
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Model call failed in run {}", run.runId(), e);
            outcome = "MODEL_ERROR";
            answer = "The model call failed: " + e.getMessage();
        }
        if ("STEP_LIMIT".equals(outcome)) {
            metrics.guardrail("step_limit");
            history.add(new AssistantMessage(answer));
        }
        conversations.save(convId, history);
        metrics.run(outcome.toLowerCase());
        return new AgentRun(convId, run.runId(), outcome, answer, reportedVerdict(answer), run.verdict(), run.steps(),
                run.approvals(), promptTokens, completionTokens, (System.nanoTime() - start) / 1_000_000, model);
    }

    static String reportedVerdict(String answer) {
        if (answer == null) {
            return null;
        }
        Matcher m = VERDICT.matcher(answer);
        String last = null;
        while (m.find()) {
            last = m.group(1).toUpperCase();
        }
        return "REJECT (FIX CV)".equals(last) ? "REJECT (fix CV)" : last;
    }

    private static String modelName(ChatResponse response, String fallback) {
        String m = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return m == null || m.isBlank() ? fallback : m;
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
