"use client";

import { usePathname, useRouter } from "next/navigation";
import { Activity, CalendarDays, ChevronRight, ClipboardList, LayoutDashboard, LogOut, Menu, Settings2, ShieldCheck, Stethoscope, UsersRound } from "lucide-react";
import { useState } from "react";
import { SessionProvider, type SessionView } from "@/components/session-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const staffNav = [
  { href: "/dashboard", label: "Tổng quan", icon: LayoutDashboard },
  { href: "/patients", label: "Bệnh nhân", icon: UsersRound },
  { href: "/appointments", label: "Lịch hẹn", icon: CalendarDays },
  { href: "/clinical", label: "Bệnh án", icon: Stethoscope },
  { href: "/catalog", label: "Danh mục", icon: ClipboardList },
  { href: "/accounts", label: "Tài khoản", icon: ShieldCheck },
];
const patientNav = [
  { href: "/portal", label: "Trang của tôi", icon: LayoutDashboard },
  { href: "/appointments", label: "Lịch hẹn", icon: CalendarDays },
  { href: "/records", label: "Kết quả khám", icon: ClipboardList },
];
const roleLabels:Record<string,string>={SUPER_ADMIN:"Quản trị hệ thống",CLINIC_MANAGER:"Quản lý phòng khám",DOCTOR:"Bác sĩ",RECEPTIONIST:"Lễ tân",ASSISTANT:"Phụ tá",PATIENT:"Bệnh nhân"};

