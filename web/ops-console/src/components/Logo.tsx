import * as React from 'react';

/**
 * IssueFlow logo mark — three nodes rising along a flow line (todo → in-progress → done),
 * the final node emphasized to signal completion. Pure SVG so it scales crisply and
 * themes via the gradient. `useId` keeps the gradient id unique per instance.
 */
export function LogoMark({ className = 'h-10 w-10' }: { className?: string }) {
  const gradientId = `issueflow-mark-${React.useId()}`;
  return (
    <svg
      className={className}
      viewBox="0 0 32 32"
      fill="none"
      role="img"
      aria-label="IssueFlow logo"
    >
      <defs>
        <linearGradient
          id={gradientId}
          x1="0"
          y1="0"
          x2="32"
          y2="32"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#1d7afc" />
          <stop offset="1" stopColor="#8b5cf6" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill={`url(#${gradientId})`} />
      <path
        d="M8 21 L16 16 L24 11"
        stroke="white"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="8" cy="21" r="2.1" fill="white" fillOpacity="0.85" />
      <circle cx="16" cy="16" r="2.1" fill="white" fillOpacity="0.85" />
      <circle cx="24" cy="11" r="3" fill="white" />
    </svg>
  );
}

export function Logo({
  className = '',
  markClassName = 'h-10 w-10',
  wordmarkClassName = 'text-xl font-semibold tracking-tight',
}: {
  className?: string;
  markClassName?: string;
  wordmarkClassName?: string;
}) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <LogoMark className={markClassName} />
      <span className={wordmarkClassName}>IssueFlow</span>
    </span>
  );
}
