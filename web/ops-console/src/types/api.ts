export type UserRole = 'ADMIN' | 'ANALYST' | 'AUDITOR' | 'MERCHANT';

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  roles: UserRole[];
  active: boolean;
  createdAt: string;
}

export interface UserAdminResponse {
  id: string;
  username: string;
  roles: string[];
  sellerId?: string;
  enabled: boolean;
  createdAt: string;
}

export interface CreateUserPayload {
  username: string;
  password: string;
  roles: string[];
  sellerId?: string;
}

export interface UpdateUserPayload {
  roles?: string[];
  enabled?: boolean;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  details: string;
  ipAddress?: string;
}


export type CaseType =
  | 'AMOUNT_MISMATCH'
  | 'FEE_VARIANCE'
  | 'FEE_MISMATCH'
  | 'DUPLICATE_PROVIDER_LINE'
  | 'MISSING_IN_PROVIDER'
  | 'MISSING_IN_LEDGER'
  | 'DEPOSIT_UNMATCHED';

export type CaseSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type CaseStatus =
  | 'OPEN'
  | 'IN_REVIEW'
  | 'ESCALATED'
  | 'RESOLVED'
  | 'WRITTEN_OFF';

export interface DiscrepancyCase {
  id: string;
  caseId: string;
  runId?: string;
  recordId?: string;
  providerLineId?: number;
  depositId?: string;
  type: CaseType;
  severity: CaseSeverity;
  status: CaseStatus;
  assignedTo?: string;
  proposedAction?: string;
  proposedBy?: string;
  approvedBy?: string;
  variancePaise: number;
  slaDeadline: string;
  version: number;
  createdAt: string;
  resolvedAt?: string;
}

export interface CaseNote {
  id: number;
  caseId: string;
  author: string;
  content: string;
  createdAt: string;
}

export interface SettlementBatch {
  id: string;
  batchId: string;
  cutoffWindow: string;
  status: string;
  totalCount: number;
  totalGrossPaise: number;
  totalFeePaise: number;
  totalNetPaise: number;
  createdAt: string;
  closedAt?: string;
}

export interface SettlementRecord {
  id: string;
  recordId: string;
  batchId?: string;
  paymentId: string;
  sellerId: string;
  grossPaise: number;
  feePaise: number;
  netPaise: number;
  status: string;
  occurredAt: string;
  createdAt: string;
}

export interface ReconciliationRun {
  id: string;
  runId: string;
  batchId?: string;
  fileId?: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  matchedCount: number;
  discrepancyCount: number;
  unmatchedCount: number;
  totalVariancePaise: number;
  precisionPct?: number;
  recallPct?: number;
  startedAt: string;
  completedAt?: string;
}

export interface ReconciliationMatch {
  id: number;
  runId: string;
  recordId: string;
  providerLineId?: number;
  bankDepositId?: string;
  stage: string;
  rule: string;
  confidence: number;
  evidenceJson?: string;
  matchedAmountPaise: number;
  variancePaise: number;
  createdAt: string;
}

export interface AccountBalance {
  code: string;
  name: string;
  type: string;
  currency: string;
  balancePaise: number;
  lockedBalancePaise: number;
  availableBalancePaise: number;
}

export interface StatementEntry {
  id: number;
  journalId: string;
  type: 'DEBIT' | 'CREDIT';
  amountPaise: number;
  runningBalancePaise: number;
  description?: string;
  createdAt: string;
}

export interface SettlementAdjustment {
  id: string;
  adjustmentId: string;
  caseId: string;
  template: 'FEE_VARIANCE' | 'WRITE_OFF';
  debitAccount: string;
  creditAccount: string;
  amountPaise: number;
  status: 'PROPOSED' | 'APPROVED' | 'POSTED' | 'REJECTED';
  proposedBy: string;
  approvedBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SystemHealth {
  status: 'UP' | 'DOWN';
  activeStrategy: string;
  invariantsPassing: boolean;
  crashRecoveryVerified: boolean;
  benchmarkReportUrl: string;
  database: {
    identity: string;
    ledger: string;
    settlement: string;
  };
}
