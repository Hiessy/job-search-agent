package com.hiessy.jobagent.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base for tests that start the whole application on a random port, with PostgreSQL in a container
 * and a scripted model. Subclasses share one Spring context and one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import(TestInfra.class)
public abstract class IntegrationTest {

    private static final Pattern POSTING_ID = Pattern.compile("\"posting_id\":\"(P\\d+)\"");

    @MockitoBean
    protected ChatModel chatModel;

    @LocalServerPort
    protected int port;

    protected String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** The posting_id in the newest tool result of a prompt, as a model would read it. */
    protected static String lastPostingId(Prompt prompt) {
        for (int i = prompt.getInstructions().size() - 1; i >= 0; i--) {
            Message m = prompt.getInstructions().get(i);
            if (m instanceof ToolResponseMessage t) {
                for (ToolResponseMessage.ToolResponse r : t.getResponses()) {
                    Matcher matcher = POSTING_ID.matcher(r.responseData());
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        }
        throw new AssertionError("No posting_id in the conversation so far");
    }
}
