import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paycoreApi } from '../api/paycoreApi';
import { Landmark, Play } from 'lucide-react';

export function PayoutsPage() {
  const queryClient = useQueryClient();
  const [selectedBatchId, setSelectedBatchId] = useState<string | null>(null);

  const { data: batches = [] } = useQuery({
    queryKey: ['batches'],
    queryFn: () => paycoreApi.getBatches(),
  });

  const { data: batchRecords = [] } = useQuery({
    queryKey: ['batchRecords', selectedBatchId],
    queryFn: () =>
      selectedBatchId ? paycoreApi.getBatchRecords(selectedBatchId) : Promise.resolve([]),
    enabled: !!selectedBatchId,
  });

  const triggerCutoffMutation = useMutation({
    mutationFn: () => paycoreApi.triggerBatchCutoff(),
    onSuccess: (newBatch) => {
      queryClient.invalidateQueries({ queryKey: ['batches'] });
      setSelectedBatchId(newBatch.batchId);
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            Batches & Merchant Payouts
          </h2>
          <p className="text-sm text-slate-500">
            Cutoff window execution, payout aggregation, and bank file dispatch
          </p>
        </div>

        <button
          onClick={() => triggerCutoffMutation.mutate()}
          disabled={triggerCutoffMutation.isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 disabled:opacity-50"
        >
          <Play className="h-4 w-4" />
          {triggerCutoffMutation.isPending ? 'Executing Cutoff...' : 'Trigger Batch Cutoff'}
        </button>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Batches Table */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900 mb-3 flex items-center gap-2">
            <Landmark className="h-4 w-4 text-indigo-600" />
            Cutoff Batches
          </h3>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
              <thead className="bg-slate-50 font-semibold text-slate-700">
                <tr>
                  <th className="px-3 py-2">Batch ID</th>
                  <th className="px-3 py-2">Records</th>
                  <th className="px-3 py-2">Net Total</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {batches.map((b) => (
                  <tr
                    key={b.batchId}
                    onClick={() => setSelectedBatchId(b.batchId)}
                    className={`cursor-pointer transition-colors ${
                      selectedBatchId === b.batchId
                        ? 'bg-indigo-50 font-semibold text-indigo-900'
                        : 'hover:bg-slate-50'
                    }`}
                  >
                    <td className="px-3 py-2 font-mono">{b.batchId}</td>
                    <td className="px-3 py-2">{b.totalCount}</td>
                    <td className="px-3 py-2 font-bold text-slate-900">
                      {(b.totalNetPaise / 100).toFixed(2)} INR
                    </td>
                    <td className="px-3 py-2">
                      <span className="rounded bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700 border border-emerald-200">
                        {b.status}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-slate-400">
                      {new Date(b.createdAt).toLocaleTimeString()}
                    </td>
                  </tr>
                ))}
                {batches.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-400">
                      No batches created yet. Click Trigger Batch Cutoff.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Batch Records Drilldown */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900 mb-3">
            {selectedBatchId ? `Records in ${selectedBatchId}` : 'Batch Items Detail'}
          </h3>

          <div className="max-h-96 overflow-y-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
              <thead className="bg-slate-50 font-semibold text-slate-700">
                <tr>
                  <th className="px-3 py-2">Payment ID</th>
                  <th className="px-3 py-2">Seller ID</th>
                  <th className="px-3 py-2">Gross</th>
                  <th className="px-3 py-2">Fee</th>
                  <th className="px-3 py-2">Net</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {batchRecords.map((r) => (
                  <tr key={r.recordId} className="hover:bg-slate-50">
                    <td className="px-3 py-2 font-mono">{r.paymentId}</td>
                    <td className="px-3 py-2 font-medium text-slate-900">{r.sellerId}</td>
                    <td className="px-3 py-2">{(r.grossPaise / 100).toFixed(2)}</td>
                    <td className="px-3 py-2 text-slate-400">{(r.feePaise / 100).toFixed(2)}</td>
                    <td className="px-3 py-2 font-bold text-emerald-700">
                      {(r.netPaise / 100).toFixed(2)} INR
                    </td>
                  </tr>
                ))}
                {selectedBatchId && batchRecords.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-400">
                      No records bound to this batch.
                    </td>
                  </tr>
                )}
                {!selectedBatchId && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-400">
                      Select a batch from the table on the left to inspect included records.
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