export function AppShell({session,children}:{session:SessionView;children:React.ReactNode}){
  const pathname=usePathname();const router=useRouter();const [mobileOpen,setMobileOpen]=useState(false);const isPatient=session.roles.includes("PATIENT");
  const roleSet=new Set(session.roles);
  const nav=isPatient?patientNav:staffNav.filter(item=>
    (item.href!=="/patients"||["SUPER_ADMIN","CLINIC_MANAGER","RECEPTIONIST"].some(role=>roleSet.has(role)))&&
    (item.href!=="/clinical"||roleSet.has("DOCTOR"))&&
    (item.href!=="/catalog"||["SUPER_ADMIN","CLINIC_MANAGER"].some(role=>roleSet.has(role)))&&
    (item.href!=="/accounts"||["SUPER_ADMIN","CLINIC_MANAGER"].some(role=>roleSet.has(role)))
  );
  const current=nav.find(item=>pathname===item.href||pathname.startsWith(`${item.href}/`));
  const initials=session.fullName.split(/\s+/).slice(-2).map(part=>part[0]).join("").toUpperCase();
  async function logout(){await fetch("/api/auth/logout",{method:"POST",headers:{"x-csrf-token":session.csrfToken}}).catch(()=>undefined);router.replace("/login");router.refresh();}
  const sidebar=<aside className="flex h-full flex-col border-r border-white/10 bg-[#18323a] text-white">
    <div className="flex h-[78px] items-center gap-3 px-6">
      <div className="flex size-10 items-center justify-center rounded-2xl bg-[#d8f0e8] text-[#166e62]"><Activity size={21}/></div>
      <div><p className="text-sm font-bold tracking-wide">AN TÂM</p><p className="mt-0.5 text-[10px] font-semibold tracking-[.18em] text-white/55">DENTAL CARE</p></div>
    </div>
    <div className="mx-4 mb-3 mt-7 rounded-2xl border border-white/10 bg-white/[.055] px-4 py-3">
      <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.12em] text-white/50"><span className="size-1.5 rounded-full bg-[#7fd0ac]"/> Một cơ sở</div>
      <p className="mt-2 text-sm font-semibold">Nha khoa An Tâm</p><p className="mt-0.5 text-xs text-white/55">Hồ Chí Minh</p>
    </div>
    <p className="px-6 pb-2 pt-5 text-[10px] font-bold uppercase tracking-[.16em] text-white/40">Không gian làm việc</p>
    <nav className="grid gap-1 px-3" aria-label="Điều hướng chính">
      {nav.map(({href,label,icon:Icon})=>{const active=pathname===href||pathname.startsWith(`${href}/`);return <a key={href} href={href} onClick={()=>setMobileOpen(false)} className={cn("focus-ring flex min-h-11 items-center gap-3 rounded-xl px-3.5 text-[13px] font-semibold transition",active?"bg-white text-[#17343a] shadow-sm":"text-white/65 hover:bg-white/8 hover:text-white")} aria-current={active?"page":undefined}><Icon size={17} strokeWidth={1.8}/><span>{label}</span>{active?<ChevronRight className="ml-auto" size={15}/>:null}</a>})}
    </nav>
    {!isPatient&&session.roles.some(role=>["SUPER_ADMIN","CLINIC_MANAGER"].includes(role))?<div className="mt-auto px-3 pb-5"><div className="mb-3 border-t border-white/10"/><a href="/catalog" className="flex min-h-10 items-center gap-3 rounded-xl px-3.5 text-xs font-semibold text-white/55 hover:bg-white/8 hover:text-white"><Settings2 size={16}/>Cấu hình</a></div>:<div className="mt-auto px-5 pb-6 pt-10"><div className="rounded-2xl bg-[#24444a] p-3.5"><div className="flex size-8 items-center justify-center rounded-xl bg-white/10 text-[#a7ded0]"><ShieldCheck size={16}/></div><p className="mt-3 text-xs font-semibold">Thông tin được bảo vệ</p><p className="mt-1 text-[11px] leading-5 text-white/55">Chỉ người được phân quyền mới xem được hồ sơ.</p></div></div>}
    <div className="border-t border-white/10 p-4 text-[10px] text-white/35">© 2026 Nha khoa An Tâm</div>
  </aside>;
  return <SessionProvider value={session}><div className="min-h-screen md:grid md:grid-cols-[252px_minmax(0,1fr)]">
    <div className="hidden md:block">{sidebar}</div>
    {mobileOpen?<div className="fixed inset-0 z-40 bg-black/35 md:hidden" onClick={()=>setMobileOpen(false)}><div className="h-full w-[270px]" onClick={e=>e.stopPropagation()}>{sidebar}</div></div>:null}
    <div className="min-w-0"><header className="sticky top-0 z-20 flex h-[78px] items-center justify-between border-b border-[var(--line)] bg-white/90 px-4 backdrop-blur md:px-8">
      <div className="flex items-center gap-3"><Button variant="ghost" size="icon" className="md:hidden" onClick={()=>setMobileOpen(true)} aria-label="Mở menu"><Menu size={19}/></Button><div><p className="text-xs text-[var(--muted)]">Phòng khám / {current?.label??"Không gian làm việc"}</p><p className="mt-1 text-sm font-bold">{current?.label??"Nha khoa An Tâm"}</p></div></div>
      <div className="flex items-center gap-3"><div className="hidden text-right sm:block"><p className="text-xs font-bold">{session.fullName}</p><p className="mt-1 text-[10px] text-[var(--muted)]">{roleLabels[session.roles[0]]??"Nhân viên"}</p></div><div className="flex size-10 items-center justify-center rounded-2xl bg-[#e5f3ef] text-xs font-bold text-[#176e62]">{initials}</div><Button variant="ghost" size="icon" className="hidden sm:inline-flex" onClick={logout} aria-label="Đăng xuất"><LogOut size={17}/></Button></div>
    </header><main className="workspace-grid min-h-[calc(100vh-78px)] p-4 pb-24 md:p-8 md:pb-10">{children}</main></div>
    <nav className="fixed inset-x-3 bottom-3 z-30 grid grid-cols-3 gap-1 rounded-2xl border border-[var(--line)] bg-white/95 p-2 shadow-lg backdrop-blur md:hidden" aria-label="Điều hướng di động">{nav.slice(0,3).map(({href,label,icon:Icon})=><a key={href} href={href} className={cn("flex min-h-12 flex-col items-center justify-center gap-1 rounded-xl text-[10px] font-semibold",pathname===href?"bg-[var(--teal-soft)] text-[var(--teal-dark)]":"text-[var(--muted)]")}><Icon size={17}/>{label}</a>)}</nav>
  </div></SessionProvider>;
}

