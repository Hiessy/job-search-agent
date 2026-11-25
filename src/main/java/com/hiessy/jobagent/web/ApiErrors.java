package com.hiessy.jobagent.web;

import com.hiessy.jobagent.applications.ApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps service exceptions to RFC 9457 problem responses. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(ApprovalService.NotFoundException.class)
    ProblemDetail notFound(ApprovalService.NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ApprovalService.ConflictException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                e instanceof ApprovalService.ConflictException ? e.getMessage() : "Someone else decided this request first");
    }
}
