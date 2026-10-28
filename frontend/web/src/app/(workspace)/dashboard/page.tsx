import { CalendarDays, Clock3, Stethoscope, UsersRound } from "lucide-react";
import { serverApi } from "@/lib/server-api";
import { formatDate } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { getSession } from "@/lib/session";
import { redirect } from "next/navigation";

type Appointment={id:string;appointmentNumber:string;patientId:string;doctorId:string;chairId:string;scheduledAt:string;durationMinutes:number;status:string;serviceName?:string};
export const dynamic="force-dynamic";

export default async function DashboardPage(){
  const session=await getSession();
  if(session?.value.roles.includes("PATIENT"))redirect("/portal");
  const day=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
  const start=new Date(`${day}T00:00:00+07:00`).toISOString();const end=new Date(`${day}T00:00:00+07:00`);end.setDate(end.getDate()+1);
  const [response,doctorResponse]=await Promise.all([
    serverApi(`/api/v1/appointments?from=${encodeURIComponent(start)}&to=${encodeURIComponent(end.toISOString())}`),
    serverApi("/api/v1/catalog/doctors"),
  ]);
  const appointments=response?.ok?await response.json() as Appointment[]:null;
  const doctors=doctorResponse?.ok?await doctorResponse.json() as {id:string}[]:null;
  const pending=appointments?.filter(item=>["BOOKED","CONFIRMED"].includes(item.status)).length??0;
  const inProgress=appointments?.filter(item=>item.status==="IN_PROGRESS").length??0;
  const upcoming=appointments?.filter(item=>!(["COMPLETED","CANCELLED","NO_SHOW"].includes(item.status))).slice(0,5)??[];
  return <div className="mx-auto max-w-[1400px]">
    <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Tổng quan hôm nay</p><h1 className="mt-2 text-3xl font-semibold tracking-[-.04em]">Chào buổi sáng 👋</h1><p className="mt-2 text-sm text-[var(--muted)]">Lịch khám và công việc trong ngày của phòng khám.</p></div><div className="rounded-xl border border-[var(--line)] bg-white px-3.5 py-2.5 text-xs font-semibold text-[var(--muted)]">{formatDate(start)}</div></div>
    {!appointments?<div role="alert" className="soft-card mt-7 p-5 text-sm text-[#a64f40]">Chưa tải được dữ liệu lịch. Kiểm tra kết nối các dịch vụ rồi tải lại trang.</div>:null}
    <div className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{[
      {label:"Lịch trong ngày",value:appointments?.length??"—",note:"Lịch hẹn thật từ hệ thống",icon:CalendarDays,color:"text-[#168174]",bg:"bg-[#e8f6f2]"},
      {label:"Chờ tiếp đón",value:appointments?pending:"—",note:"Đã đặt hoặc đã xác nhận",icon:Clock3,color:"text-[#b1792f]",bg:"bg-[#fff4e5]"},
      {label:"Đang khám",value:appointments?inProgress:"—",note:"Lượt khám đang thực hiện",icon:Stethoscope,color:"text-[#7764ad]",bg:"bg-[#f0edfa]"},
      {label:"Bác sĩ nhận lịch",value:doctors?.length??"—",note:"Theo danh mục hiện hành",icon:UsersRound,color:"text-[#467d8a]",bg:"bg-[#eaf4f5]"},
    ].map(({label,value,note,icon:Icon,color,bg})=><article key={label} className="soft-card p-5"><div className="flex items-start justify-between"><div><p className="text-xs font-semibold text-[var(--muted)]">{label}</p><p className="mt-3 text-[32px] font-semibold tracking-[-.05em]">{value}</p></div><div className={`flex size-10 items-center justify-center rounded-xl ${bg} ${color}`}><Icon size={19}/></div></div><p className="mt-3 text-[11px] text-[var(--muted)]">{note}</p></article>)}</div>
    <div className="mt-6 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(300px,.65fr)]">
      <section className="soft-card overflow-hidden"><div className="flex items-center justify-between border-b border-[var(--line)] px-5 py-4"><div><p className="text-sm font-bold">Lịch sắp tới</p><p className="mt-1 text-xs text-[var(--muted)]">Các lượt chưa hoàn thành trong ngày</p></div><a href="/appointments" className="text-xs font-bold text-[var(--teal-dark)]">Mở lịch <span aria-hidden="true">→</span></a></div>
        {!appointments?.length?<div className="px-6 py-14 text-center"><div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-[#eef6f4] text-[var(--teal)]"><CalendarDays size={21}/></div><p className="mt-4 text-sm font-semibold">Chưa có lịch khám hôm nay</p><p className="mt-1 text-xs text-[var(--muted)]">Lịch mới sẽ xuất hiện tại đây sau khi được đặt.</p><a href="/appointments" className="mt-4 inline-flex text-xs font-bold text-[var(--teal-dark)]">Tạo lịch hẹn</a></div>:null}
        {appointments?.length&&upcoming.length===0?<div className="px-6 py-10 text-center text-sm text-[var(--muted)]">Tất cả lịch hôm nay đã hoàn tất.</div>:null}
        <div className="divide-y divide-[var(--line)]">{upcoming.map(item=><div key={item.id} className="flex flex-wrap items-center gap-3 px-5 py-4"><div className="min-w-[92px]"><p className="text-sm font-bold">{new Intl.DateTimeFormat("vi-VN",{timeZone:"Asia/Ho_Chi_Minh",hour:"2-digit",minute:"2-digit"}).format(new Date(item.scheduledAt))}</p><p className="mt-1 text-[10px] text-[var(--muted)]">{item.durationMinutes} phút</p></div><div className="min-w-[160px] flex-1"><p className="text-xs font-bold">{item.appointmentNumber}</p><p className="mt-1 text-[11px] text-[var(--muted)]">{item.serviceName??"Lịch khám"}</p></div><Badge value={item.status}/></div>)}</div>
      </section>
      <aside className="grid gap-5"><section className="soft-card p-5"><p className="eyebrow">Thao tác nhanh</p><div className="mt-4 grid gap-2"><a href="/appointments" className="flex items-center gap-3 rounded-xl border border-[var(--line)] p-3 transition hover:bg-[#f6faf9]"><span className="flex size-9 items-center justify-center rounded-xl bg-[#e8f6f2] text-[var(--teal)]"><CalendarDays size={17}/></span><span className="flex-1"><span className="block text-xs font-bold">Đặt lịch khám</span><span className="mt-1 block text-[10px] text-[var(--muted)]">Chọn bác sĩ, ghế và giờ trống</span></span><span className="text-[var(--muted)]">→</span></a><a href="/patients" className="flex items-center gap-3 rounded-xl border border-[var(--line)] p-3 transition hover:bg-[#f6faf9]"><span className="flex size-9 items-center justify-center rounded-xl bg-[#fff3e8] text-[#b57b37]"><UsersRound size={17}/></span><span className="flex-1"><span className="block text-xs font-bold">Thêm bệnh nhân</span><span className="mt-1 block text-[10px] text-[var(--muted)]">Tạo hồ sơ hành chính mới</span></span><span className="text-[var(--muted)]">→</span></a></div></section>
        <section className="rounded-[18px] bg-[#dff1ec] p-5"><div className="flex size-9 items-center justify-center rounded-xl bg-white/80 text-[var(--teal-dark)]"><Clock3 size={17}/></div><p className="mt-4 text-sm font-bold text-[#214b47]">Theo dõi giờ tiếp đón</p><p className="mt-2 text-xs leading-5 text-[#517671]">Xác nhận lịch và check-in đúng trạng thái giúp bác sĩ xem được hàng đợi hiện tại.</p></section></aside>
    </div>
  </div>;
}

