import { cn } from "@/lib/utils";

const styles:Record<string,string>={BOOKED:"bg-[#fff4e4] text-[#9b681e]",CONFIRMED:"bg-[#eaf1ff] text-[#4d68a2]",ARRIVED:"bg-[#e4f5f1] text-[#167668]",IN_PROGRESS:"bg-[#e9e4fa] text-[#6854a3]",COMPLETED:"bg-[#e9f4e6] text-[#4f7d43]",CANCELLED:"bg-[#f4eded] text-[#906966]",NO_SHOW:"bg-[#fff0ed] text-[#ad5848]",DRAFT:"bg-[#eef2f3] text-[#596c75]",SIGNED:"bg-[#e7f5f2] text-[#167668]",ISSUED:"bg-[#e7f5f2] text-[#167668]"};
const labels:Record<string,string>={BOOKED:"Đã đặt",CONFIRMED:"Đã xác nhận",ARRIVED:"Đã đến",IN_PROGRESS:"Đang khám",COMPLETED:"Hoàn tất",CANCELLED:"Đã hủy",NO_SHOW:"Vắng mặt",DRAFT:"Bản nháp",SIGNED:"Đã chốt",ISSUED:"Đã phát hành"};
export function Badge({value,className}:{value:string;className?:string}){return <span className={cn("inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-bold",styles[value]??"bg-slate-100 text-slate-600",className)}>{labels[value]??value.replaceAll("_"," ")}</span>}

