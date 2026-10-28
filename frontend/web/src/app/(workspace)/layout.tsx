import { redirect } from "next/navigation";
import { AppShell } from "@/components/app-shell";
import { getSession } from "@/lib/session";

export const dynamic = "force-dynamic";
export default async function WorkspaceLayout({children}:{children:React.ReactNode}){
  const session=await getSession();if(!session)redirect("/login");
  return <AppShell session={{userId:session.value.userId,fullName:session.value.fullName,roles:session.value.roles,csrfToken:session.value.csrfToken}}>{children}</AppShell>;
}

