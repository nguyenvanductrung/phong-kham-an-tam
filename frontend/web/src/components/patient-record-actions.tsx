"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Archive, Pencil, RotateCcw, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { useApi } from "@/components/use-api";
import type { components } from "@/lib/api.generated";

type Patient = components["schemas"]["PatientSummary"];
type Action = "archive" | "restore" | "delete";
const schema = z.object({
  fullName: z.string().trim().min(2, "Nhập ít nhất 2 ký tự.").max(200),
  phone: z.string().trim().min(8, "Số điện thoại phải có ít nhất 8 ký tự.").max(32),
  email: z.union([z.email("Email chưa đúng định dạng."), z.literal("")]),
  gender: z.enum(["", "MALE", "FEMALE", "OTHER"]),
  dateOfBirth: z.string().refine(value => !value || value <= new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date()), "Ngày sinh không được nằm trong tương lai."),
  address: z.string().max(500), emergencyContact: z.string().max(500), emergencyPhone: z.string().max(32),
});
type Values = z.infer<typeof schema>;
const labels: Record<Action, string> = { archive: "Lưu trữ hồ sơ", restore: "Khôi phục hồ sơ", delete: "Xóa vĩnh viễn" };

export function PatientRecordActions({ patient, onUpdated, onDeleted }: { patient: Patient; onUpdated: (patient: Patient) => void; onDeleted: () => void }) {
  const api = useApi();
  const cache = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirmation, setConfirmation] = useState<Action | null>(null);
  const [notice, setNotice] = useState("");
  const form = useForm<Values>({ resolver: zodResolver(schema) });
  async function refresh() {
    await Promise.all([cache.invalidateQueries({ queryKey: ["patients"] }), cache.invalidateQueries({ queryKey: ["patient"] })]);
  }
  const update = useMutation({
    mutationFn: (values: Values) => api.operation("/api/v1/patients/{id}", "put", { path: { id: patient.id }, body: {
      ...values, email: values.email || null, gender: values.gender || null, dateOfBirth: values.dateOfBirth || null,
      address: values.address || null, emergencyContact: values.emergencyContact || null, emergencyPhone: values.emergencyPhone || null,
    } }),
    onSuccess: async updated => { onUpdated(updated); setEditing(false); setNotice("Đã cập nhật hồ sơ."); await refresh(); },
  });
  const lifecycle = useMutation({
    mutationFn: async (action: Action) => {
      if (action === "delete") { await api.operation("/api/v1/patients/{id}", "delete", { path: { id: patient.id } }); return null; }
      return action === "archive"
        ? api.operation("/api/v1/patients/{id}/archive", "post", { path: { id: patient.id } })
        : api.operation("/api/v1/patients/{id}/restore", "post", { path: { id: patient.id } });
    },
    onSuccess: async (updated, action) => {
      setConfirmation(null);
      if (updated) { onUpdated(updated); setNotice(action === "archive" ? "Đã lưu trữ hồ sơ. Lịch sử khám vẫn được giữ lại." : "Đã khôi phục hồ sơ."); }
      else onDeleted();
      await refresh();
    },
  });
  function edit() {
    update.reset(); setNotice("");
    form.reset({ fullName: patient.fullName, phone: patient.phone, email: patient.email ?? "", gender: patient.gender ?? "",
      dateOfBirth: patient.dateOfBirth ?? "", address: patient.address ?? "", emergencyContact: patient.emergencyContact ?? "", emergencyPhone: patient.emergencyPhone ?? "" });
    setEditing(true);
  }
  return <section className="mt-4 border-t border-[var(--line)] pt-4">
    <div className="flex flex-wrap gap-2">
      {!patient.archivedAt && <Button size="sm" variant="secondary" onClick={edit}><Pencil size={14} />Sửa thông tin</Button>}
      <Button size="sm" variant="secondary" onClick={() => { lifecycle.reset(); setNotice(""); setConfirmation(patient.archivedAt ? "restore" : "archive"); }}>
        {patient.archivedAt ? <RotateCcw size={14} /> : <Archive size={14} />}{patient.archivedAt ? "Khôi phục" : "Lưu trữ"}
      </Button>
      <Button size="sm" variant="danger" onClick={() => { lifecycle.reset(); setNotice(""); setConfirmation("delete"); }}><Trash2 size={14} />Xóa</Button>
    </div>
    {patient.archivedAt && <p className="mt-3 text-xs text-[var(--muted)]">Hồ sơ đã lưu trữ. Khôi phục để sửa thông tin hoặc đặt lịch mới.</p>}
    {notice && <p role="status" className="mt-3 text-xs text-[var(--teal-dark)]">{notice}</p>}
    {editing && <PatientDialog title="Sửa thông tin bệnh nhân" pending={update.isPending} onClose={() => setEditing(false)}>
      <p className="mt-2 text-sm text-[var(--muted)]">{patient.patientCode} · Thay đổi thông tin hành chính được lưu vào lịch sử thao tác.</p>
      <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={form.handleSubmit(values => update.mutate(values))} noValidate>
        <div className="sm:col-span-2"><Field label="Họ và tên" error={form.formState.errors.fullName?.message}><Input autoFocus {...form.register("fullName")} /></Field></div>
        <Field label="Số điện thoại" error={form.formState.errors.phone?.message}><Input {...form.register("phone")} /></Field>
        <Field label="Email" error={form.formState.errors.email?.message}><Input type="email" {...form.register("email")} /></Field>
        <Field label="Ngày sinh" error={form.formState.errors.dateOfBirth?.message}><Input type="date" {...form.register("dateOfBirth")} /></Field>
        <Field label="Giới tính"><select className="focus-ring min-h-10 rounded-xl border border-[var(--line)] px-3 text-sm" {...form.register("gender")}><option value="">Chưa chọn</option><option value="FEMALE">Nữ</option><option value="MALE">Nam</option><option value="OTHER">Khác</option></select></Field>
        <div className="sm:col-span-2"><Field label="Địa chỉ" error={form.formState.errors.address?.message}><Input {...form.register("address")} /></Field></div>
        <Field label="Người liên hệ khẩn cấp" error={form.formState.errors.emergencyContact?.message}><Input {...form.register("emergencyContact")} /></Field>
        <Field label="Số liên hệ khẩn cấp" error={form.formState.errors.emergencyPhone?.message}><Input {...form.register("emergencyPhone")} /></Field>
        {update.error && <p role="alert" className="sm:col-span-2 text-sm text-[#a64f40]">{update.error.message}</p>}
        <div className="flex justify-end gap-2 sm:col-span-2"><Button type="button" variant="secondary" disabled={update.isPending} onClick={() => setEditing(false)}>Đóng</Button><Button disabled={update.isPending}>{update.isPending ? "Đang lưu…" : "Lưu thay đổi"}</Button></div>
      </form>
    </PatientDialog>}
    {confirmation && <PatientDialog title={labels[confirmation]} pending={lifecycle.isPending} onClose={() => setConfirmation(null)}>
      <p className="mt-4 text-sm font-semibold">{patient.fullName} · {patient.patientCode}</p>
      <p className="mt-3 text-sm leading-6 text-[var(--muted)]">{confirmation === "delete"
        ? "Chỉ xóa vĩnh viễn hồ sơ tạo nhầm, chưa có lịch hẹn, liên kết tài khoản hoặc dữ liệu liên quan. Thao tác này không thể hoàn tác. Hồ sơ đã được sử dụng cần lưu trữ."
        : confirmation === "archive" ? "Hồ sơ sẽ ẩn khỏi danh sách đặt lịch mới. Lịch sử hẹn và bệnh án vẫn được giữ lại. Hãy hoàn tất hoặc hủy các lịch hẹn đang chờ trước khi lưu trữ."
          : "Hồ sơ sẽ xuất hiện lại trong danh sách đang hoạt động để sửa thông tin và đặt lịch mới."}</p>
      {lifecycle.error && <p role="alert" className="mt-3 text-sm text-[#a64f40]">{lifecycle.error.message}</p>}
      <div className="mt-6 flex justify-end gap-2"><Button variant="secondary" disabled={lifecycle.isPending} onClick={() => setConfirmation(null)}>Đóng</Button><Button variant={confirmation === "delete" ? "danger" : "primary"} disabled={lifecycle.isPending} onClick={() => lifecycle.mutate(confirmation)}>{lifecycle.isPending ? "Đang xử lý…" : labels[confirmation]}</Button></div>
    </PatientDialog>}
  </section>;
}

function PatientDialog({ title, pending, onClose, children }: { title: string; pending: boolean; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = "patient-action-dialog-title";
  useEffect(() => { const dialog = ref.current; dialog?.showModal(); return () => dialog?.close(); }, []);
  return <dialog ref={ref} aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!pending) onClose(); }}
    className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-[650px] overflow-y-auto rounded-[22px] bg-white p-5 text-[var(--ink)] shadow-2xl backdrop:bg-[#102a30]/45 sm:p-7">
    <div className="flex items-start justify-between gap-4"><h2 id={titleId} className="text-xl font-bold">{title}</h2><button type="button" aria-label="Đóng hộp thoại" disabled={pending} className="focus-ring rounded-lg p-2" onClick={onClose}><X size={18} /></button></div>
    {children}
  </dialog>;
}
