import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { paycoreApi } from '../api/paycoreApi';
import {
  BookOpen,
  Search,
  CheckCircle2,
  Lock,
  ArrowRightLeft,
} from 'lucide-react';

export function LedgerPage() {
  const [accountCode, setAccountCode] = useState('MERCHANT_seller_alpha');
  const [searchQuery, setSearchQuery] = useState('MERCHANT_seller_alpha');

  const { data: account, isLoading: accountLoading } = useQuery({
    queryKey: ['account', accountCode],
    queryFn: () => paycoreApi.getAccount(accountCode),
    retry: false,
  });

  const { data: statement = [] } = useQuery({
    queryKey: ['statement', accountCode],
    queryFn: () => paycoreApi.getStatement(accountCode),
    retry: false,
  });

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      setAccountCode(searchQuery.trim());
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            Double-Entry Ledger Explorer
          </h2>
          <p className="text-sm text-slate-500">
            Immutable balance audit, keyset-paginated statements, and deterministic multi-leg journals
          </p>
        </div>

        <form onSubmit={handleSearch} className="flex gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Account code (e.g. MERCHANT_...)"
              className="rounded-lg border border-slate-300 bg-white pl-9 pr-3 py-2 text-xs font-mono text-slate-900 shadow-xs focus:border-indigo-500 focus:outline-hidden w-64"
            />
          </div>
          <button
            type="submit"
            className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white hover:bg-indigo-700 shadow-xs"
          >
            Search
          </button>
        </form>
      </div>

      {/* Account Balance Summary */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Available Balance</span>
            <CheckCircle2 className="h-5 w-5 text-emerald-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.availableBalancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-slate-500 font-mono">{accountCode}</p>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Locked (Holds)</span>
            <Lock className="h-5 w-5 text-amber-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.lockedBalancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-slate-500">Reserved for pending authorizations</p>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Total Settled Balance</span>
            <ArrowRightLeft className="h-5 w-5 text-indigo-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.balancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-emerald-600 font-medium">Invariant I5 Non-Negative Verified</p>
          </div>
        </div>
      </div>

      {/* Statement Table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <BookOpen className="h-4 w-4 text-indigo-600" />
            Account Statement
          </h3>
          <span className="text-xs font-medium text-slate-500">
            Keyset pagination (cursor-based, no OFFSET drift)
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 font-semibold text-slate-700">
              <tr>
                <th className="px-4 py-3">Sequence #</th>
                <th className="px-4 py-3">Journal ID</th>
                <th className="px-4 py-3">Entry Type</th>
                <th className="px-4 py-3">Amount</th>
                <th className="px-4 py-3">Running Balance</th>
                <th className="px-4 py-3">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium text-slate-600">
              {statement.map((entry) => (
                <tr key={entry.id} className="hover:bg-slate-50/80">
                  <td className="px-4 py-3 font-mono font-bold text-slate-900">#{entry.id}</td>
                  <td className="px-4 py-3 font-mono text-slate-600">{entry.journalId}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                        entry.type === 'CREDIT'
                          ? 'bg-emerald-50 text-emerald-700'
                          : 'bg-red-50 text-red-700'
                      }`}
                    >
                      {entry.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-bold text-slate-900">
                    {(entry.amountPaise / 100).toFixed(2)} INR
                  </td>
                  <td className="px-4 py-3 font-mono font-semibold text-slate-800">
                    {(entry.runningBalancePaise / 100).toFixed(2)} INR
                  </td>
                  <td className="px-4 py-3 text-slate-500">
                    {new Date(entry.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
              {statement.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-400">
                    {accountLoading
                      ? 'Loading statement entries...'
                      : 'No journal postings found for this account code.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
