package com.hiessy.jobagent.config;

import com.hiessy.jobagent.guardrails.AgentToolbox;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serves the same guarded tools to MCP clients (Claude Code, Claude Desktop, IDEs) over streamable
 * HTTP at /mcp. Same allowlist, validation and approval step as the agent: save_application from an
 * MCP client also only creates an approval request.
 */
@Configuration
class McpConfig {

    @Bean
    ToolCallbackProvider jobAgentTools(AgentToolbox toolbox) {
        return ToolCallbackProvider.from(toolbox.callbacks());
    }
}
