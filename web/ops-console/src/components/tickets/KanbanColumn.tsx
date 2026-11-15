import { useDroppable } from '@dnd-kit/core';
import { TicketCard } from './TicketCard';
import type { TicketResponse, UserResponse, TicketStatus } from '../../types/api';
import { cn } from '../../lib/utils';

interface KanbanColumnProps {
  id: TicketStatus;
  title: string;
  tickets: TicketResponse[];
  users: UserResponse[];
  color: string;
}

export function KanbanColumn({ id, title, tickets, users, color }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id });

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex w-[300px] shrink-0 flex-col rounded-lg bg-[#f1f2f4] transition-colors min-h-[460px]',
        isOver && 'bg-accent ring-2 ring-primary ring-offset-2 ring-offset-background'
      )}
    >
      <div className="flex items-center gap-2 px-3 pt-3 pb-2">
        <span className={cn('h-2 w-2 rounded-full', color)} />
        <h3 className="truncate text-xs font-bold uppercase tracking-wider text-[#44546f]">
          {title}
        </h3>
        <span className="ml-0.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-[#dcdfe4] px-1.5 text-xs font-semibold text-[#44546f]">
          {tickets.length}
        </span>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto px-2 pb-2">
        {tickets.map((ticket) => (
          <TicketCard key={ticket.id} ticket={ticket} users={users} />
        ))}

        {tickets.length === 0 && (
          <div className="m-1 rounded-md border border-dashed border-[#c1c7d0] py-10 text-center text-xs text-muted-foreground">
            No issues
          </div>
        )}
      </div>
    </div>
  );
}
