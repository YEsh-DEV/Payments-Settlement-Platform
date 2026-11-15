package io.github.yesh.paycore.settlement.domain;

public enum BatchStatus {
  OPEN,
  CREATED,
  PAYOUTS_REQUESTED,
  SENT,
  CLOSED,
  RECONCILED,
  SETTLED,
  COMPLETED,
  PARTIAL_FAILURE
}
