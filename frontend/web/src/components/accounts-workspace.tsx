"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Search, ShieldCheck, UserPlus, UsersRound } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useApi } from "@/components/use-api";
import { useSession } from "@/components/session-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api-client";
import type { components } from "@/lib/api.generated";
import { formatDate } from "@/lib/utils";

type Account = components["schemas"]["UserAccount"];
type Role = Account["roles"][number];
const roleNames: Record<string, string> = { SUPER_ADMIN: "Quản trị hệ thống", CLINIC_MANAGER: "Quản lý phòng khám", DOCTOR: "Bác sĩ", RECEPTIONIST: "Lễ tân", ASSISTANT: "Phụ tá", PATIENT: "Bệnh nhân" };
const allRoles = ["SUPER_ADMIN", "CLINIC_MANAGER", "DOCTOR", "RECEPTIONIST", "ASSISTANT", "PATIENT"] as const;
const staffRoles = allRoles.filter((role) => role !== "SUPER_ADMIN");
const schema = z.object({ fullName: z.string().trim().min(2, "Nhập họ tên.").max(200), email: z.email("Email chưa hợp lệ."), password: z.string().min(12, "Mật khẩu cần ít nhất 12 ký tự.").max(128), roles: z.array(z.enum(allRoles)).min(1, "Chọn ít nhất một vai trò.") });
type Values = z.infer<typeof schema>;

