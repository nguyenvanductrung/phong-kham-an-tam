"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, CalendarDays, Clock3, HeartPulse, Stethoscope } from "lucide-react";
import { useMemo } from "react";
import { useApi } from "@/components/use-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate, formatMoney } from "@/lib/utils";

type Patient = { id: string; patientCode: string; fullName: string; phone: string; email: string | null; allergies?: string | null };
type Appointment = { id: string; appointmentNumber: string; doctorId: string; chairId: string; scheduledAt: string; durationMinutes: number; status: string; serviceName: string; estimatedPrice: number };
type Doctor = { id: string; name: string; specialty: string };
type Service = { id: string; name: string; durationMinutes: number; price: number };

export function PatientPortal() {
  const api = useApi();
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const ranges = useMemo(() => {
    const start = new Date(`${today}T00:00:00+07:00`);
    return Array.from({ length: 3 }, (_, index) => {
      const from = new Date(start);
      from.setUTCDate(from.getUTCDate() + index * 30);
      const to = new Date(from);
      to.setUTCDate(to.getUTCDate() + 30);
      return { from: from.toISOString(), to: to.toISOString() };
    });
  }, [today]);
  const patient = useQuery({ queryKey: ["patient", "me"], queryFn: () => api<Patient>("/api/v1/patients/me") });
  const appointments = useQuery({
    queryKey: ["appointments", "patient", today, 90],
    queryFn: async () => {
      const pages = await Promise.all(ranges.map(({ from, to }) =>
        api<Appointment[]>(`/api/v1/appointments?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
      ));
      const unique = new Map<string, Appointment>();
      for (const page of pages) for (const appointment of page) unique.set(appointment.id, appointment);
      return [...unique.values()].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
    },
  });
  const doctors = useQuery({ queryKey: ["catalog", "doctors"], queryFn: () => api<Doctor[]>("/api/v1/catalog/doctors") });
  const services = useQuery({ queryKey: ["catalog", "services"], queryFn: () => api<Service[]>("/api/v1/catalog/services") });
  const doctorNames = new Map((doctors.data ?? []).map((doctor) => [doctor.id, doctor.name]));
  const upcoming = appointments.data?.filter((item) => ["BOOKED", "CONFIRMED"].includes(item.status)) ?? [];
  return <div className="mx-auto max-w-[1200px]">
    <section className="overflow-hidden rounded-[24px] bg-[#17363b] p-6 text-white sm:p-9"><p className="text-xs font-semibold uppercase tracking-[.15em] text-[#a6d5ca]">Cổng thông tin bệnh nhân</p><div className="mt-3 flex flex-wrap items-end justify-between gap-5"><div><h1 className="text-3xl font-semibold tracking-[-.04em] sm:text-4xl">Xin chào{patient.data ? `, ${patient.data.fullName}` : ""}</h1><p className="mt-3 max-w-xl text-sm leading-6 text-white/65">Theo dõi lịch khám, kết quả và đơn thuốc đã được bác sĩ chốt.</p></div><a href="/appointments"><Button variant="secondary"><CalendarDays size={16}/>Đặt lịch khám</Button></a></div></section>
    {patient.isError ? <div role="alert" className="mt-5 rounded-xl bg-[#fff3f0] p-4 text-sm text-[#a64f40]">Tài khoản chưa liên kết hồ sơ bệnh nhân. Vui lòng liên hệ lễ tân để kết nối hồ sơ an toàn.</div> : null}
    <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1.3fr)_minmax(280px,.7fr)]">
      <section className="soft-card overflow-hidden"><div className="flex items-center justify-between border-b border-[var(--line)] px-5 py-4"><div><p className="text-sm font-bold">Lịch khám sắp tới</p><p className="mt-1 text-xs text-[var(--muted)]">Trong 90 ngày tiếp theo</p></div><a href="/appointments" className="text-xs font-bold text-[var(--teal-dark)]">Quản lý lịch <ArrowRight className="ml-1 inline" size={14}/></a></div>
        {appointments.isLoading ? <p className="p-8 text-center text-sm text-[var(--muted)]">Đang tải lịch hẹn…</p> : null}
        {appointments.isError ? <div role="alert" className="p-6 text-sm text-[#a64f40]">Không thể tải lịch hẹn. Hãy thử lại trong giây lát.<div className="mt-3"><Button size="sm" variant="secondary" onClick={() => void appointments.refetch()}>Thử lại</Button></div></div> : null}
        {!appointments.isLoading && !appointments.isError && upcoming.length === 0 ? <div className="px-6 py-12 text-center"><div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-[#eef6f4] text-[var(--teal)]"><CalendarDays size={20}/></div><p className="mt-4 text-sm font-semibold">Chưa có lịch khám sắp tới</p><p className="mt-1 text-xs text-[var(--muted)]">Chọn bác sĩ và giờ trống để đặt lịch.</p><a href="/appointments" className="mt-4 inline-flex text-xs font-bold text-[var(--teal-dark)]">Tìm lịch trống <ArrowRight size={14} className="ml-1"/></a></div> : null}
        <div className="divide-y divide-[var(--line)]">{upcoming.map((item) => <article key={item.id} className="flex flex-wrap items-center gap-3 px-5 py-4"><div className="flex size-11 flex-col items-center justify-center rounded-xl bg-[#eef6f4] text-[var(--teal-dark)]"><CalendarDays size={16}/></div><div className="min-w-[180px] flex-1"><p className="text-xs font-bold">{item.serviceName} · {item.appointmentNumber}</p><p className="mt-1 text-[11px] text-[var(--muted)]">{formatDate(item.scheduledAt)} · {item.durationMinutes} phút · BS. {doctorNames.get(item.doctorId) ?? "đang cập nhật"}</p><p className="mt-1 text-[11px] text-[var(--muted)]">Dự kiến {formatMoney(item.estimatedPrice)}</p></div><Badge value={item.status}/></article>)}</div>
      </section>
      <aside className="grid gap-5"><section className="soft-card p-5"><div className="flex items-center gap-2"><HeartPulse size={17} className="text-[var(--teal)]"/><h2 className="text-sm font-bold">Hồ sơ của tôi</h2></div>{patient.isLoading ? <p className="mt-3 text-xs text-[var(--muted)]">Đang tải hồ sơ…</p> : patient.data ? <div className="mt-4 grid gap-2 text-xs"><p><span className="text-[var(--muted)]">Mã hồ sơ:</span> {patient.data.patientCode}</p><p><span className="text-[var(--muted)]">Điện thoại:</span> {patient.data.phone}</p>{patient.data.email ? <p><span className="text-[var(--muted)]">Email:</span> {patient.data.email}</p> : null}{patient.data.allergies ? <p className="rounded-lg bg-[#fff5ed] p-2 text-[#8c572d]"><b>Dị ứng đã lưu:</b> {patient.data.allergies}</p> : null}</div> : null}<a href="/records" className="mt-4 block border-t border-[var(--line)] pt-3 text-xs font-bold text-[var(--teal-dark)]">Kết quả khám và đơn thuốc <ArrowRight className="ml-1 inline" size={14}/></a></section>
        <section className="soft-card p-5"><div className="flex items-center gap-2"><Stethoscope size={17} className="text-[var(--teal)]"/><h2 className="text-sm font-bold">Bác sĩ và dịch vụ</h2></div>{doctors.isLoading || services.isLoading ? <p className="mt-3 text-xs text-[var(--muted)]">Đang tải danh mục…</p> : <><p className="mt-3 text-xs text-[var(--muted)]">{doctors.data?.length ?? 0} bác sĩ đang nhận lịch</p><div className="mt-3 grid gap-2">{(services.data ?? []).slice(0, 3).map((service) => <div key={service.id} className="flex justify-between gap-3 text-xs"><span>{service.name} · {service.durationMinutes} phút</span><b>{formatMoney(service.price)}</b></div>)}</div></>}</section>
        <section className="rounded-[18px] bg-[#e4f2ed] p-5"><div className="flex items-center gap-2 text-[#1c675b]"><Clock3 size={17}/><p className="text-sm font-bold">Đổi hoặc hủy lịch</p></div><p className="mt-2 text-xs leading-5 text-[#53746f]">Bạn có thể tự hủy lịch trước giờ hẹn ít nhất 2 tiếng. Lịch đã hủy vẫn được lưu lại trong lịch sử.</p></section>
      </aside>
    </div>
  </div>;
}
