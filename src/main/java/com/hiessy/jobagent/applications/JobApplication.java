package com.hiessy.jobagent.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A job application the user chose to track. Only {@link ApprovalService} creates these. */
@Entity
@Table(name = "job_application")
public class JobApplication {

    public enum Status { SAVED, APPLIED, INTERVIEW, OFFER, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 200)
    private String role;

    @Column(name = "posting_url", length = 2048)
    private String postingUrl;

    @Column(length = 32)
    private String verdict;

    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.SAVED;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected JobApplication() {
    }

    public JobApplication(String company, String role, String postingUrl, String verdict, Integer score, String notes) {
        this.company = company;
        this.role = role;
        this.postingUrl = postingUrl;
        this.verdict = verdict;
        this.score = score;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public String getPostingUrl() {
        return postingUrl;
    }

    public String getVerdict() {
        return verdict;
    }

    public Integer getScore() {
        return score;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
