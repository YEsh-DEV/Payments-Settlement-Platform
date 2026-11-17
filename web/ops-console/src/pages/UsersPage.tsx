import { useState, useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';
import client from '../api/client';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { LoadingSpinner, PageLoader } from '../components/ui/loading';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import { UserPlus, Shield, Store, CheckCircle, XCircle, Search, RefreshCw } from 'lucide-react';

interface AdminUserRecord {
  id: string;
  username: string;
  roles: string[];
  sellerId?: string | null;
  enabled: boolean;
  createdAt: string;
}

const createUserSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  role: z.enum(['ADMIN', 'ANALYST', 'AUDITOR', 'MERCHANT']),
  sellerId: z.string().optional(),
});

type CreateUserFormData = z.infer<typeof createUserSchema>;

export function UsersPage() {
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();
  const [users, setUsers] = useState<AdminUserRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<CreateUserFormData>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      role: 'ANALYST',
    },
  });

  const selectedRole = watch('role');

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await client.get<AdminUserRecord[]>('/admin/users');
      setUsers(Array.isArray(res.data) ? res.data : []);
    } catch {
      // Mock fallback for UI demo when identity service admin endpoint is offline
      setUsers([
        {
          id: 'u-1',
          username: 'admin',
          roles: ['ADMIN'],
          sellerId: null,
          enabled: true,
          createdAt: new Date().toISOString(),
        },
        {
          id: 'u-2',
          username: 'analyst',
          roles: ['ANALYST'],
          sellerId: null,
          enabled: true,
          createdAt: new Date().toISOString(),
        },
        {
          id: 'u-3',
          username: 'auditor',
          roles: ['AUDITOR'],
          sellerId: null,
          enabled: true,
          createdAt: new Date().toISOString(),
        },
        {
          id: 'u-4',
          username: 'seller_flipkart',
          roles: ['MERCHANT'],
          sellerId: 'seller_001',
          enabled: true,
          createdAt: new Date().toISOString(),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleCreate = async (data: CreateUserFormData) => {
    setIsSubmitting(true);
    try {
      await client.post('/admin/users', {
        username: data.username,
        password: data.password,
        roles: [data.role],
        sellerId: data.role === 'MERCHANT' ? data.sellerId : undefined,
      });
      showToast(`User ${data.username} created successfully`, 'success');
      setIsDialogOpen(false);
      reset();
      fetchUsers();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { detail?: string } }; message?: string };
      showToast(error.response?.data?.detail || error.message || 'Failed to create user', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleStatus = async (userToToggle: AdminUserRecord) => {
    if (userToToggle.username === currentUser?.username) {
      showToast('Cannot modify your own active account status', 'error');
      return;
    }

    try {
      await client.put(`/admin/users/${userToToggle.id}`, {
        roles: userToToggle.roles,
        enabled: !userToToggle.enabled,
      });
      showToast(
        `User ${userToToggle.username} ${userToToggle.enabled ? 'disabled' : 'enabled'}`,
        'success'
      );
      fetchUsers();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { detail?: string } }; message?: string };
      showToast(error.response?.data?.detail || error.message || 'Failed to update user status', 'error');
    }
  };

  const filteredUsers = users.filter(
    (u) =>
      u.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.roles.some((r) => r.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (u.sellerId && u.sellerId.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  if (isLoading && users.length === 0) {
    return <PageLoader />;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">User Administration</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Manage operator accounts, assign security roles, and enforce dual-control permissions.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="outline" size="sm" onClick={fetchUsers} disabled={isLoading}>
            <RefreshCw className={`w-4 h-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => setIsDialogOpen(true)} className="bg-primary text-primary-foreground">
            <UserPlus className="w-4 h-4 mr-2" />
            Add Operator
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-semibold">Active Operators & Credentials</CardTitle>
            <div className="relative w-64">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search username, role, seller ID..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 h-9 text-sm"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/50 text-muted-foreground border-b border-border text-xs uppercase font-medium">
                <tr>
                  <th className="py-3 px-4">Operator</th>
                  <th className="py-3 px-4">Assigned Roles</th>
                  <th className="py-3 px-4">Merchant Link</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Created At</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredUsers.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-muted-foreground">
                      No matching operator accounts found.
                    </td>
                  </tr>
                ) : (
                  filteredUsers.map((u) => (
                    <tr key={u.id} className="hover:bg-muted/30 transition-colors">
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <div className="w-8 h-8 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-xs uppercase">
                            {u.username.substring(0, 2)}
                          </div>
                          <div>
                            <span className="font-medium text-foreground block">{u.username}</span>
                            <span className="text-xs text-muted-foreground">ID: {u.id}</span>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {u.roles.map((role) => (
                            <Badge
                              key={role}
                              variant={
                                role === 'ADMIN'
                                  ? 'default'
                                  : role === 'ANALYST'
                                  ? 'secondary'
                                  : 'outline'
                              }
                              className="text-xs uppercase font-mono"
                            >
                              <Shield className="w-3 h-3 mr-1 inline" />
                              {role}
                            </Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        {u.sellerId ? (
                          <span className="inline-flex items-center text-xs text-primary font-mono bg-primary/5 px-2 py-0.5 rounded border border-primary/20">
                            <Store className="w-3 h-3 mr-1" />
                            {u.sellerId}
                          </span>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        {u.enabled ? (
                          <span className="inline-flex items-center gap-1 text-xs text-emerald-600 font-medium">
                            <CheckCircle className="w-3.5 h-3.5" />
                            Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-xs text-destructive font-medium">
                            <XCircle className="w-3.5 h-3.5" />
                            Disabled
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-xs text-muted-foreground">
                        {new Date(u.createdAt).toLocaleDateString()}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleToggleStatus(u)}
                          disabled={u.username === currentUser?.username}
                          className="text-xs hover:bg-muted"
                        >
                          {u.enabled ? 'Deactivate' : 'Activate'}
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Add Platform Operator</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(handleCreate)} className="space-y-4 pt-2">
            <div className="space-y-1">
              <Label htmlFor="username">Username</Label>
              <Input
                id="username"
                placeholder="e.g. analyst_john"
                {...register('username')}
                disabled={isSubmitting}
              />
              {errors.username && (
                <p className="text-xs text-destructive">{errors.username.message}</p>
              )}
            </div>

            <div className="space-y-1">
              <Label htmlFor="password">Initial Password</Label>
              <Input
                id="password"
                type="password"
                placeholder="Minimum 8 characters"
                {...register('password')}
                disabled={isSubmitting}
              />
              {errors.password && (
                <p className="text-xs text-destructive">{errors.password.message}</p>
              )}
            </div>

            <div className="space-y-1">
              <Label htmlFor="role">Security Role</Label>
              <select
                id="role"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-primary"
                {...register('role')}
                disabled={isSubmitting}
              >
                <option value="ANALYST">ANALYST — Discrepancies, Recon & Case Resolution</option>
                <option value="ADMIN">ADMIN — Dual-Control Approvals & System Config</option>
                <option value="AUDITOR">AUDITOR — Read-only Ledger & Compliance</option>
                <option value="MERCHANT">MERCHANT — Self-Service Statement Portal</option>
              </select>
            </div>

            {selectedRole === 'MERCHANT' && (
              <div className="space-y-1">
                <Label htmlFor="sellerId">Linked Seller ID</Label>
                <Input
                  id="sellerId"
                  placeholder="e.g. seller_001"
                  {...register('sellerId')}
                  disabled={isSubmitting}
                />
              </div>
            )}

            <DialogFooter className="pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsDialogOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? <LoadingSpinner className="mr-2" /> : null}
                Create Operator
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
export default UsersPage;
