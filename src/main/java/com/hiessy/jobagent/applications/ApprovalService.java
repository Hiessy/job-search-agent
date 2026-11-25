package com.hiessy.jobagent.applications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-in-the-loop writes. Tools can only <em>request</em> a write, which is stored as a
 * {@link PendingAction}; the write runs when a person calls {@link #approve}. There is no tool for
 * approving, so neither the model nor an MCP client can approve its own request.
 */
@Service
public class ApprovalService {

    public static final String SAVE_APPLICATION = "save_application";

    /** What save_application wants written. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SaveRequest(String company, String role, String postingUrl, String verdict, Integer score, String notes) {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    private final PendingActionRepository actions;
    private final JobApplicationRepository applications;
    private final ObjectMapper mapper;

    public ApprovalService(PendingActionRepository actions, JobApplicationRepository applications, ObjectMapper mapper) {
        this.actions = actions;
        this.applications = applications;
        this.mapper = mapper;
    }

    @Transactional
    public PendingAction requestSave(SaveRequest req, String requestedBy) {
        String summary = "Save application: " + req.role() + " at " + req.company()
                + (req.verdict() == null ? "" : " (" + req.verdict() + (req.score() == null ? "" : ", score " + req.score()) + ")");
        return actions.save(new PendingAction(SAVE_APPLICATION, cut(summary, 500), toJson(req), requestedBy));
    }

    @Transactional
    public PendingAction approve(long id) {
        PendingAction action = pending(id);
        if (!SAVE_APPLICATION.equals(action.getTool())) {
            throw new ConflictException("Unknown action type " + action.getTool());
        }
        SaveRequest req = fromJson(action.getPayload());
        if (req.postingUrl() != null) {
            applications.findByPostingUrl(req.postingUrl()).ifPresent(existing -> {
                throw new ConflictException("This posting is already saved as application " + existing.getId());
            });
        }
        JobApplication saved = applications.save(new JobApplication(req.company(), req.role(), req.postingUrl(),
                req.verdict(), req.score(), req.notes()));
        action.approve(saved.getId());
        return action;
    }

    @Transactional
    public PendingAction reject(long id) {
        PendingAction action = pending(id);
        action.reject();
        return action;
    }

    @Transactional(readOnly = true)
    public List<PendingAction> list(PendingAction.Status status) {
        return status == null ? actions.findAllByOrderByCreatedAtDesc() : actions.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return actions.countByStatus(PendingAction.Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<JobApplication> applications(JobApplication.Status status, int limit) {
        Limit l = Limit.of(Math.max(1, Math.min(limit, 200)));
        return status == null ? applications.findAllByOrderByCreatedAtDesc(l)
                : applications.findByStatusOrderByCreatedAtDesc(status, l);
    }

    private PendingAction pending(long id) {
        PendingAction action = actions.findById(id).orElseThrow(() -> new NotFoundException("No approval request " + id));
        if (action.getStatus() != PendingAction.Status.PENDING) {
            throw new ConflictException("Approval request " + id + " is already " + action.getStatus());
        }
        return action;
    }

    private String toJson(SaveRequest req) {
        try {
            return mapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SaveRequest fromJson(String json) {
        try {
            return mapper.readValue(json, SaveRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored payload is not readable", e);
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
