"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, Plus, RefreshCw, Search, Stethoscope, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useApi } from "@/components/use-api";
import { useSession } from "@/components/session-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { ApiError } from "@/lib/api-client";
import type { components } from "@/lib/api.generated";
import { formatMoney } from "@/lib/utils";

type Appointment = components["schemas"]["AppointmentSummary"];
type Patient = components["schemas"]["PatientSummary"];
type Slot = components["schemas"]["AvailabilitySlot"];
type BookingTarget = { id: string; durationMinutes: number } | null;

const statusLabels: Record<string, string> = { BOOKED: "Đã đặt", CONFIRMED: "Đã xác nhận", ARRIVED: "Đã đến", IN_PROGRESS: "Đang khám", COMPLETED: "Hoàn tất", CANCELLED: "Đã hủy", NO_SHOW: "Vắng mặt" };
const todayInClinic = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const clinicRange = (date: string, days: number) => {
  const from = new Date(`${date}T00:00:00+07:00`);
  const to = new Date(from);
  to.setDate(to.getDate() + days);
  return { from: from.toISOString(), to: to.toISOString() };
};
const inputClass = "focus-ring min-h-10 w-full rounded-xl border border-[var(--line)] bg-white px-3 text-sm";

export function AppointmentsWorkspace() {
  const api = useApi();
  const session = useSession();
  const queryClient = useQueryClient();
  const isPatient = session.roles.includes("PATIENT");
  const canSchedule = isPatient || session.roles.some((role) => ["SUPER_ADMIN", "CLINIC_MANAGER", "RECEPTIONIST"].includes(role));
  const canConfirm = session.roles.some((role) => ["SUPER_ADMIN", "CLINIC_MANAGER", "RECEPTIONIST"].includes(role));
  const canCheckIn = session.roles.some((role) => ["SUPER_ADMIN", "CLINIC_MANAGER", "RECEPTIONIST", "ASSISTANT"].includes(role));
  const canStart = session.roles.includes("DOCTOR");
  const [date, setDate] = useState(todayInClinic);
  const [period, setPeriod] = useState<1 | 7>(1);
  const [doctorFilter, setDoctorFilter] = useState("");
  const [chairFilter, setChairFilter] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [rescheduleTarget, setRescheduleTarget] = useState<BookingTarget>(null);
  const [cancelTarget, setCancelTarget] = useState<Appointment | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [patientSearch, setPatientSearch] = useState("");
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const [doctorId, setDoctorId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [bookingDate, setBookingDate] = useState(todayInClinic);
  const [slotAt, setSlotAt] = useState("");
  const [chairId, setChairId] = useState("");
  const [reason, setReason] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    const update = () => setNow(Date.now());
    update();
    const timer = window.setInterval(update, 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const range = useMemo(() => clinicRange(date, period), [date, period]);
  const appointmentsQuery = useQuery({
    queryKey: ["appointments", range.from, range.to],
    queryFn: () => api.operation("/api/v1/appointments", "get", { query: { from: range.from, to: range.to } }),
  });
  const doctorsQuery = useQuery({ queryKey: ["catalog", "doctors"], queryFn: () => api.operation("/api/v1/catalog/doctors", "get", {}) });
  const chairsQuery = useQuery({ queryKey: ["catalog", "chairs"], queryFn: () => api.operation("/api/v1/catalog/chairs", "get", {}) });
  const servicesQuery = useQuery({ queryKey: ["catalog", "services"], queryFn: () => api.operation("/api/v1/catalog/services", "get", {}) });
  const currentPatientQuery = useQuery({ queryKey: ["patient", "me"], queryFn: () => api.operation("/api/v1/patients/me", "get", {}), enabled: isPatient });
  const patientSearchQuery = useQuery({
    queryKey: ["patients", "appointment-picker", patientSearch],
    queryFn: () => api.operation("/api/v1/patients", "get", { query: { q: patientSearch, page: 0, size: 8 } }),
    enabled: !isPatient && createOpen && patientSearch.trim().length >= 2 && !selectedPatient,
  });
  const selectedService = servicesQuery.data?.find((service) => service.id === serviceId);
  const effectiveDoctorId = rescheduleTarget ? appointmentsQuery.data?.find((item) => item.id === rescheduleTarget.id)?.doctorId ?? "" : doctorId;
  const effectiveDuration = rescheduleTarget?.durationMinutes ?? selectedService?.durationMinutes;
  const availabilityQuery = useQuery({
    queryKey: ["availability", effectiveDoctorId, bookingDate, effectiveDuration],
    queryFn: () => api.operation("/api/v1/availability", "get", { query: { doctorId: effectiveDoctorId, date: bookingDate, durationMinutes: effectiveDuration ?? 15 } }),
    enabled: (createOpen || !!rescheduleTarget) && !!effectiveDoctorId && !!effectiveDuration && !!bookingDate,
  });

  const refreshAppointments = async () => {
    await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    await queryClient.invalidateQueries({ queryKey: ["availability"] });
  };
  const createMutation = useMutation({
    mutationFn: async () => {
      const patient = isPatient ? currentPatientQuery.data : selectedPatient;
      if (!patient || !selectedService || !slotAt || !chairId || !doctorId) throw new Error("Chọn bệnh nhân, bác sĩ, dịch vụ và giờ khám còn trống.");
      return api.operation("/api/v1/appointments", "post", {
        parameters: { header: { "Idempotency-Key": crypto.randomUUID() } },
        body: { patientId: patient.id, doctorId, chairId, serviceId: selectedService.id, scheduledAt: slotAt, durationMinutes: selectedService.durationMinutes, reason: reason.trim() || null },
      });
    },
    onSuccess: async (appointment) => {
      setError(""); setNotice(`Đã tạo lịch ${appointment.appointmentNumber}.`); setCreateOpen(false); resetBooking(); await refreshAppointments();
    },
    onError: (cause) => setError(messageForError(cause)),
  });
  const commandMutation = useMutation({
    mutationFn: ({ appointment, type, cancellationReason }: { appointment: Appointment; type: string; cancellationReason?: string }) => api<Appointment>(`/api/v1/appointments/${appointment.id}/commands`, {
      method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ type, reason: cancellationReason || null }),
    }),
    onSuccess: async (appointment) => { setError(""); setNotice(`Lịch ${appointment.appointmentNumber}: ${statusLabels[appointment.status] ?? appointment.status}.`); setCancelTarget(null); setCancelReason(""); await refreshAppointments(); },
    onError: (cause) => setError(messageForError(cause)),
  });
  const rescheduleMutation = useMutation({
    mutationFn: () => {
      const appointment = appointmentsQuery.data?.find((item) => item.id === rescheduleTarget?.id);
      if (!appointment || !slotAt || !chairId || !effectiveDuration) throw new Error("Chọn giờ khám và ghế còn trống.");
      return api<Appointment>(`/api/v1/appointments/${appointment.id}/reschedule`, {
        method: "PUT", headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ scheduledAt: slotAt, durationMinutes: effectiveDuration, chairId }),
      });
    },
    onSuccess: async (appointment) => { setError(""); setNotice(`Đã đổi giờ lịch ${appointment.appointmentNumber}.`); setRescheduleTarget(null); resetBooking(); await refreshAppointments(); },
    onError: (cause) => setError(messageForError(cause)),
  });

  const appointments = appointmentsQuery.data ?? [];
  const visibleAppointments = appointments.filter((item) => (!doctorFilter || item.doctorId === doctorFilter) && (!chairFilter || item.chairId === chairFilter));
  const doctors = doctorsQuery.data ?? [];
  const chairs = chairsQuery.data ?? [];
  const doctorNames = new Map(doctors.map((item) => [item.id, item.name]));
  const chairNames = new Map(chairs.map((item) => [item.id, item.name]));
  const selectedSlot = availabilityQuery.data?.find((slot) => slot.startsAt === slotAt);
  const slots = availabilityQuery.data ?? [];
  const panelOpen = createOpen || !!rescheduleTarget;

  function resetBooking() {
    setSelectedPatient(null); setPatientSearch(""); setDoctorId(""); setServiceId(""); setBookingDate(todayInClinic()); setSlotAt(""); setChairId(""); setReason("");
  }
  function beginCreate() {
    setError(""); setNotice(""); setRescheduleTarget(null); resetBooking(); setCreateOpen(true);
  }
  function beginReschedule(appointment: Appointment) {
    setError(""); setNotice(""); setCreateOpen(false); setSelectedPatient(null); setRescheduleTarget({ id: appointment.id, durationMinutes: appointment.durationMinutes });
    setBookingDate(new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(appointment.scheduledAt)));
    setSlotAt(""); setChairId("");
  }
  function chooseSlot(slot: Slot) { setSlotAt(slot.startsAt); setChairId(slot.availableChairs[0] ?? ""); }

  return <div className="mx-auto max-w-[1400px]">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="eyebrow">{isPatient ? "Chăm sóc của bạn" : "Điều phối phòng khám"}</p><h1 className="mt-2 text-3xl font-semibold tracking-[-.04em]">{isPatient ? "Lịch hẹn của tôi" : "Lịch khám"}</h1><p className="mt-2 text-sm text-[var(--muted)]">{isPatient ? "Xem, đặt hoặc đổi lịch khám của bạn." : "Lịch được lấy trực tiếp từ hệ thống đặt hẹn."}</p></div>
      {canSchedule ? <Button onClick={beginCreate}><Plus size={16}/>{isPatient ? "Đặt lịch khám" : "Tạo lịch hẹn"}</Button> : null}
    </div>
    {notice ? <div role="status" className="mt-5 rounded-xl border border-[#bfe2d7] bg-[#effaf6] px-4 py-3 text-sm text-[#236d5f]">{notice}</div> : null}
    {error && !panelOpen && !cancelTarget ? <div role="alert" className="mt-5 rounded-xl border border-[#f1c9c1] bg-[#fff3f0] px-4 py-3 text-sm text-[#a64f40]">{error}</div> : null}
    <section className="soft-card mt-6 overflow-hidden">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-[var(--line)] px-5 py-4">
        <div><p className="text-sm font-bold">{period === 1 ? "Lịch trong ngày" : "Lịch trong tuần"}</p><p className="mt-1 text-xs text-[var(--muted)]">{visibleAppointments.length} lịch trong khoảng đã chọn</p></div>
        <div className="flex flex-wrap items-end gap-2">
          <Field label="Ngày bắt đầu"><Input type="date" value={date} onChange={(event) => setDate(event.target.value)} className="w-[155px]"/></Field>
          <div className="flex rounded-xl border border-[var(--line)] bg-white p-1"><button className={`rounded-lg px-3 py-2 text-xs font-semibold ${period === 1 ? "bg-[var(--teal-soft)] text-[var(--teal-dark)]" : "text-[var(--muted)]"}`} onClick={() => setPeriod(1)}>Ngày</button><button className={`rounded-lg px-3 py-2 text-xs font-semibold ${period === 7 ? "bg-[var(--teal-soft)] text-[var(--teal-dark)]" : "text-[var(--muted)]"}`} onClick={() => setPeriod(7)}>Tuần</button></div>
          {!isPatient ? <><Field label="Bác sĩ"><select className={`${inputClass} w-[170px]`} value={doctorFilter} onChange={(event) => setDoctorFilter(event.target.value)}><option value="">Tất cả bác sĩ</option>{doctors.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.name}</option>)}</select></Field><Field label="Ghế"><select className={`${inputClass} w-[130px]`} value={chairFilter} onChange={(event) => setChairFilter(event.target.value)}><option value="">Tất cả ghế</option>{chairs.map((chair) => <option key={chair.id} value={chair.id}>{chair.name}</option>)}</select></Field></> : null}
        </div>
      </div>
      {appointmentsQuery.isLoading ? <div className="p-10 text-center text-sm text-[var(--muted)]">Đang tải lịch hẹn…</div> : null}
      {appointmentsQuery.isError ? <div role="alert" className="p-8 text-center text-sm text-[#a64f40]">Không tải được lịch hẹn. Vui lòng thử lại.<div className="mt-3"><Button variant="secondary" size="sm" onClick={() => void appointmentsQuery.refetch()}>Thử lại</Button></div></div> : null}
      {appointmentsQuery.data && visibleAppointments.length === 0 ? <div className="px-6 py-16 text-center"><div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-[#eef6f4] text-[var(--teal)]"><CalendarDays size={21}/></div><p className="mt-4 text-sm font-semibold">Chưa có lịch trong khoảng này</p><p className="mt-1 text-xs text-[var(--muted)]">Chọn ngày khác hoặc tạo lịch hẹn mới.</p></div> : null}
      {visibleAppointments.length > 0 ? <div className="divide-y divide-[var(--line)]">{visibleAppointments.map((appointment) => <article key={appointment.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
        <div className="flex size-11 shrink-0 flex-col items-center justify-center rounded-xl bg-[#eef6f4] text-[var(--teal-dark)]"><span className="text-[10px] font-bold">{new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit" }).format(new Date(appointment.scheduledAt))}</span><span className="mt-0.5 text-[9px]">{new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit" }).format(new Date(appointment.scheduledAt))}</span></div>
        <div className="min-w-[180px] flex-1"><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-bold">{appointment.appointmentNumber}</p><Badge value={appointment.status}/></div><p className="mt-1 text-xs text-[var(--muted)]">{appointment.serviceName} · {appointment.durationMinutes} phút · {formatMoney(appointment.estimatedPrice)}</p><p className="mt-1 text-[10px] text-[var(--muted)]">{isPatient ? `Bác sĩ ${doctorNames.get(appointment.doctorId) ?? "đang cập nhật"} · ${chairNames.get(appointment.chairId) ?? "ghế đang cập nhật"}` : `Bệnh nhân ${appointment.patientId.slice(0, 8)} · Bác sĩ ${doctorNames.get(appointment.doctorId) ?? "—"} · ${chairNames.get(appointment.chairId) ?? "—"}`}</p></div>
        <div className="flex flex-wrap items-center gap-2">
          {canConfirm && appointment.status === "BOOKED" ? <Button size="sm" variant="secondary" onClick={() => commandMutation.mutate({ appointment, type: "CONFIRM" })}><Check size={14}/>Xác nhận</Button> : null}
          {canCheckIn && appointment.status === "CONFIRMED" ? <Button size="sm" variant="secondary" onClick={() => commandMutation.mutate({ appointment, type: "CHECK_IN" })}><Check size={14}/>Tiếp đón</Button> : null}
          {canStart && appointment.status === "ARRIVED" ? <Button size="sm" onClick={() => commandMutation.mutate({ appointment, type: "START" })}><Stethoscope size={14}/>Bắt đầu khám</Button> : null}
          {canSchedule && ["BOOKED", "CONFIRMED"].includes(appointment.status) ? <Button size="sm" variant="secondary" onClick={() => beginReschedule(appointment)}><RefreshCw size={14}/>Đổi lịch</Button> : null}
          {(canSchedule || isPatient) && ["BOOKED", "CONFIRMED"].includes(appointment.status) ? <Button size="sm" variant="danger" onClick={() => { setError(""); setCancelTarget(appointment); }}><X size={14}/>Hủy</Button> : null}
          {canConfirm && now !== null && ["BOOKED", "CONFIRMED"].includes(appointment.status) && now > new Date(appointment.scheduledAt).getTime() + 15 * 60_000 ? <Button size="sm" variant="ghost" onClick={() => commandMutation.mutate({ appointment, type: "MARK_NO_SHOW" })}>Vắng mặt</Button> : null}
        </div>
      </article>)}</div> : null}
    </section>

    {panelOpen ? <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a30]/45 p-3 sm:p-6" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) { setCreateOpen(false); setRescheduleTarget(null); setError(""); } }}>
      <section role="dialog" aria-modal="true" aria-labelledby="booking-title" className="max-h-[94vh] w-full max-w-[760px] overflow-y-auto rounded-[22px] bg-white p-5 shadow-2xl sm:p-7">
        <div className="flex items-start justify-between"><div><p className="eyebrow">Lịch khám · {isPatient ? "Đặt trực tuyến" : "Điều phối"}</p><h2 id="booking-title" className="mt-2 text-xl font-bold">{rescheduleTarget ? "Đổi lịch hẹn" : "Tạo lịch hẹn"}</h2></div><button className="focus-ring rounded-lg p-2 text-[var(--muted)] hover:bg-[#f2f6f5]" aria-label="Đóng" onClick={() => { setCreateOpen(false); setRescheduleTarget(null); setError(""); }}><X size={18}/></button></div>
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          {!isPatient && !rescheduleTarget ? <div className="sm:col-span-2"><Field label="Tìm hồ sơ bệnh nhân"><div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#98a6aa]" size={16}/><Input className="pl-9" value={selectedPatient ? `${selectedPatient.fullName} · ${selectedPatient.patientCode}` : patientSearch} onChange={(event) => { setSelectedPatient(null); setPatientSearch(event.target.value); }} placeholder="Nhập tên, mã hồ sơ hoặc số điện thoại" aria-label="Tìm bệnh nhân"/></div></Field>
            {patientSearchQuery.isFetching ? <p className="mt-2 text-xs text-[var(--muted)]">Đang tìm hồ sơ…</p> : null}
            {patientSearchQuery.data?.items.length ? <div className="mt-2 max-h-44 overflow-auto rounded-xl border border-[var(--line)]">{patientSearchQuery.data.items.map((patient) => <button key={patient.id} className="flex w-full items-center justify-between border-b border-[var(--line)] px-3 py-2.5 text-left last:border-0 hover:bg-[#f6faf9]" onClick={() => { setSelectedPatient(patient); setPatientSearch(""); }}><span><span className="block text-xs font-bold">{patient.fullName}</span><span className="mt-1 block text-[10px] text-[var(--muted)]">{patient.patientCode} · {patient.phone}</span></span><span className="text-xs text-[var(--teal-dark)]">Chọn</span></button>)}</div> : null}
            {selectedPatient ? <p className="mt-2 text-xs text-[var(--teal-dark)]">Đã chọn: {selectedPatient.fullName} · {selectedPatient.phone}</p> : null}
          </div> : null}
          {!rescheduleTarget ? <Field label="Dịch vụ"><select className={inputClass} value={serviceId} onChange={(event) => { setServiceId(event.target.value); setSlotAt(""); setChairId(""); }}><option value="">Chọn dịch vụ</option>{(servicesQuery.data ?? []).map((service) => <option key={service.id} value={service.id}>{service.name} · {service.durationMinutes} phút · {formatMoney(service.price)}</option>)}</select></Field> : <div className="rounded-xl bg-[#f5f8f7] p-3 text-xs text-[var(--muted)]">Giữ nguyên bác sĩ và thời lượng: <strong className="text-[var(--ink)]">{effectiveDuration} phút</strong></div>}
          {!rescheduleTarget ? <Field label="Bác sĩ"><select className={inputClass} value={doctorId} onChange={(event) => { setDoctorId(event.target.value); setSlotAt(""); setChairId(""); }}><option value="">Chọn bác sĩ</option>{doctors.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.name} · {doctor.specialty}</option>)}</select></Field> : null}
          <Field label="Ngày khám"><Input type="date" min={todayInClinic()} value={bookingDate} onChange={(event) => { setBookingDate(event.target.value); setSlotAt(""); setChairId(""); }}/></Field>
          <div className="sm:col-span-2"><div className="flex items-center justify-between"><p className="text-xs font-semibold">Giờ còn trống</p>{availabilityQuery.isFetching ? <span className="text-[10px] text-[var(--muted)]">Đang tải…</span> : null}</div>
            {availabilityQuery.isError ? <div role="alert" className="mt-2 rounded-lg bg-[#fff3f0] p-3 text-xs text-[#a64f40]">Không tải được giờ trống. Thử lại sau.</div> : null}
            {availabilityQuery.data && slots.length === 0 ? <div className="mt-2 rounded-xl border border-dashed border-[var(--line)] px-4 py-6 text-center text-xs text-[var(--muted)]">Không có giờ phù hợp trong ngày này. Hãy chọn ngày khác.</div> : null}
            {slots.length > 0 ? <div className="mt-2 grid grid-cols-3 gap-2 sm:grid-cols-5">{slots.map((slot) => <button key={slot.startsAt} className={`focus-ring min-h-10 rounded-xl border px-2 text-xs font-semibold ${slotAt === slot.startsAt ? "border-[var(--teal)] bg-[var(--teal-soft)] text-[var(--teal-dark)]" : "border-[var(--line)] hover:bg-[#f5f9f8]"}`} onClick={() => chooseSlot(slot)}>{new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit" }).format(new Date(slot.startsAt))}</button>)}</div> : null}
          </div>
          {selectedSlot ? <Field label="Ghế"><select className={inputClass} value={chairId} onChange={(event) => setChairId(event.target.value)}>{selectedSlot.availableChairs.map((id) => <option key={id} value={id}>{chairNames.get(id) ?? id}</option>)}</select></Field> : null}
          {!rescheduleTarget ? <Field label="Ghi chú cho lịch hẹn"><Input maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Nội dung cần lưu ý (không bắt buộc)"/></Field> : null}
          {isPatient && !currentPatientQuery.data && !currentPatientQuery.isLoading ? <div role="alert" className="sm:col-span-2 rounded-xl bg-[#fff3f0] p-3 text-xs text-[#a64f40]">Tài khoản chưa được liên kết với hồ sơ bệnh nhân. Vui lòng liên hệ phòng khám.</div> : null}
          {error ? <div role="alert" className="sm:col-span-2 rounded-xl border border-[#f1c9c1] bg-[#fff3f0] px-3.5 py-3 text-sm text-[#a64f40]">{error}</div> : null}
          <div className="mt-2 flex justify-end gap-2 sm:col-span-2"><Button variant="secondary" onClick={() => { setCreateOpen(false); setRescheduleTarget(null); }}>Đóng</Button>{rescheduleTarget ? <Button disabled={rescheduleMutation.isPending || !slotAt || !chairId} onClick={() => rescheduleMutation.mutate()}>{rescheduleMutation.isPending ? "Đang đổi…" : "Lưu giờ mới"}</Button> : <Button disabled={createMutation.isPending || !slotAt || !chairId || !doctorId || !serviceId || (!isPatient && !selectedPatient)} onClick={() => createMutation.mutate()}>{createMutation.isPending ? "Đang lưu…" : "Tạo lịch"}</Button>}</div>
        </div>
      </section>
    </div> : null}

    {cancelTarget ? <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a30]/45 p-3" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setCancelTarget(null); }}><section role="dialog" aria-modal="true" aria-labelledby="cancel-title" className="w-full max-w-[460px] rounded-[22px] bg-white p-6 shadow-2xl"><p className="eyebrow">Hủy lịch hẹn</p><h2 id="cancel-title" className="mt-2 text-xl font-bold">{cancelTarget.appointmentNumber}</h2><p className="mt-2 text-sm text-[var(--muted)]">Lịch đã hủy vẫn được lưu trong lịch sử. {isPatient ? "Bạn có thể tự hủy trước giờ hẹn ít nhất 2 tiếng." : "Nhập lý do hủy để lưu vào lịch sử."}</p>{!isPatient ? <div className="mt-4"><Field label="Lý do hủy"><Input maxLength={500} value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} placeholder="Ví dụ: bệnh nhân báo bận"/></Field></div> : null}{error ? <div role="alert" className="mt-3 text-sm text-[#a64f40]">{error}</div> : null}<div className="mt-6 flex justify-end gap-2"><Button variant="secondary" onClick={() => setCancelTarget(null)}>Quay lại</Button><Button variant="danger" disabled={commandMutation.isPending || (!isPatient && !cancelReason.trim())} onClick={() => commandMutation.mutate({ appointment: cancelTarget, type: "CANCEL", cancellationReason: cancelReason.trim() })}>{commandMutation.isPending ? "Đang hủy…" : "Xác nhận hủy"}</Button></div></section></div> : null}
  </div>;
}

function messageForError(cause: unknown) {
  if (cause instanceof ApiError) {
    if (cause.code === "APPOINTMENT_CONFLICT") return "Khung giờ vừa được đặt. Hãy chọn giờ khác trong danh sách còn trống.";
    if (cause.code === "IDEMPOTENCY_KEY_REUSED") return "Yêu cầu bị gửi lặp với nội dung khác. Hãy thử lại.";
    if (cause.code === "CANCELLATION_WINDOW_CLOSED") return "Bạn chỉ có thể tự hủy lịch trước giờ hẹn ít nhất 2 tiếng.";
    if (cause.status === 403) return "Bạn không có quyền thực hiện thao tác này.";
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể hoàn thành yêu cầu. Vui lòng thử lại.";
}
