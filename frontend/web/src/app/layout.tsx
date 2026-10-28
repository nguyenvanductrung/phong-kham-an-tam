import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "Nha Khoa An Tâm | Quản lý phòng khám",
  description: "Hệ thống đặt lịch và quản lý hồ sơ khám nha khoa.",
  icons: { icon: "/favicon.svg" },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi"><body><Providers>{children}</Providers></body></html>;
}