export function AccountsWorkspace() {
  const api = useApi(); const cache = useQueryClient(); const session = useSession();
  const canSuperAdmin = session.roles.includes("SUPER_ADMIN");
  const [search, setSearch] = useState(""); const [createOpen, setCreateOpen] = useState(false); const [notice, setNotice] = useState(""); const [error, setError] = useState("");
  const accounts = useQuery({ queryKey: ["accounts", search], queryFn: () => api.operation("/api/v1/admin/users", "get", { query: { q: search } }) });
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { fullName: "", email: "", password: "", roles: ["RECEPTIONIST"] } });
  const refresh = () => cache.invalidateQueries({ queryKey: ["accounts"] });
  const create = useMutation({ mutationFn: (values: Values) => api.operation("/api/v1/admin/users", "post", { body: values }), onSuccess: async (account) => { setCreateOpen(false); form.reset({ fullName: "", email: "", password: "", roles: ["RECEPTIONIST"] }); setNotice(`Đã tạo tài khoản ${account.email}.`); setError(""); await refresh(); }, onError: showError });
  const updateRoles = useMutation({ mutationFn: ({ account, roles }: { account: Account; roles: string[] }) => api.operation("/api/v1/admin/users/{id}/roles", "put", { path: { id: account.id }, body: { roles } }), onSuccess: async (account) => { setNotice(`Đã cập nhật vai trò ${account.email}.`); setError(""); await refresh(); }, onError: showError });
  const updateStatus = useMutation({ mutationFn: ({ account, status }: { account: Account; status: Account["status"] }) => api.operation("/api/v1/admin/users/{id}/status", "put", { path: { id: account.id }, body: { status } }), onSuccess: async (account) => { setNotice(`Tài khoản ${account.email}: ${statusNames[account.status] ?? account.status}.`); setError(""); await refresh(); }, onError: showError });
  function showError(cause: unknown) { setNotice(""); setError(cause instanceof ApiError ? `${cause.code}: ${cause.message}` : cause instanceof Error ? cause.message : "Không thể hoàn thành thao tác."); }
  const options = canSuperAdmin ? allRoles : staffRoles;
  return <div className="mx-auto max-w-[1300px]">
    <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Quản trị danh tính</p><h1 className="mt-2 text-3xl font-semibold tracking-[-.04em]">Tài khoản và vai trò</h1><p className="mt-2 text-sm text-[var(--muted)]">Tạo tài khoản nhân viên, gán quyền và khóa phiên đăng nhập khi cần.</p></div><Button onClick={() => { setCreateOpen(true); setError(""); }}><UserPlus size={16}/>Tạo tài khoản</Button></div>
    {notice ? <div role="status" className="mt-5 rounded-xl border border-[#bfe2d7] bg-[#effaf6] px-4 py-3 text-sm text-[#236d5f]">{notice}</div> : null}
    {error ? <div role="alert" className="mt-5 rounded-xl border border-[#f1c9c1] bg-[#fff3f0] px-4 py-3 text-sm text-[#a64f40]">{error}</div> : null}
    <section className="soft-card mt-6 overflow-hidden"><div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--line)] px-5 py-4"><div><p className="text-sm font-bold">Tài khoản hệ thống</p><p className="mt-1 text-xs text-[var(--muted)]">Tối đa 200 kết quả</p></div><label className="relative w-full sm:w-[300px]"><Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#98a6aa]" size={16}/><Input className="pl-9" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm tên hoặc email" aria-label="Tìm tài khoản"/></label></div>
      {accounts.isLoading ? <div className="p-8 text-center text-sm text-[var(--muted)]">Đang tải tài khoản…</div> : null}
      {accounts.isError ? <div role="alert" className="p-8 text-center text-sm text-[#a64f40]">Không thể tải tài khoản.<div className="mt-3"><Button size="sm" variant="secondary" onClick={() => void accounts.refetch()}>Thử lại</Button></div></div> : null}
      {accounts.data && accounts.data.length === 0 ? <div className="px-6 py-14 text-center"><div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-[#eef6f4] text-[var(--teal)]"><UsersRound size={20}/></div><p className="mt-4 text-sm font-semibold">Không có tài khoản phù hợp</p></div> : null}
      <div className="divide-y divide-[var(--line)]">{accounts.data?.map((account) => <AccountRow key={account.id} account={account} roleOptions={options} canSuperAdmin={canSuperAdmin} busy={updateRoles.isPending || updateStatus.isPending} onRoles={(roles) => updateRoles.mutate({ account, roles })} onStatus={(status) => updateStatus.mutate({ account, status })}/>)}</div>
    </section>
    <div className="mt-4 flex gap-2 text-xs leading-5 text-[var(--muted)]"><ShieldCheck size={16} className="mt-0.5 shrink-0 text-[var(--teal)]"/><p>Vai trò SUPER_ADMIN chỉ được cấp bởi SUPER_ADMIN. Không thể khóa hoặc gỡ vai trò của SUPER_ADMIN hoạt động cuối cùng. Khi khóa tài khoản, refresh token được thu hồi; access token hiện tại hết hạn tối đa sau 10 phút.</p></div>
    {createOpen ? <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#102a30]/45 p-3 sm:p-6" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setCreateOpen(false); }}><section role="dialog" aria-modal="true" aria-labelledby="account-title" className="max-h-[94vh] w-full max-w-[600px] overflow-y-auto rounded-[22px] bg-white p-5 shadow-2xl sm:p-7"><div className="flex items-start justify-between"><div><p className="eyebrow">Tài khoản mới</p><h2 id="account-title" className="mt-2 text-xl font-bold">Tạo tài khoản</h2></div><button className="focus-ring rounded-lg p-2 text-[var(--muted)] hover:bg-[#f2f6f5]" aria-label="Đóng" onClick={() => setCreateOpen(false)}>×</button></div><form className="mt-6 grid gap-4" onSubmit={form.handleSubmit((values) => create.mutate(values))} noValidate><Field label="Họ và tên" error={form.formState.errors.fullName?.message}><Input autoFocus maxLength={200} {...form.register("fullName")}/></Field><Field label="Email đăng nhập" error={form.formState.errors.email?.message}><Input type="email" maxLength={254} {...form.register("email")}/></Field><Field label="Mật khẩu tạm thời" hint="Ít nhất 12 ký tự. Chia sẻ an toàn ngoài ứng dụng; mật khẩu không được hiển thị lại." error={form.formState.errors.password?.message}><Input type="password" autoComplete="new-password" maxLength={128} {...form.register("password")}/></Field><fieldset className="grid gap-2"><legend className="mb-1 text-sm font-semibold">Vai trò ban đầu</legend>{options.map((role) => <label key={role} className="flex items-center gap-2 rounded-lg border border-[var(--line)] px-3 py-2 text-xs"><input type="checkbox" value={role} {...form.register("roles")}/><span>{roleNames[role]}</span></label>)}</fieldset>{form.formState.errors.roles?.message ? <p className="text-xs text-[#b34d3d]">{form.formState.errors.roles.message}</p> : null}<div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>Đóng</Button><Button type="submit" disabled={create.isPending}>{create.isPending ? "Đang tạo…" : "Tạo tài khoản"}</Button></div></form></section></div> : null}
  </div>;
}

const statusNames: Record<string, string> = { ACTIVE: "Hoạt động", SUSPENDED: "Tạm khóa", DISABLED: "Đã vô hiệu hóa" };
function AccountRow({ account, roleOptions, canSuperAdmin, busy, onRoles, onStatus }: { account: Account; roleOptions: readonly Role[]; canSuperAdmin: boolean; busy: boolean; onRoles: (roles: Role[]) => void; onStatus: (status: Account["status"]) => void }) {
  const [roles, setRoles] = useState(account.roles);
  const dirty = roles.length !== account.roles.length || roles.some((role) => !account.roles.includes(role));
  function toggle(role: Role) { setRoles((current) => current.includes(role) ? current.filter((item) => item !== role) : [...current, role]); }
  return <article className="grid gap-4 px-5 py-4 lg:grid-cols-[minmax(190px,.9fr)_minmax(300px,1.5fr)_auto] lg:items-center"><div className="flex items-center gap-3"><div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[#eef6f4] text-[var(--teal-dark)]"><KeyRound size={17}/></div><div className="min-w-0"><p className="truncate text-xs font-bold">{account.fullName}</p><p className="mt-1 truncate text-[11px] text-[var(--muted)]">{account.email}</p><p className="mt-1 text-[10px] text-[var(--muted)]">Tạo {formatDate(account.createdAt)}</p></div></div><fieldset className="flex flex-wrap gap-2"><legend className="mb-2 text-[10px] font-bold uppercase tracking-[.1em] text-[var(--muted)]">Vai trò</legend>{roleOptions.map((role) => <label key={role} className="flex min-h-8 items-center gap-1.5 rounded-lg border border-[var(--line)] px-2 text-[10px]"><input type="checkbox" checked={roles.includes(role)} onChange={() => toggle(role)} aria-label={roleNames[role]}/>{roleNames[role]}</label>)}</fieldset><div className="flex items-center gap-2 lg:justify-end"><Badge value={account.status}/><Button size="sm" variant="secondary" disabled={!dirty || busy || roles.length === 0} onClick={() => onRoles(roles)}>Lưu quyền</Button><select className="focus-ring min-h-8 rounded-lg border border-[var(--line)] bg-white px-2 text-[10px]" aria-label={`Trạng thái ${account.email}`} value={account.status} disabled={busy || (!canSuperAdmin && account.roles.includes("SUPER_ADMIN"))} onChange={(event) => onStatus(event.target.value as Account["status"])}>{Object.entries(statusNames).map(([value, name]) => <option key={value} value={value}>{name}</option>)}</select></div></article>;
}
