import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paycoreApi } from '../api/paycoreApi';
import { useAuth } from '../contexts/AuthContext';
import {
  ArrowLeft,
  Clock,
  FileText,
  AlertCircle,
} from 'lucide-react';

export function CaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user, hasRole } = useAuth();
  const queryClient = useQueryClient();

  const [noteContent, setNoteContent] = useState('');
  const [adjustmentTemplate, setAdjustmentTemplate] = useState<'FEE_VARIANCE' | 'WRITE_OFF'>('FEE_VARIANCE');
  const [adjustmentAmount, setAdjustmentAmount] = useState<number>(0);
  const [adjustmentComment, setAdjustmentComment] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { data: caseItem, isLoading } = useQuery({
    queryKey: ['case', id],
    queryFn: () => paycoreApi.getCase(id!),
    enabled: !!id,
  });

  const { data: notes = [] } = useQuery({
    queryKey: ['caseNotes', id],
    queryFn: () => paycoreApi.getCaseNotes(id!),
    enabled: !!id,
  });

  const addNoteMutation = useMutation({
    mutationFn: (content: string) => paycoreApi.addCaseNote(id!, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['caseNotes', id] });
      setNoteContent('');
    },
  });

  const proposeAdjustmentMutation = useMutation({
    mutationFn: () =>
      paycoreApi.proposeAdjustment({
        caseId: id!,
        template: adjustmentTemplate,
        amountPaise: Math.round(adjustmentAmount * 100),
        comment: adjustmentComment,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', id] });
      queryClient.invalidateQueries({ queryKey: ['caseNotes', id] });
      setAdjustmentComment('');
      setErrorMsg(null);
    },
    onError: (err: any) => {
      setErrorMsg(err.response?.data?.detail || err.message);
    },
  });

  const approveAdjustmentMutation = useMutation({
    mutationFn: (adjId: string) => paycoreApi.approveAdjustment(adjId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['case', id] });
      queryClient.invalidateQueries({ queryKey: ['caseNotes', id] });
      setErrorMsg(null);
    },
    onError: (err: any) => {
      setErrorMsg(err.response?.data?.detail || err.message);
    },
  });

  if (isLoading || !caseItem) {
    return <div className="py-12 text-center text-slate-500">Loading case details...</div>;
  }

  const isProposer = user?.username === caseItem.proposedBy;
  const canApprove = hasRole('ADMIN') && !isProposer && caseItem.status === 'IN_REVIEW';

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Link to="/cases" className="rounded-lg border border-slate-200 bg-white p-2 text-slate-600 hover:bg-slate-50 shadow-xs">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold text-slate-900">{caseItem.caseId}</h2>
            <span className="rounded-full bg-indigo-50 border border-indigo-200 px-2.5 py-0.5 text-xs font-bold text-indigo-700">
              {caseItem.status}
            </span>
            <span className="rounded-full bg-slate-100 border border-slate-200 px-2.5 py-0.5 text-xs font-semibold text-slate-700">
              {caseItem.severity}
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Reconciliation Exception Type: <span className="font-semibold text-slate-700">{caseItem.type}</span> | SLA Deadline:{' '}
            {new Date(caseItem.slaDeadline).toLocaleString()}
          </p>
        </div>
      </div>

      {errorMsg && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-xs font-medium text-red-800 flex items-center justify-between">
          <span>{errorMsg}</span>
          <button onClick={() => setErrorMsg(null)} className="text-red-600 hover:text-red-900">
            Dismiss
          </button>
        </div>
      )}

      {/* Grid: Evidence Panel & Timeline */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Evidence Panel (Left) */}
        <div className="space-y-6 lg:col-span-1">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs space-y-4">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <FileText className="h-4 w-4 text-indigo-600" />
              Evidence & Linkages
            </h3>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Variance Amount:</span>
                <span className="font-bold text-slate-900">{(caseItem.variancePaise / 100).toFixed(2)} INR</span>
              </div>
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Ledger Record:</span>
                <span className="font-mono text-slate-700">{caseItem.recordId || 'None (Missing)'}</span>
              </div>
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Provider Line ID:</span>
                <span className="font-mono text-slate-700">{caseItem.providerLineId || 'None (Missing)'}</span>
              </div>
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Bank Deposit ID:</span>
                <span className="font-mono text-slate-700">{caseItem.depositId || 'None'}</span>
              </div>
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Assignee:</span>
                <span className="font-medium text-slate-700">{caseItem.assignedTo || 'Unassigned'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Version:</span>
                <span className="font-medium text-slate-700">v{caseItem.version}</span>
              </div>
            </div>
          </div>

          {/* Maker-Checker Adjustment Form */}
          {caseItem.status !== 'RESOLVED' && caseItem.status !== 'WRITTEN_OFF' && (
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs space-y-4">
              <h3 className="text-sm font-bold text-slate-900">Propose Adjustment</h3>
              <p className="text-[11px] text-slate-500">
                Fixed double-entry posting template with mandatory dual-control maker-checker
              </p>

              <div className="space-y-3 text-xs">
                <div>
                  <label className="font-medium text-slate-700 block mb-1">Template</label>
                  <select
                    value={adjustmentTemplate}
                    onChange={(e) => setAdjustmentTemplate(e.target.value as any)}
                    className="w-full rounded-md border border-slate-300 p-2 text-xs"
                  >
                    <option value="FEE_VARIANCE">FEE_VARIANCE (DR Provider Fee, CR Provider Rec)</option>
                    <option value="WRITE_OFF">WRITE_OFF (DR Write Off Exp, CR Provider Rec)</option>
                  </select>
                </div>

                <div>
                  <label className="font-medium text-slate-700 block mb-1">Amount (INR)</label>
                  <input
                    type="number"
                    step="0.01"
                    value={adjustmentAmount || ''}
                    onChange={(e) => setAdjustmentAmount(parseFloat(e.target.value) || 0)}
                    placeholder={(caseItem.variancePaise / 100).toFixed(2)}
                    className="w-full rounded-md border border-slate-300 p-2 text-xs font-mono"
                  />
                </div>

                <div>
                  <label className="font-medium text-slate-700 block mb-1">Audit Comment</label>
                  <textarea
                    rows={2}
                    value={adjustmentComment}
                    onChange={(e) => setAdjustmentComment(e.target.value)}
                    placeholder="Justification for adjustment..."
                    className="w-full rounded-md border border-slate-300 p-2 text-xs"
                  />
                </div>

                <button
                  onClick={() => proposeAdjustmentMutation.mutate()}
                  disabled={adjustmentAmount <= 0}
                  className="w-full rounded-md bg-indigo-600 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
                >
                  Propose Adjustment
                </button>
              </div>
            </div>
          )}

          {/* Maker-Checker Approval Box (For ADMIN when proposed) */}
          {caseItem.proposedAction && caseItem.status === 'IN_REVIEW' && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 shadow-xs space-y-3">
              <div className="flex items-center gap-2 text-amber-900 font-bold text-xs">
                <AlertCircle className="h-4 w-4" />
                Dual-Control Approval Pending
              </div>
              <p className="text-[11px] text-amber-800">
                Action: <span className="font-semibold">{caseItem.proposedAction}</span> proposed by{' '}
                <span className="font-semibold">{caseItem.proposedBy}</span>.
              </p>
              {isProposer && (
                <p className="text-[10px] text-amber-700 italic">
                  Rule: Proposers cannot approve their own adjustment. Another administrator must review.
                </p>
              )}
              {canApprove && (
                <button
                  onClick={() => approveAdjustmentMutation.mutate(caseItem.caseId)}
                  className="w-full rounded-md bg-emerald-600 py-2 text-xs font-semibold text-white hover:bg-emerald-700"
                >
                  Approve & Post to Ledger
                </button>
              )}
            </div>
          )}
        </div>

        {/* Timeline Notes (Right) */}
        <div className="lg:col-span-2 space-y-4">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
            <h3 className="text-sm font-bold text-slate-900 mb-4 flex items-center gap-2">
              <Clock className="h-4 w-4 text-slate-500" />
              Audit Timeline & Notes
            </h3>

            <div className="space-y-4 min-h-[300px]">
              {notes.map((n) => (
                <div key={n.id} className="border-l-2 border-indigo-200 pl-4 py-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-900">{n.author}</span>
                    <span className="text-[10px] text-slate-400">
                      {new Date(n.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <p className="text-xs text-slate-600 mt-1">{n.content}</p>
                </div>
              ))}
              {notes.length === 0 && (
                <div className="py-12 text-center text-xs text-slate-400">No notes recorded yet.</div>
              )}
            </div>

            {/* Add note input */}
            <div className="mt-4 pt-4 border-t border-slate-100 flex gap-2">
              <input
                type="text"
                value={noteContent}
                onChange={(e) => setNoteContent(e.target.value)}
                placeholder="Append comment or audit note..."
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-xs"
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && noteContent.trim()) {
                    addNoteMutation.mutate(noteContent);
                  }
                }}
              />
              <button
                onClick={() => noteContent.trim() && addNoteMutation.mutate(noteContent)}
                className="rounded-md bg-slate-900 px-4 py-2 text-xs font-semibold text-white hover:bg-slate-800"
              >
                Send
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
