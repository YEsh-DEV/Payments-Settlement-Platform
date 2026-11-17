import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paycoreApi } from '../api/paycoreApi';
import type { DiscrepancyCase, CaseStatus, CaseSeverity } from '../types/api';
import { Link } from 'react-router-dom';
import {
  Clock,
  LayoutGrid,
  List as ListIcon,
  ChevronRight,
} from 'lucide-react';

const COLUMNS: { id: CaseStatus; title: string }[] = [
  { id: 'OPEN', title: 'Open' },
  { id: 'IN_REVIEW', title: 'In Review' },
  { id: 'ESCALATED', title: 'Escalated' },
  { id: 'RESOLVED', title: 'Resolved' },
];

export function CasesPage() {
  const [viewMode, setViewMode] = useState<'kanban' | 'list'>('kanban');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const queryClient = useQueryClient();

  const { data: cases = [] } = useQuery({
    queryKey: ['cases'],
    queryFn: () => paycoreApi.getCases(),
  });

  const transitionMutation = useMutation({
    mutationFn: ({
      caseId,
      newStatus,
      version,
    }: {
      caseId: string;
      newStatus: CaseStatus;
      version: number;
    }) => paycoreApi.transitionCase(caseId, newStatus, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cases'] });
      setErrorMessage(null);
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: { data?: { detail?: string; message?: string } };
        message?: string;
      };
      const msg =
        error.response?.data?.detail ||
        error.response?.data?.message ||
        error.message ||
        'Error';
      setErrorMessage(`Transition rejected: ${msg}`);
    },
  });

  const filteredCases = cases.filter((c) => {
    if (severityFilter !== 'ALL' && c.severity !== severityFilter) {
      return false;
    }
    return true;
  });

  const handleStatusChange = (c: DiscrepancyCase, newStatus: CaseStatus) => {
    if (c.status === newStatus) return;
    transitionMutation.mutate({
      caseId: c.caseId,
      newStatus,
      version: c.version,
    });
  };

  const getSeverityBadge = (sev: CaseSeverity) => {
    switch (sev) {
      case 'CRITICAL':
        return 'bg-red-50 text-red-700 border-red-200';
      case 'HIGH':
        return 'bg-orange-50 text-orange-700 border-orange-200';
      case 'MEDIUM':
        return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'LOW':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header and Controls */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            Cases & Exceptions Management
          </h2>
          <p className="text-sm text-slate-500">
            Reconciliation discrepancies, SLA escalations, and maker-checker audit workflow
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Severity Filter */}
          <select
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 shadow-xs focus:border-indigo-500 focus:outline-hidden"
          >
            <option value="ALL">All Severities</option>
            <option value="CRITICAL">Critical (&gt; Rs 50k)</option>
            <option value="HIGH">High (&gt; Rs 10k)</option>
            <option value="MEDIUM">Medium (&gt; Rs 1k)</option>
            <option value="LOW">Low (&lt; Rs 1k)</option>
          </select>

          {/* View Toggle */}
          <div className="flex rounded-lg border border-slate-200 bg-white p-0.5 shadow-xs">
            <button
              onClick={() => setViewMode('kanban')}
              className={`p-1.5 rounded-md ${viewMode === 'kanban' ? 'bg-indigo-50 text-indigo-600' : 'text-slate-500 hover:text-slate-900'}`}
              title="Kanban Board"
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={`p-1.5 rounded-md ${viewMode === 'list' ? 'bg-indigo-50 text-indigo-600' : 'text-slate-500 hover:text-slate-900'}`}
              title="List View"
            >
              <ListIcon className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      {errorMessage && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-xs font-medium text-red-800 flex items-center justify-between">
          <span>{errorMessage}</span>
          <button onClick={() => setErrorMessage(null)} className="text-red-600 hover:text-red-900">
            Dismiss
          </button>
        </div>
      )}

      {/* Kanban Board Mode */}
      {viewMode === 'kanban' ? (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
          {COLUMNS.map((col) => {
            const colCases = filteredCases.filter((c) => c.status === col.id);
            return (
              <div key={col.id} className="flex flex-col rounded-xl border border-slate-200 bg-slate-100/60 p-4">
                <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-200">
                  <span className="font-semibold text-sm text-slate-800">{col.title}</span>
                  <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-bold text-slate-700">
                    {colCases.length}
                  </span>
                </div>

                <div className="flex-1 space-y-3 min-h-[400px]">
                  {colCases.map((c) => (
                    <div
                      key={c.caseId}
                      className="rounded-lg border border-slate-200 bg-white p-4 shadow-xs transition-shadow hover:shadow-md"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <Link
                          to={`/cases/${c.caseId}`}
                          className="font-semibold text-xs text-indigo-600 hover:underline"
                        >
                          {c.caseId}
                        </Link>
                        <span
                          className={`rounded-md border px-1.5 py-0.5 text-[10px] font-bold ${getSeverityBadge(c.severity)}`}
                        >
                          {c.severity}
                        </span>
                      </div>

                      <p className="mt-2 text-xs font-medium text-slate-800">{c.type}</p>
                      <p className="text-[11px] text-slate-500">
                        Variance: <span className="font-semibold text-slate-900">{(c.variancePaise / 100).toFixed(2)} INR</span>
                      </p>

                      <div className="mt-3 flex items-center justify-between pt-2 border-t border-slate-100 text-[11px] text-slate-500">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3 text-slate-400" />
                          {new Date(c.slaDeadline).toLocaleDateString()}
                        </span>

                        {/* Accessible state transition menu */}
                        <select
                          value={c.status}
                          onChange={(e) => handleStatusChange(c, e.target.value as CaseStatus)}
                          className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[10px] font-medium text-slate-700"
                        >
                          {COLUMNS.map((s) => (
                            <option key={s.id} value={s.id}>
                              Move: {s.title}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  ))}

                  {colCases.length === 0 && (
                    <div className="flex h-32 items-center justify-center rounded-lg border border-dashed border-slate-300 text-xs text-slate-400">
                      No cases in {col.title}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* List Mode */
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 font-semibold text-slate-700">
              <tr>
                <th className="px-4 py-3">Case ID</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Severity</th>
                <th className="px-4 py-3">Variance</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Assignee</th>
                <th className="px-4 py-3">SLA Deadline</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium text-slate-600">
              {filteredCases.map((c) => (
                <tr key={c.caseId} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3 font-semibold text-indigo-600">
                    <Link to={`/cases/${c.caseId}`}>{c.caseId}</Link>
                  </td>
                  <td className="px-4 py-3">{c.type}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${getSeverityBadge(c.severity)}`}>
                      {c.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-bold text-slate-900">
                    {(c.variancePaise / 100).toFixed(2)} INR
                  </td>
                  <td className="px-4 py-3 font-semibold">{c.status}</td>
                  <td className="px-4 py-3">{c.assignedTo || <span className="text-slate-400 italic">Unassigned</span>}</td>
                  <td className="px-4 py-3">{new Date(c.slaDeadline).toLocaleString()}</td>
                  <td className="px-4 py-3 text-right">
                    <Link
                      to={`/cases/${c.caseId}`}
                      className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-1 text-slate-700 hover:bg-slate-200 font-semibold"
                    >
                      View <ChevronRight className="h-3 w-3" />
                    </Link>
                  </td>
                </tr>
              ))}
              {filteredCases.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-slate-400">
                    No cases match the selected filter criteria.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
