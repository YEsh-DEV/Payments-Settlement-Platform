import { useDraggable } from '@dnd-kit/core';
import { useNavigate } from 'react-router-dom';
import type { TicketResponse, UserResponse, TicketPriority } from '../../types/api';
import { Badge } from '../ui/badge';
import { cn } from '../../lib/utils';
import { CircleAlert as AlertCircle, Clock } from 'lucide-react';
import { format, parseISO } from 'date-fns';

interface TicketCardProps {
  ticket: TicketResponse;
  users: UserResponse[];
  isDragging?: boolean;
}

const priorityVariants: Record<TicketPriority, 'low' | 'medium' | 'high' | 'critical'> = {
  LOW: 'low',
  MEDIUM: 'medium',
  HIGH: 'high',
  CRITICAL: 'critical',
};

const AVATAR_COLORS = ['#0c66e4', '#1f845a', '#6e5dc6', '#c9372c', '#e2772c', '#206b74'];
function avatarColor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export function TicketCard({ ticket, users, isDragging }: TicketCardProps) {
  const navigate = useNavigate();
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: ticket.id,
    data: ticket,
  });

  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      }
    : undefined;

  const assignee = users.find(u => u.id === ticket.assigneeId);

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      onClick={() => navigate(`/tickets/${ticket.id}`)}
      className={cn(
        'elevate group rounded-lg border border-border bg-white p-3 shadow-card cursor-pointer hover:border-[#b3bac5]',
        isDragging && 'rotate-2 scale-[1.02] shadow-overlay',
        ticket.overdue && 'border-l-2 border-l-destructive'
      )}
    >
      <div className="mb-2.5 flex items-start justify-between gap-2">
        <h4 className="line-clamp-2 flex-1 text-sm font-medium leading-snug text-foreground group-hover:text-primary">
          {ticket.title}
        </h4>
        {ticket.overdue && (
          <AlertCircle className="h-4 w-4 flex-shrink-0 text-destructive" />
        )}
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-1.5">
        <Badge variant={priorityVariants[ticket.priority]}>{ticket.priority}</Badge>
        <Badge variant="outline" className="text-[11px]">{ticket.type}</Badge>
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span className="font-medium text-[#626f86]">#{ticket.id}</span>
          {ticket.dueDate && (
            <span className={cn('flex items-center gap-1', ticket.overdue && 'font-medium text-destructive')}>
              <Clock className="h-3 w-3" />
              {format(parseISO(ticket.dueDate), 'MMM d')}
            </span>
          )}
        </div>

        {assignee ? (
          <div
            className="flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-semibold text-white"
            style={{ backgroundColor: avatarColor(assignee.username) }}
            title={assignee.fullName}
          >
            {assignee.fullName?.charAt(0).toUpperCase() || assignee.username.charAt(0).toUpperCase()}
          </div>
        ) : (
          <div
            className="flex h-6 w-6 items-center justify-center rounded-full border border-dashed border-[#c1c7d0] text-[10px] text-muted-foreground"
            title="Unassigned"
          >
            ?
          </div>
        )}
      </div>
    </div>
  );
}
