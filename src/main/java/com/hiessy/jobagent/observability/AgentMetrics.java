package com.hiessy.jobagent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * Agent metrics, served at /actuator/metrics and /actuator/prometheus:
 * <ul>
 *   <li>{@code jobagent.tool.latency} timer: tool, outcome, channel (agent or mcp)</li>
 *   <li>{@code jobagent.tool.failures} counter: tool, reason (invalid_input, refused, error, not_allowed...)</li>
 *   <li>{@code jobagent.llm.latency} timer and {@code jobagent.llm.tokens} counter (type prompt or completion)</li>
 *   <li>{@code jobagent.agent.runs} counter: outcome</li>
 *   <li>{@code jobagent.guardrail.blocks} counter: rule</li>
 * </ul>
 * Spring AI adds its own {@code gen_ai.client.*} observations next to these.
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void toolCall(String tool, String outcome, String channel, long nanos) {
        Timer.builder("jobagent.tool.latency")
                .description("Tool call latency")
                .tags("tool", tool, "outcome", outcome, "channel", channel)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(nanos));
        if (!outcome.equals("ok") && !outcome.equals("pending_approval")) {
            Counter.builder("jobagent.tool.failures").description("Tool calls that did not succeed")
                    .tags("tool", tool, "reason", outcome).register(registry).increment();
        }
    }

    public void guardrail(String rule) {
        Counter.builder("jobagent.guardrail.blocks").description("Calls or content stopped by a guardrail")
                .tag("rule", rule).register(registry).increment();
    }

    public void llmCall(String model, long nanos, Usage usage) {
        Timer.builder("jobagent.llm.latency").description("One model call").tag("model", model)
                .register(registry).record(Duration.ofNanos(nanos));
        if (usage != null) {
            tokens(model, "prompt", usage.getPromptTokens());
            tokens(model, "completion", usage.getCompletionTokens());
        }
    }

    public void run(String outcome) {
        Counter.builder("jobagent.agent.runs").description("Agent runs by outcome").tag("outcome", outcome)
                .register(registry).increment();
    }

    private void tokens(String model, String type, Integer count) {
        if (count != null && count > 0) {
            Counter.builder("jobagent.llm.tokens").description("Tokens used").tags("model", model, "type", type)
                    .register(registry).increment(count);
        }
    }
}
