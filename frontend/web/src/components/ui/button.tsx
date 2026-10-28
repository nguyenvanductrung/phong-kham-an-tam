import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

const buttonVariants = cva("focus-ring inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition disabled:pointer-events-none disabled:opacity-45", {
  variants: {
    variant: { primary: "bg-[var(--teal)] text-white hover:bg-[var(--teal-dark)]", secondary: "border border-[var(--line)] bg-white text-[var(--ink)] hover:bg-[#f5f8f7]", ghost: "text-[var(--muted)] hover:bg-[var(--teal-soft)] hover:text-[var(--teal-dark)]", danger: "bg-[#fff0ed] text-[#a94f3e] hover:bg-[#ffe2dc]" },
    size: { md: "min-h-10 px-4 text-sm", sm: "min-h-8 px-3 text-xs", lg: "min-h-12 px-5 text-sm", icon: "size-10" },
  }, defaultVariants: { variant: "primary", size: "md" },
});
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { asChild?: boolean }
export const Button = forwardRef<HTMLButtonElement,ButtonProps>(({ className,variant,size,asChild=false,...props },ref)=>{
  const Component=asChild?Slot:"button";
  return <Component ref={ref} className={cn(buttonVariants({variant,size}),className)} {...props}/>;
});
Button.displayName="Button";

