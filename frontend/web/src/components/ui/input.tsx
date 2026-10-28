import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export const Input = forwardRef<HTMLInputElement,InputHTMLAttributes<HTMLInputElement>>(({className,...props},ref)=><input ref={ref} className={cn("focus-ring min-h-10 w-full rounded-xl border border-[var(--line)] bg-white px-3.5 text-sm text-[var(--ink)] placeholder:text-[#99a5aa] outline-none transition focus:border-[var(--teal)]",className)} {...props}/>);
Input.displayName="Input";

