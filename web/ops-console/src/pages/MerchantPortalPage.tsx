import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../contexts/AuthContext';
import { paycoreApi } from '../api/paycoreApi';
import { CheckCircle2, Lock, Landmark, FileText } from 'lucide-react';

export function MerchantPortalPage() {
  const { user } = useAuth();
  const sellerId = user?.username || 'seller_alpha';
  const accountCode = `MERCHANT_${sellerId}`;

  const { data: account } = useQuery({
    queryKey: ['account', accountCode],
    queryFn: () => paycoreApi.getAccount(accountCode),
    retry: false,
  });

  const { data: statement = [] } = useQuery({
    queryKey: ['statement', accountCode],
    queryFn: () => paycoreApi.getStatement(accountCode),
    retry: false,
  });

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-900">
          Merchant Settlement Portal
        </h2>
        <p className="text-sm text-slate-500">
          View your real-time payable balance, active authorization holds, and payout statements
        </p>
      </div>

      {/* Balance Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Available for Payout</span>
            <CheckCircle2 className="h-5 w-5 text-emerald-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.availableBalancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-emerald-600 font-medium">Eligible for next cutoff</p>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Active Holds</span>
            <Lock className="h-5 w-5 text-amber-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.lockedBalancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-slate-500">Auto-releases if uncaptured in 7 days</p>
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase">Total Account Balance</span>
            <Landmark className="h-5 w-5 text-indigo-500" />
          </div>
          <div className="mt-3">
            <span className="text-2xl font-bold text-slate-900">
              {account ? (account.balancePaise / 100).toFixed(2) : '0.00'} INR
            </span>
            <p className="mt-1 text-xs text-slate-400 font-mono">{accountCode}</p>
          </div>
        </div>
      </div>

      {/* Statement Table */}
      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
        <h3 className="text-sm font-bold text-slate-900 mb-3 flex items-center gap-2">
          <FileText className="h-4 w-4 text-indigo-600" />
          Transaction Statement
        </h3>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 font-semibold text-slate-700">
              <tr>
                <th className="px-4 py-3">Sequence</th>
                <th className="px-4 py-3">Journal Ref</th>
                <th className="px-4 py-3">Type</th>
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
                    No transactions found for this merchant account.
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
