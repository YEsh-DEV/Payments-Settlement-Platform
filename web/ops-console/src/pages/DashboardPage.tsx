import React from 'react';
import { useAuth } from '../contexts/AuthContext';
import { projectsApi, ticketsApi, auditLogsApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card';
import { LoadingSpinner } from '../components/ui/loading';
import { FolderKanban, Ticket, CircleAlert as AlertCircle, Clock, Activity } from 'lucide-react';
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { ProjectResponse, TicketResponse, AuditLogResponse } from '../types/api';

const STATUS_COLORS: Record<string, string> = {
  TODO: '#5e6c84',
  IN_PROGRESS: '#0052CC',
  IN_REVIEW: '#5e4db6',
  DONE: '#00875A',
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: '#0052CC',
  MEDIUM: '#FF8B00',
  HIGH: '#DE350B',
  CRITICAL: '#AE2A19',
};

export function DashboardPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [projects, setProjects] = React.useState<ProjectResponse[]>([]);
  const [tickets, setTickets] = React.useState<TicketResponse[]>([]);
  const [auditLogs, setAuditLogs] = React.useState<AuditLogResponse[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);

  React.useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        const [projectsData, auditData] = await Promise.all([
          projectsApi.getAll(),
          auditLogsApi.getAll({ pageSize: 5 }),
        ]);

        setProjects(projectsData.filter(p => !p.deletedAt));
        setAuditLogs(auditData.data);

        if (projectsData.length > 0 && projectsData[0].id) {
          const ticketsData = await ticketsApi.getAll(projectsData[0].id);
          setTickets(ticketsData);
        }
      } catch {
        showToast('Failed to load dashboard data', 'error');
      } finally {
        setIsLoading(false);
      }
    };

    fetchDashboardData();
  }, [showToast]);

  if (isLoading) {
    return <LoadingSpinner size="lg" />;
  }

  const openTickets = tickets.filter(t => t.status !== 'DONE');
  const overdueTickets = tickets.filter(t => t.overdue);
  const criticalTickets = tickets.filter(t => t.priority === 'CRITICAL');

  const statusData = Object.entries(
    tickets.reduce((acc, t) => {
      acc[t.status] = (acc[t.status] || 0) + 1;
      return acc;
    }, {} as Record<string, number>)
  ).map(([name, value]) => ({ name, value }));

  const priorityData = Object.entries(
    tickets.reduce((acc, t) => {
      acc[t.priority] = (acc[t.priority] || 0) + 1;
      return acc;
    }, {} as Record<string, number>)
  ).map(([name, value]) => ({ name, value }));

  return (
    <div className="space-y-6">
      {/* Page hero — gradient banner echoing the brand rail / login */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-[#0a2754] to-[#0c66e4] px-6 py-7 text-white shadow-sm">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-10 -top-16 h-48 w-48 rounded-full bg-white/10 blur-2xl"
        />
        <h1 className="relative text-2xl font-semibold tracking-tight">
          Welcome back, {user?.fullName}
        </h1>
        <p className="relative mt-1 text-sm text-white/75">
          Here is what is happening across your projects today.
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {[
          { label: 'Total Projects', value: projects.length, icon: FolderKanban, chip: 'bg-accent text-primary', value_cls: 'text-foreground' },
          { label: 'Open Tickets', value: openTickets.length, icon: Ticket, chip: 'bg-[#f3f0ff] text-[#5e4db2]', value_cls: 'text-foreground' },
          { label: 'Overdue', value: overdueTickets.length, icon: Clock, chip: 'bg-[#ffeceb] text-destructive', value_cls: 'text-destructive' },
          { label: 'Critical', value: criticalTickets.length, icon: AlertCircle, chip: 'bg-[#fff7d6] text-[#7f5f01]', value_cls: 'text-[#7f5f01]' },
        ].map((stat) => (
          <Card key={stat.label} className="flex items-center gap-4 p-5 elevate">
            <div className={`flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-lg ${stat.chip}`}>
              <stat.icon className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <div className={`text-2xl font-semibold leading-tight ${stat.value_cls}`}>{stat.value}</div>
              <div className="text-sm text-muted-foreground">{stat.label}</div>
            </div>
          </Card>
        ))}
      </div>

      {/* Charts */}
      {tickets.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Tickets by Status</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={statusData}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, percent }) => `${name} (${((percent || 0) * 100).toFixed(0)}%)`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    {statusData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={STATUS_COLORS[entry.name] || '#8884d8'} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Tickets by Priority</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={priorityData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="value" fill="#8884d8">
                    {priorityData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={PRIORITY_COLORS[entry.name] || '#8884d8'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Recent Activity */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="h-5 w-5" />
            Recent Activity
          </CardTitle>
        </CardHeader>
        <CardContent>
          {auditLogs.length === 0 ? (
            <p className="text-muted-foreground text-center py-8">No recent activity</p>
          ) : (
            <div className="space-y-4">
              {auditLogs.slice(0, 5).map((log) => (
                <div
                  key={log.id}
                  className="flex items-start justify-between border-b pb-3 last:border-0"
                >
                  <div>
                    <p className="font-medium">{log.action}</p>
                    <p className="text-sm text-muted-foreground">
                      {log.entityType} (ID: {log.entityId})
                    </p>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {new Date(log.timestamp).toLocaleString()}
                  </p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
