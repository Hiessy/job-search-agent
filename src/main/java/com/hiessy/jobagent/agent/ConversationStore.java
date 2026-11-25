package com.hiessy.jobagent.agent;

import com.hiessy.jobagent.config.JobAgentProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * In-memory chat history per conversation id, so the user can follow up ("save it", "draft the note").
 * Keeps the 100 most recent conversations; older turns are cut at a user message so tool calls and
 * their results never get separated.
 */
@Component
class ConversationStore {

    private static final int MAX_CONVERSATIONS = 100;
    private final int maxMessages;
    private final Map<String, List<Message>> conversations = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
            return size() > MAX_CONVERSATIONS;
        }
    };

    ConversationStore(JobAgentProperties props) {
        this.maxMessages = props.agent().maxHistoryMessages();
    }

    /** History without the system message. */
    synchronized List<Message> history(String conversationId) {
        return new ArrayList<>(conversations.getOrDefault(conversationId, List.of()));
    }

    synchronized void save(String conversationId, List<Message> messages) {
        List<Message> kept = new ArrayList<>(messages);
        while (kept.size() > maxMessages) {
            int next = 1;
            while (next < kept.size() && !(kept.get(next) instanceof UserMessage)) {
                next++;
            }
            kept = new ArrayList<>(kept.subList(Math.min(next, kept.size()), kept.size()));
        }
        conversations.put(conversationId, kept);
    }
}
