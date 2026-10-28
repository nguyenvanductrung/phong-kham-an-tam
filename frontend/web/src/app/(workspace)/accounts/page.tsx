import { redirect } from "next/navigation";
import { AccountsWorkspace } from "@/components/accounts-workspace";
import { getSession } from "@/lib/session";
export const dynamic = "force-dynamic";
export default async function AccountsPage() { const session = await getSession(); if (!session) redirect("/login"); if (!session.value.roles.some((role) => ["SUPER_ADMIN", "CLINIC_MANAGER"].includes(role))) redirect("/dashboard"); return <AccountsWorkspace/>; }
