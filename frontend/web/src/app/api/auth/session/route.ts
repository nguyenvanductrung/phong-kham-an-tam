import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function GET() {
  const session = await getSession();
  if (!session) return NextResponse.json({ authenticated: false }, { headers: { "cache-control": "no-store" } });
  return NextResponse.json({ authenticated: true, userId: session.value.userId, fullName: session.value.fullName,
    roles: session.value.roles, csrfToken: session.value.csrfToken }, { headers: { "cache-control": "no-store" } });
}

