import client from './client';
import type {
  DiscrepancyCase,
  CaseNote,
  SettlementBatch,
  SettlementRecord,
  ReconciliationRun,
  ReconciliationMatch,
  AccountBalance,
  StatementEntry,
  SettlementAdjustment,
  User,
} from '../types/api';

export const paycoreApi = {
  // --- Auth ---
  login: async (credentials: { username: string; password: string }) => {
    const res = await client.post('/auth/login', credentials);
    return res.data;
  },

  refresh: async () => {
    const res = await client.post(
      '/auth/refresh',
      {},
      { headers: { 'X-Requested-With': 'paycore-console' } }
    );
    return res.data;
  },

  logout: async () => {
    await client.post('/auth/logout');
  },

  // --- Cases ---
  getCases: async (status?: string): Promise<DiscrepancyCase[]> => {
    const params = status ? { status } : {};
    const res = await client.get('/cases', { params });
    return res.data.content || res.data || [];
  },

  getCase: async (caseId: string): Promise<DiscrepancyCase> => {
    const res = await client.get(`/cases/${caseId}`);
    return res.data;
  },

  assignCase: async (caseId: string, assignedTo: string, version: number): Promise<DiscrepancyCase> => {
    const res = await client.post(`/cases/${caseId}/assign`, { assignedTo, version });
    return res.data;
  },

  transitionCase: async (
    caseId: string,
    newStatus: string,
    version: number,
    comment?: string
  ): Promise<DiscrepancyCase> => {
    const res = await client.post(`/cases/${caseId}/transition`, { newStatus, version, comment });
    return res.data;
  },

  getCaseNotes: async (caseId: string): Promise<CaseNote[]> => {
    const res = await client.get(`/cases/${caseId}/notes`);
    return res.data;
  },

  addCaseNote: async (caseId: string, content: string): Promise<CaseNote> => {
    const res = await client.post(`/cases/${caseId}/notes`, { content });
    return res.data;
  },

  // --- Adjustments ---
  proposeAdjustment: async (data: {
    caseId: string;
    template: 'FEE_VARIANCE' | 'WRITE_OFF';
    amountPaise: number;
    comment?: string;
  }): Promise<SettlementAdjustment> => {
    const res = await client.post('/adjustments/propose', data);
    return res.data;
  },

  approveAdjustment: async (adjustmentId: string): Promise<SettlementAdjustment> => {
    const res = await client.post(`/adjustments/${adjustmentId}/approve`);
    return res.data;
  },

  rejectAdjustment: async (adjustmentId: string, reason?: string): Promise<SettlementAdjustment> => {
    const res = await client.post(`/adjustments/${adjustmentId}/reject`, null, {
      params: { reason },
    });
    return res.data;
  },

  // --- Reconciliation ---
  getReconRuns: async (): Promise<ReconciliationRun[]> => {
    const res = await client.get('/recon/runs');
    return res.data.content || res.data || [];
  },

  getReconRun: async (runId: string): Promise<ReconciliationRun> => {
    const res = await client.get(`/recon/runs/${runId}`);
    return res.data;
  },

  getReconMatches: async (runId: string): Promise<ReconciliationMatch[]> => {
    const res = await client.get(`/recon/runs/${runId}/matches`);
    return res.data.content || res.data || [];
  },

  triggerReconRun: async (batchId?: string): Promise<ReconciliationRun> => {
    const res = await client.post('/recon/runs', { batchId });
    return res.data;
  },

  uploadSettlementFile: async (formData: FormData): Promise<unknown> => {
    const res = await client.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },

  // --- Batches & Payouts ---
  getBatches: async (): Promise<SettlementBatch[]> => {
    const res = await client.get('/batches');
    return res.data.content || res.data || [];
  },

  getBatch: async (batchId: string): Promise<SettlementBatch> => {
    const res = await client.get(`/batches/${batchId}`);
    return res.data;
  },

  getBatchRecords: async (batchId: string): Promise<SettlementRecord[]> => {
    const res = await client.get(`/batches/${batchId}/records`);
    return res.data.content || res.data || [];
  },

  triggerBatchCutoff: async (): Promise<SettlementBatch> => {
    const res = await client.post('/batches/run', {});
    return res.data;
  },

  // --- Ledger ---
  getAccount: async (code: string): Promise<AccountBalance> => {
    const res = await client.get(`/accounts/${code}`);
    return res.data;
  },

  getStatement: async (code: string): Promise<StatementEntry[]> => {
    const res = await client.get(`/accounts/${code}/statement`);
    return res.data.content || res.data || [];
  },

  // --- Users & Audit ---
  getUsers: async (): Promise<User[]> => {
    const res = await client.get('/admin/users');
    return res.data.content || res.data || [];
  },

  createUser: async (user: Partial<User> & { password: string }): Promise<User> => {
    const res = await client.post('/admin/users', user);
    return res.data;
  },

  getAuditLogs: async (): Promise<unknown[]> => {
    const res = await client.get('/admin/audit');
    return res.data.content || res.data || [];
  },
};
