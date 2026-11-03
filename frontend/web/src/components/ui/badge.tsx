import { cn } from "@/lib/utils";

const styles:Record<string,string>={BOOKED:"bg-[#f7edda] text-[#76551e]",CONFIRMED:"bg-[#e9eff5] text-[#425d73]",ARRIVED:"bg-[#e2f0e8] text-[#155d51]",IN_PROGRESS:"bg-[#e9e8f2] text-[#5a5179]",COMPLETED:"bg-[#e6f0e2] text-[#41613d]",CANCELLED:"bg-[#f2ece9] text-[#765950]",NO_SHOW:"bg-[#f8e9e3] text-[#8e493d]",DRAFT:"bg-[#edf0ed] text-[#526267]",SIGNED:"bg-[#e2f0e8] text-[#155d51]",ISSUED:"bg-[#e2f0e8] text-[#155d51]"};
const labels:Record<string,string>={BOOKED:"Đã đặt",CONFIRMED:"Đã xác nhận",ARRIVED:"Đã đến",IN_PROGRESS:"Đang khám",COMPLETED:"Hoàn tất",CANCELLED:"Đã hủy",NO_SHOW:"Vắng mặt",DRAFT:"Bản nháp",SIGNED:"Đã chốt",ISSUED:"Đã phát hành"};
export function Badge({value,className}:{value:string;className?:string}){return <span className={cn("inline-flex min-h-7 items-center rounded-full px-3 py-1 text-[11px] font-bold leading-none",styles[value]??"bg-slate-100 text-slate-600",className)}>{labels[value]??value.replaceAll("_"," ")}</span>}

