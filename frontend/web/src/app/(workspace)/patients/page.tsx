import { redirect } from "next/navigation";
import { PatientsWorkspace } from "@/components/patients-workspace";
import { getSession } from "@/lib/session";
export const dynamic="force-dynamic";
export default async function PatientsPage(){
  const session=await getSession();
  if(!session)redirect("/login");
  if(session.value.roles.includes("PATIENT"))redirect("/portal");
  if(!session.value.roles.some(role=>["SUPER_ADMIN","CLINIC_MANAGER","RECEPTIONIST"].includes(role)))redirect("/dashboard");
  return <PatientsWorkspace/>;
}

