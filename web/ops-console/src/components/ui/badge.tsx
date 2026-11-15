import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "../../lib/utils"

/*
 * Jira-style "lozenge": compact, uppercase, bold, subtle tinted background
 * with a saturated text color. Variant keys are kept identical to prior usage.
 */
const badgeVariants = cva(
  "inline-flex items-center rounded-[3px] px-1.5 py-0.5 text-[11px] font-bold uppercase leading-4 tracking-[0.03em] whitespace-nowrap transition-colors",
  {
    variants: {
      variant: {
        default: "bg-accent text-[#0055cc]",
        secondary: "bg-[#dcdfe4] text-[#44546f]",
        destructive: "bg-[#ffeceb] text-[#ae2e24]",
        outline: "border border-border bg-transparent text-foreground normal-case font-medium",
        success: "bg-[#dcfff1] text-[#216e4e]",
        warning: "bg-[#fff7d6] text-[#7f5f01]",
        /* Roles */
        admin: "bg-accent text-[#0055cc]",
        developer: "bg-[#dcfff1] text-[#216e4e]",
        /* Priority */
        low: "bg-[#dcfff1] text-[#216e4e]",
        medium: "bg-[#fff7d6] text-[#7f5f01]",
        high: "bg-[#ffeceb] text-[#ae2e24]",
        critical: "bg-[#ae2e24] text-white",
        /* Status */
        todo: "bg-[#dcdfe4] text-[#44546f]",
        in_progress: "bg-accent text-[#0055cc]",
        in_review: "bg-[#f3f0ff] text-[#5e4db2]",
        done: "bg-[#dcfff1] text-[#216e4e]",
        /* Type */
        bug: "bg-[#ffeceb] text-[#ae2e24]",
        feature: "bg-accent text-[#0055cc]",
        technical: "bg-[#dcdfe4] text-[#44546f]",
        /* Audit actions */
        create: "bg-[#dcfff1] text-[#216e4e]",
        update: "bg-accent text-[#0055cc]",
        delete: "bg-[#ffeceb] text-[#ae2e24]",
        restore: "bg-[#f3f0ff] text-[#5e4db2]",
        /* Audit entities */
        user: "bg-accent text-[#0055cc]",
        system: "bg-[#dcdfe4] text-[#44546f]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
