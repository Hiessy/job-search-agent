package com.hiessy.jobagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails at startup with a plain message when a paid provider is selected without its key. Keys come
 * from environment variables only (ANTHROPIC_API_KEY, OPENAI_API_KEY); nothing in the repo holds one.
 */
@Component
class ProviderCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProviderCheck.class);
    private final Environment env;

    ProviderCheck(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        String provider = env.getProperty("spring.ai.model.chat", "ollama");
        String key = switch (provider) {
            case "anthropic" -> "spring.ai.anthropic.api-key";
            case "openai" -> "spring.ai.openai.api-key";
            default -> null;
        };
        if (key != null && env.getProperty(key, "").isBlank()) {
            throw new IllegalStateException("JOBAGENT_PROVIDER=" + provider + " needs "
                    + ("anthropic".equals(provider) ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY") + " set in the environment");
        }
        log.info("LLM provider: {}", provider);
    }
}
