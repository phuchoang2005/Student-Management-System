package org.phuchoang.management.enrollment.web.dto;

import java.util.List;

/**
 * Deliberately not the {@code Error}/{@code ValidationError} envelope: those describe a request that
 * failed, and this request succeeded — the per-course {@code status} values <em>are</em> its answer.
 * That is why the endpoint answers 200 even when every course was rejected (api-specification.md §5
 * decision #12).
 *
 * <p>{@code requested} counts distinct courses, so it is below the submitted length when the request
 * repeated one. The counts are denormalised so a client can render "3 of 5 enrolled" without walking
 * {@code results}.
 */
public record BatchEnrollmentResponse(
    String studentCode,
    int requested,
    int enrolled,
    int failed,
    List<BatchEnrollmentResultDto> results) {}
