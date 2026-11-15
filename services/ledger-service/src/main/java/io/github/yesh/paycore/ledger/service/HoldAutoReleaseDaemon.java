package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.ledger.domain.Hold;
import io.github.yesh.paycore.ledger.domain.HoldStatus;
import io.github.yesh.paycore.ledger.repository.HoldRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background daemon that sweeps and auto-releases expired authorization holds. Runs once every
 * minute.
 */
@Component
public class HoldAutoReleaseDaemon {

  private static final Logger log = LoggerFactory.getLogger(HoldAutoReleaseDaemon.class);

  private final HoldRepository holdRepository;
  private final HoldService holdService;

  public HoldAutoReleaseDaemon(HoldRepository holdRepository, HoldService holdService) {
    this.holdRepository = holdRepository;
    this.holdService = holdService;
  }

  @Scheduled(fixedDelayString = "${paycore.ledger.holds.sweep-interval-ms:60000}")
  public int sweepExpiredHolds() {
    Instant now = Instant.now();
    List<Hold> expiredHolds = holdRepository.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);

    if (expiredHolds.isEmpty()) {
      return 0;
    }

    log.info("Found {} expired authorization holds. Auto-releasing...", expiredHolds.size());
    int releasedCount = 0;

    for (Hold hold : expiredHolds) {
      try {
        holdService.releaseHold(hold.getHoldId());
        releasedCount++;
      } catch (Exception ex) {
        log.error("Failed to auto-release hold id={}: {}", hold.getHoldId(), ex.getMessage());
      }
    }

    log.info("Successfully auto-released {} expired authorization holds", releasedCount);
    return releasedCount;
  }
}
