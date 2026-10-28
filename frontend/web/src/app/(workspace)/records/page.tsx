import { redirect } from "next/navigation";
import { PatientRecords } from "@/components/patient-records";
import { getSession } from "@/lib/session";
export const dynamic = "force-dynamic";
export default async function PatientRecordsPage() { const session = await getSession(); if (!session) redirect("/login"); if (!session.value.roles.includes("PATIENT")) redirect("/dashboard"); return <PatientRecords/>; }
