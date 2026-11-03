import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

const buttonVariants = cva("focus-ring inline-flex items-center justify-center gap-2 rounded-[var(--radius-control)] font-semibold transition duration-150 active:translate-y-px disabled:pointer-events-none disabled:opacity-45", {
  variants: {
    variant: { primary: "bg-[var(--teal)] text-white shadow-sm hover:bg-[var(--teal-dark)] hover:shadow", secondary: "border border-[var(--line)] bg-[var(--paper)] text-[var(--ink)] hover:bg-[var(--teal-soft)]", ghost: "text-[var(--muted)] hover:bg-[var(--teal-soft)] hover:text-[var(--teal-dark)]", danger: "bg-[#fff0ed] text-[#a94f3e] hover:bg-[#ffe2dc]" },
    size: { md: "min-h-11 px-4 text-sm", sm: "min-h-10 px-3 text-xs", lg: "min-h-12 px-5 text-sm", icon: "size-11" },
  }, defaultVariants: { variant: "primary", size: "md" },
});
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { asChild?: boolean }
export const Button = forwardRef<HTMLButtonElement,ButtonProps>(({ className,variant,size,asChild=false,...props },ref)=>{
  const Component=asChild?Slot:"button";
  return <Component ref={ref} className={cn(buttonVariants({variant,size}),className)} {...props}/>;
});
Button.displayName="Button";

