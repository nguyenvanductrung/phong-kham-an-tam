import { redirect } from "next/navigation";
import { ClinicalWorkspace } from "@/components/clinical-workspace";
import { getSession } from "@/lib/session";

export const dynamic = "force-dynamic";
export default async function ClinicalPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (!session.value.roles.includes("DOCTOR")) redirect("/appointments");
  return <ClinicalWorkspace />;
}
