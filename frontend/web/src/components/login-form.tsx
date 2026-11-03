"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { Activity, ArrowRight, BadgeCheck, CalendarDays, HeartPulse, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { useState } from "react";

const schema=z.object({email:z.email("Email chưa đúng định dạng."),password:z.string().min(1,"Vui lòng nhập mật khẩu.")});
type LoginValues=z.infer<typeof schema>;
const demos=[{label:"Lễ tân",email:"receptionist@antam.local"},{label:"Bác sĩ",email:"doctor@antam.local"},{label:"Bệnh nhân",email:"patient@antam.local"}];

export function LoginForm(){
  const router=useRouter();const [error,setError]=useState("");const [busy,setBusy]=useState(false);
  const {register,handleSubmit,setValue,formState:{errors}}=useForm<LoginValues>({resolver:zodResolver(schema),defaultValues:{email:"",password:""}});
  async function submit(values:LoginValues){setError("");setBusy(true);try{const response=await fetch("/api/auth/login",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(values)});const result=await response.json();if(!response.ok)throw new Error(result.detail??"Email hoặc mật khẩu không đúng.");router.replace(result.roles.includes("PATIENT")?"/portal":"/dashboard");router.refresh();}catch(e){setError(e instanceof Error?e.message:"Không thể đăng nhập.");}finally{setBusy(false);}}
  return <main className="grid min-h-screen bg-[var(--paper)] lg:grid-cols-[minmax(0,1.02fr)_minmax(460px,.98fr)]">
    <section className="relative hidden overflow-hidden bg-[var(--nav)] px-12 py-10 text-white lg:flex lg:flex-col xl:px-20">
      <div className="absolute -right-36 top-12 size-[520px] rounded-full border border-white/10"/><div className="absolute -right-12 top-36 size-[320px] rounded-full border border-white/10"/><div className="absolute right-20 top-[290px] h-20 w-56 -rotate-12 rounded-[50%] border-b border-[#d9967c]/55"/><div className="absolute bottom-[-140px] left-[-90px] size-[420px] rounded-full border border-[#d9967c]/20"/>
      <div className="relative flex items-center gap-3"><div className="relative flex size-11 items-center justify-center rounded-[16px] bg-[#dceee5] text-[var(--teal-dark)]"><Activity size={23}/><span className="absolute -bottom-1 -right-1 size-2.5 rounded-full border-2 border-[var(--nav)] bg-[var(--coral)]"/></div><div><div className="text-sm font-bold tracking-[.18em]">AN TÂM</div><div className="mt-1 text-[10px] font-semibold tracking-[.2em] text-white/60">NHA KHOA</div></div></div>
      <div className="relative mt-auto max-w-[590px] pb-12 pt-20"><p className="eyebrow !text-[#b4d8c8]">Chăm sóc răng miệng trọn vẹn</p><h1 className="mt-5 text-5xl font-semibold leading-[1.12] tracking-[-.045em] xl:text-[58px]">Một nụ cười khỏe bắt đầu từ sự an tâm.</h1><p className="mt-6 max-w-[490px] text-[15px] leading-7 text-white/75">Đặt lịch thuận tiện, phối hợp chăm sóc rõ ràng và lưu giữ lịch sử điều trị trong cùng một hệ thống.</p>
        <div className="mt-10 grid max-w-[490px] grid-cols-3 gap-3">{[{icon:CalendarDays,label:"Lịch khám rõ ràng"},{icon:HeartPulse,label:"Chăm sóc liên tục"},{icon:ShieldCheck,label:"Quyền riêng tư"}].map(({icon:Icon,label})=><div key={label} className="rounded-2xl border border-white/12 bg-white/[.06] p-4"><Icon size={18} className="text-[#b4d8c8]"/><p className="mt-4 text-xs font-semibold text-white/85">{label}</p></div>)}</div>
      </div><div className="relative flex items-center gap-2 pb-3 text-xs text-white/55"><BadgeCheck size={15} className="text-[#b4d8c8]"/>Hệ thống nội bộ phòng khám Nha khoa An Tâm</div>
    </section>
    <section className="flex min-h-screen items-center justify-center px-5 py-12 sm:px-10"><div className="w-full max-w-[420px]">
      <div className="mb-10 flex items-center gap-3 lg:hidden"><div className="relative flex size-10 items-center justify-center rounded-[15px] bg-[#e5f0e9] text-[var(--teal-dark)]"><Activity size={21}/><span className="absolute -bottom-1 -right-1 size-2.5 rounded-full border-2 border-[var(--paper)] bg-[var(--coral)]"/></div><div><div className="text-sm font-bold tracking-[.16em]">AN TÂM</div><div className="mt-0.5 text-[10px] font-semibold tracking-[.16em] text-[var(--muted)]">NHA KHOA</div></div></div>
      <div className="mb-8"><p className="eyebrow">Chào mừng bạn</p><h2 className="mt-3 text-[32px] font-semibold tracking-[-.04em]">Đăng nhập hệ thống</h2><p className="mt-2 text-sm leading-6 text-[var(--muted)]">Dùng tài khoản được phòng khám cấp để tiếp tục.</p></div>
      <form className="grid gap-5" onSubmit={handleSubmit(submit)} noValidate>
        <Field label="Email" error={errors.email?.message}><Input type="email" autoComplete="username" placeholder="ten@phongkham.vn" {...register("email")}/></Field>
        <Field label="Mật khẩu" error={errors.password?.message}><Input type="password" autoComplete="current-password" placeholder="Nhập mật khẩu" {...register("password")}/></Field>
        {error?<div role="alert" className="rounded-xl border border-[#f1c9c1] bg-[#fff3f0] px-3.5 py-3 text-sm text-[#a64f40]">{error}</div>:null}
        <Button type="submit" size="lg" disabled={busy} className="mt-1 w-full">{busy?"Đang đăng nhập…":"Đăng nhập"}<ArrowRight size={17}/></Button>
      </form>
      <div className="mt-8 border-t border-[var(--line)] pt-6"><div className="flex items-center justify-between"><p className="text-xs font-bold">Tài khoản demo</p><span className="text-[10px] text-[var(--muted)]">Mật khẩu hiển thị trong bước khởi động</span></div><div className="mt-3 flex flex-wrap gap-2">{demos.map(demo=><button type="button" key={demo.email} onClick={()=>setValue("email",demo.email,{shouldValidate:true})} className="focus-ring rounded-full border border-[var(--line)] bg-white px-3 py-2 text-[11px] font-semibold text-[var(--muted)] transition hover:border-[#b8dcd4] hover:bg-[#f4fbf9] hover:text-[var(--teal-dark)]">{demo.label}</button>)}</div></div>
      <p className="mt-9 text-center text-[11px] text-[#9aa5a9]">Bằng việc đăng nhập, bạn đồng ý tuân thủ chính sách bảo mật thông tin phòng khám.</p>
    </div></section>
  </main>;
}

