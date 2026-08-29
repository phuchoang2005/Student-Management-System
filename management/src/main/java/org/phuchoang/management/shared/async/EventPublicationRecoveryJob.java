package org.phuchoang.management.shared.async;

import java.time.Duration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PM-047 / H6's second layer, alongside {@link AsyncConfig#taskExecutor()}'s widened sizing and
 * {@code CallerRunsPolicy}: a publication that ends up incomplete for any reason other than
 * executor rejection (e.g. the process restarting mid-listener) still needs a path back to
 * completion, since nothing else in the codebase ever called {@code
 * IncompleteEventPublications.resubmitIncompletePublications} outside of tests before this.
 *
 * <p>The stated wall-clock window {@code 08-hazard-fix-specs.md}'s IP-06 entry requires: a
 * publication still incomplete after 2 minutes is resubmitted on the next tick of this
 * fixed-60-second-rate job, so the worst-case time to completion for anything this job catches is
 * roughly 2–3 minutes (the 2-minute threshold, plus up to 60 seconds until the next tick, plus
 * resubmission dispatch time) — not unbounded, and never silent.
 */
@Component
public class EventPublicationRecoveryJob {

  private static final Duration INCOMPLETE_THRESHOLD = Duration.ofMinutes(2);

  private final IncompleteEventPublications incompletePublications;

  public EventPublicationRecoveryJob(IncompleteEventPublications incompletePublications) {
    this.incompletePublications = incompletePublications;
  }

  @Scheduled(fixedRate = 60_000)
  public void resubmitStuckPublications() {
    incompletePublications.resubmitIncompletePublicationsOlderThan(INCOMPLETE_THRESHOLD);
  }
}
