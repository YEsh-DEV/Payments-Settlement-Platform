import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "../../lib/utils"

const buttonVariants = cva(
  "inline-flex items-center justify-center whitespace-nowrap rounded text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 disabled:pointer-events-none disabled:opacity-50 select-none cursor-pointer",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground shadow-sm hover:bg-[#0055cc] active:bg-[#09326c]",
        destructive:
          "bg-destructive text-destructive-foreground shadow-sm hover:bg-[#ae2e24] active:bg-[#601e16]",
        outline:
          "border border-border bg-white text-foreground hover:bg-muted active:bg-secondary",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-[#dcdfe4] active:bg-[#c1c7d0]",
        ghost: "text-foreground hover:bg-muted active:bg-secondary",
        link: "text-primary underline-offset-4 hover:underline",
        success:
          "bg-success text-success-foreground shadow-sm hover:bg-[#1a6b48] active:bg-[#164b35]",
        warning:
          "bg-warning text-warning-foreground hover:bg-[#e2b203] active:bg-[#cf9f02]",
      },
      size: {
        default: "h-9 px-3 py-2",
        sm: "h-8 rounded px-2.5 text-[13px]",
        lg: "h-10 rounded px-5",
        icon: "h-9 w-9",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild, ...props }, ref) => {
    // `asChild` is accepted for call-site compatibility (buttons nested inside
    // <label> upload triggers). This component always renders a native <button>
    // so attributes like `disabled` stay valid; consume the flag here so it is
    // not forwarded to the DOM as an unknown attribute.
    void asChild
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
