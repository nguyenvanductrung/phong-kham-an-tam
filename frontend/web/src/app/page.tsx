import type { Metadata } from "next";
import { LandingPage } from "@/components/landing-page";
import { getSession } from "@/lib/session";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "Nha Khoa An Tâm | Đặt lịch khám răng trực tuyến",
  description: "Đặt lịch khám nha khoa trực tuyến và xem lại hồ sơ bệnh án điện tử của bạn.",
};

export default async function HomePage() {
  const session = await getSession();
  const isPatient = session?.value.roles.includes("PATIENT") ?? false;
  return (
    <LandingPage
      session={session ? { href: isPatient ? "/portal" : "/dashboard", label: isPatient ? "Trang của tôi" : "Vào trang làm việc" } : null}
    />
  );
}
