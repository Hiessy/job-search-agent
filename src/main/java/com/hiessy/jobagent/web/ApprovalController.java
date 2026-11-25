package com.hiessy.jobagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hiessy.jobagent.applications.ApprovalService;
import com.hiessy.jobagent.applications.JobApplication;
import com.hiessy.jobagent.applications.PendingAction;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The human side of the approval step, and the tracked applications. Not exposed as tools. */
@RestController
@RequestMapping("/api")
class ApprovalController {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ApprovalView(Long id, String tool, String summary, String status, String requestedBy, JsonNode payload,
                        OffsetDateTime createdAt, OffsetDateTime decidedAt, Long applicationId) {
    }

    private final ApprovalService approvals;
    private final ObjectMapper mapper;

    ApprovalController(ApprovalService approvals, ObjectMapper mapper) {
        this.approvals = approvals;
        this.mapper = mapper;
    }

    @GetMapping("/approvals")
    List<ApprovalView> list(@RequestParam(required = false) PendingAction.Status status) {
        return approvals.list(status).stream().map(this::view).toList();
    }

    @PostMapping("/approvals/{id}/approve")
    ApprovalView approve(@PathVariable long id) {
        return view(approvals.approve(id));
    }

    @PostMapping("/approvals/{id}/reject")
    ApprovalView reject(@PathVariable long id) {
        return view(approvals.reject(id));
    }

    @GetMapping("/applications")
    List<JobApplication> applications(@RequestParam(required = false) JobApplication.Status status,
                                      @RequestParam(defaultValue = "50") int limit) {
        return approvals.applications(status, limit);
    }

    private ApprovalView view(PendingAction a) {
        JsonNode payload;
        try {
            payload = mapper.readTree(a.getPayload());
        } catch (Exception e) {
            payload = mapper.getNodeFactory().textNode(a.getPayload());
        }
        return new ApprovalView(a.getId(), a.getTool(), a.getSummary(), a.getStatus().name(), a.getRequestedBy(), payload,
                a.getCreatedAt(), a.getDecidedAt(), a.getApplicationId());
    }
}
