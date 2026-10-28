import { LoginForm } from "@/components/login-form";
import { getSession } from "@/lib/session";
import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";
export default async function LoginPage(){const session=await getSession();if(session)redirect(session.value.roles.includes("PATIENT")?"/portal":"/dashboard");return <LoginForm/>;}

