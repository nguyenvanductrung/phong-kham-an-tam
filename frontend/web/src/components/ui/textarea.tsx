import { forwardRef, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export const Textarea = forwardRef<HTMLTextAreaElement,TextareaHTMLAttributes<HTMLTextAreaElement>>(({className,...props},ref)=><textarea ref={ref} className={cn("focus-ring min-h-24 w-full resize-y rounded-xl border border-[var(--line)] bg-white px-3.5 py-3 text-sm text-[var(--ink)] placeholder:text-[#99a5aa] outline-none transition focus:border-[var(--teal)]",className)} {...props}/>);
Textarea.displayName="Textarea";

