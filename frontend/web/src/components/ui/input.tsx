import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export const Input = forwardRef<HTMLInputElement,InputHTMLAttributes<HTMLInputElement>>(({className,...props},ref)=><input ref={ref} className={cn("focus-ring min-h-11 w-full rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--paper)] px-3.5 text-sm text-[var(--ink)] placeholder:text-[#849397] outline-none transition focus:border-[var(--teal)] focus:ring-2 focus:ring-[var(--teal)]/10",className)} {...props}/>);
Input.displayName="Input";

