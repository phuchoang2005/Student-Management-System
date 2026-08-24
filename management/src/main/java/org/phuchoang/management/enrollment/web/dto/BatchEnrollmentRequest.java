package org.phuchoang.management.enrollment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One student, up to 50 courses. The container-element constraints on {@code courseCodes} are
 * validated as part of the {@code @Valid} cascade and report paths like {@code courseCodes[2]},
 * which {@code GlobalExceptionHandler} already folds into the standard {@code ValidationError}
 * envelope — no new error shape for the batch.
 *
 * <p>The cap is 50 because each course commits its own transaction (see {@code
 * EnrollmentBatchService}), so the request costs one round trip per course; past that a client
 * should be making more than one request.
 */
public record BatchEnrollmentRequest(
    @NotBlank @Size(max = 20) String studentCode,
    @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 20) String> courseCodes) {}
