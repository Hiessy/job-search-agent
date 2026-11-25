package com.hiessy.jobagent.applications;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findAllByOrderByCreatedAtDesc(Limit limit);

    List<JobApplication> findByStatusOrderByCreatedAtDesc(JobApplication.Status status, Limit limit);

    Optional<JobApplication> findByPostingUrl(String postingUrl);
}
