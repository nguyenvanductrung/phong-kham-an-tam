import { redirect } from "next/navigation";
import { PatientPortal } from "@/components/patient-portal";
import { getSession } from "@/lib/session";
export const dynamic = "force-dynamic";
export default async function PortalPage() { const session = await getSession(); if (!session) redirect("/login"); if (!session.value.roles.includes("PATIENT")) redirect("/dashboard"); return <PatientPortal/>; }
