import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paycoreApi } from '../api/paycoreApi';
import {
  Upload,
  Play,
  Layers,
} from 'lucide-react';

export function ReconciliationPage() {
  const queryClient = useQueryClient();
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [fileToUpload, setFileToUpload] = useState<File | null>(null);
  const [uploadStatus, setUploadStatus] = useState<string | null>(null);

  const { data: runs = [] } = useQuery({
    queryKey: ['reconRuns'],
    queryFn: () => paycoreApi.getReconRuns(),
  });

  const { data: matches = [] } = useQuery({
    queryKey: ['reconMatches', selectedRunId],
    queryFn: () => (selectedRunId ? paycoreApi.getReconMatches(selectedRunId) : Promise.resolve([])),
    enabled: !!selectedRunId,
  });

  const triggerRunMutation = useMutation({
    mutationFn: () => paycoreApi.triggerReconRun(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['reconRuns'] });
      setSelectedRunId(data.runId);
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('provider', 'RAZORPAY_SIM');
      return paycoreApi.uploadSettlementFile(formData);
    },
    onSuccess: () => {
      setUploadStatus('File ingested successfully (SHA-256 and trailer validated)');
      setFileToUpload(null);
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: { data?: { detail?: string } };
        message?: string;
      };
      setUploadStatus(
        `Upload failed: ${error.response?.data?.detail || error.message || 'Error'}`
      );
    },
  });

  const handleFileUpload = (e: React.FormEvent) => {
    e.preventDefault();
    if (fileToUpload) {
      uploadMutation.mutate(fileToUpload);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            4-Stage Reconciliation Workbench
          </h2>
          <p className="text-sm text-slate-500">
            Exact matching, fuzzy fallback, subset sum composite matching, and ground truth accuracy audit
          </p>
        </div>

        <button
          onClick={() => triggerRunMutation.mutate()}
          disabled={triggerRunMutation.isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 disabled:opacity-50"
        >
          <Play className="h-4 w-4" />
          {triggerRunMutation.isPending ? 'Running Engine...' : 'Run Reconciliation'}
        </button>
      </div>

      {/* Ingestion & Stages Summary Cards */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        {/* File Ingestion */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2 mb-2">
            <Upload className="h-4 w-4 text-indigo-600" />
            Ingest Bank Settlement File
          </h3>
          <p className="text-xs text-slate-500 mb-4">
            Streaming CSV parser with SHA-256 fingerprinting & trailer checksum validation
          </p>

          <form onSubmit={handleFileUpload} className="space-y-3">
            <input
              type="file"
              accept=".csv"
              onChange={(e) => setFileToUpload(e.target.files?.[0] || null)}
              className="w-full text-xs text-slate-600 file:mr-2 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"
            />
            <button
              type="submit"
              disabled={!fileToUpload || uploadMutation.isPending}
              className="w-full rounded-md bg-slate-900 py-2 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {uploadMutation.isPending ? 'Validating Trailer...' : 'Upload & Ingest'}
            </button>
            {uploadStatus && (
              <p className="text-[11px] text-slate-600 font-medium">{uploadStatus}</p>
            )}
          </form>
        </div>

        {/* 4-Stage Engine Overview */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs md:col-span-2">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2 mb-2">
            <Layers className="h-4 w-4 text-indigo-600" />
            4-Stage Engine Pipeline
          </h3>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 mt-3 text-xs">
            <div className="rounded-lg bg-slate-50 p-3 border border-slate-100">
              <span className="font-bold text-indigo-700">Stage 1</span>
              <p className="font-semibold text-slate-900 mt-1">Exact Match</p>
              <p className="text-[11px] text-slate-500 mt-0.5">Joined on provider_txn_id & bps fee model</p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3 border border-slate-100">
              <span className="font-bold text-indigo-700">Stage 2</span>
              <p className="font-semibold text-slate-900 mt-1">Fuzzy Fallback</p>
              <p className="text-[11px] text-slate-500 mt-0.5">Merchant ref & unique ±10 min time window</p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3 border border-slate-100">
              <span className="font-bold text-indigo-700">Stage 3</span>
              <p className="font-semibold text-slate-900 mt-1">Subset Sum DP</p>
              <p className="text-[11px] text-slate-500 mt-0.5">Meet-in-the-middle 1:N deposit resolution</p>
            </div>
            <div className="rounded-lg bg-slate-50 p-3 border border-slate-100">
              <span className="font-bold text-indigo-700">Stage 4</span>
              <p className="font-semibold text-slate-900 mt-1">Ledger Clear</p>
              <p className="text-[11px] text-slate-500 mt-0.5">Posting journals & case escalation</p>
            </div>
          </div>
        </div>
      </div>

      {/* Runs Table & Matches Drilldown */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Runs List */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900 mb-3">Reconciliation Runs</h3>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
              <thead className="bg-slate-50 font-semibold text-slate-700">
                <tr>
                  <th className="px-3 py-2">Run ID</th>
                  <th className="px-3 py-2">Matched</th>
                  <th className="px-3 py-2">Exceptions</th>
                  <th className="px-3 py-2">Precision</th>
                  <th className="px-3 py-2">Recall</th>
                  <th className="px-3 py-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {runs.map((r) => (
                  <tr
                    key={r.runId}
                    onClick={() => setSelectedRunId(r.runId)}
                    className={`cursor-pointer transition-colors ${selectedRunId === r.runId ? 'bg-indigo-50/70 font-semibold text-indigo-900' : 'hover:bg-slate-50'}`}
                  >
                    <td className="px-3 py-2 font-mono">{r.runId}</td>
                    <td className="px-3 py-2 font-semibold text-emerald-600">{r.matchedCount}</td>
                    <td className="px-3 py-2 font-semibold text-amber-600">{r.discrepancyCount}</td>
                    <td className="px-3 py-2">{r.precisionPct != null ? `${r.precisionPct}%` : '100%'}</td>
                    <td className="px-3 py-2">{r.recallPct != null ? `${r.recallPct}%` : '100%'}</td>
                    <td className="px-3 py-2">
                      <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold">
                        {r.status}
                      </span>
                    </td>
                  </tr>
                ))}
                {runs.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-6 text-center text-slate-400">
                      No runs executed yet. Click &apos;Run Reconciliation&apos; to begin.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Selected Run Matches */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900 mb-3">
            {selectedRunId ? `Matches for ${selectedRunId}` : 'Line-Level Match Trail'}
          </h3>

          <div className="max-h-96 overflow-y-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
              <thead className="bg-slate-50 font-semibold text-slate-700">
                <tr>
                  <th className="px-3 py-2">Record</th>
                  <th className="px-3 py-2">Stage</th>
                  <th className="px-3 py-2">Rule</th>
                  <th className="px-3 py-2">Amount</th>
                  <th className="px-3 py-2">Confidence</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {matches.map((m) => (
                  <tr key={m.id} className="hover:bg-slate-50">
                    <td className="px-3 py-2 font-mono">{m.recordId}</td>
                    <td className="px-3 py-2 font-semibold text-slate-800">{m.stage}</td>
                    <td className="px-3 py-2">{m.rule}</td>
                    <td className="px-3 py-2 font-bold text-slate-900">
                      {(m.matchedAmountPaise / 100).toFixed(2)} INR
                    </td>
                    <td className="px-3 py-2">
                      <span className="font-semibold text-indigo-600">
                        {(m.confidence * 100).toFixed(0)}%
                      </span>
                    </td>
                  </tr>
                ))}
                {selectedRunId && matches.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-400">
                      No match entries recorded for this run.
                    </td>
                  </tr>
                )}
                {!selectedRunId && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-400">
                      Select a run from the table on the left to inspect matched lines.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
