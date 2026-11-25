package com.hiessy.jobagent.applications;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingActionRepository extends JpaRepository<PendingAction, Long> {

    List<PendingAction> findByStatusOrderByCreatedAtDesc(PendingAction.Status status);

    List<PendingAction> findAllByOrderByCreatedAtDesc();

    long countByStatus(PendingAction.Status status);
}
