import React from 'react';
import {
  DndContext,
  DragOverlay,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent, DragStartEvent } from '@dnd-kit/core';
import type { TicketResponse, UserResponse, TicketStatus } from '../../types/api';
import { KanbanColumn } from './KanbanColumn';
import { TicketCard } from './TicketCard';

interface KanbanBoardProps {
  tickets: TicketResponse[];
  users: UserResponse[];
  onTicketUpdate: (ticketId: number, updates: Partial<TicketResponse>) => Promise<void>;
}

const COLUMNS: { id: TicketStatus; title: string; color: string }[] = [
  { id: 'TODO', title: 'To Do', color: 'bg-[#8590a2]' },
  { id: 'IN_PROGRESS', title: 'In Progress', color: 'bg-[#0c66e4]' },
  { id: 'IN_REVIEW', title: 'In Review', color: 'bg-[#6e5dc6]' },
  { id: 'DONE', title: 'Done', color: 'bg-[#1f845a]' },
];

export function KanbanBoard({ tickets, users, onTicketUpdate }: KanbanBoardProps) {
  const [activeTicket, setActiveTicket] = React.useState<TicketResponse | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor)
  );

  const handleDragStart = (event: DragStartEvent) => {
    const { active } = event;
    const ticket = tickets.find(t => t.id === active.id);
    setActiveTicket(ticket || null);
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveTicket(null);

    if (!over) return;

    const ticketId = Number(active.id);
    const newStatus = over.id as TicketStatus;

    const ticket = tickets.find(t => t.id === ticketId);
    if (ticket && ticket.status !== newStatus) {
      await onTicketUpdate(ticketId, { status: newStatus });
    }
  };

  const getTicketsByStatus = (status: TicketStatus) =>
    tickets.filter(t => t.status === status);

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
    >
      <div className="flex gap-3 overflow-x-auto pb-2">
        {COLUMNS.map((column) => (
          <KanbanColumn
            key={column.id}
            id={column.id}
            title={column.title}
            tickets={getTicketsByStatus(column.id)}
            users={users}
            color={column.color}
          />
        ))}

        <DragOverlay>
          {activeTicket && (
            <TicketCard ticket={activeTicket} users={users} isDragging />
          )}
        </DragOverlay>
      </div>
    </DndContext>
  );
}
