package com.hiessy.jobagent.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 * A write a tool asked for. It stays PENDING until a person approves or rejects it through
 * /api/approvals; the agent itself has no way to approve.
 */
@Entity
@Table(name = "pending_action")
public class PendingAction {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tool;

    @Column(nullable = false, length = 500)
    private String summary;

    /** The write itself, as JSON. */
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "requested_by", nullable = false, length = 16)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "application_id")
    private Long applicationId;

    @Version
    private long version;

    protected PendingAction() {
    }

    public PendingAction(String tool, String summary, String payload, String requestedBy) {
        this.tool = tool;
        this.summary = summary;
        this.payload = payload;
        this.requestedBy = requestedBy;
    }

    void approve(long applicationId) {
        this.status = Status.APPROVED;
        this.applicationId = applicationId;
        this.decidedAt = OffsetDateTime.now();
    }

    void reject() {
        this.status = Status.REJECTED;
        this.decidedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTool() {
        return tool;
    }

    public String getSummary() {
        return summary;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public Long getApplicationId() {
        return applicationId;
    }
}
