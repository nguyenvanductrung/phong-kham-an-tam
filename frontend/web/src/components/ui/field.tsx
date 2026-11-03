import type { ReactNode } from "react";

export function Field({label,children,error,hint}:{label:string;children:ReactNode;error?:string;hint?:string}){
  return <label className="grid gap-2 text-sm font-semibold text-[var(--ink)]"><span>{label}</span>{children}{error?<span className="text-xs font-medium text-[#a64f40]">{error}</span>:hint?<span className="text-xs font-normal leading-5 text-[var(--muted)]">{hint}</span>:null}</label>
}

