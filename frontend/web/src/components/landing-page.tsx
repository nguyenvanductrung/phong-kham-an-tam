import Link from "next/link";
import { ArrowRight, CalendarDays, Check, FileText, LockKeyhole, Menu, Search, ShieldCheck, Sparkles } from "lucide-react";
import type { ReactNode, SVGProps } from "react";

/*
 * Trang chủ công khai. Gateway yêu cầu đăng nhập cho mọi API danh mục,
 * nên nội dung dịch vụ/bác sĩ dưới đây là dữ liệu tĩnh khớp với dữ liệu mẫu
 * trong backend/scheduling-service/.../seed/DemoScheduleSeed.java.
 */
const services = [
  { name: "Khám tổng quát", description: "Khám và tư vấn răng miệng", minutes: 30, price: 150000, icon: Search },
  { name: "Lấy cao răng", description: "Vệ sinh răng miệng", minutes: 45, price: 350000, icon: Sparkles },
  { name: "Trám răng", description: "Trám răng cơ bản", minutes: 60, price: 500000, icon: ToothIcon },
] as const;

const stats = [
  { value: "3", label: "Dịch vụ đang nhận lịch" },
  { value: "1", label: "Bác sĩ đang làm việc" },
  { value: "2", label: "Ghế khám" },
  { value: "8:00–18:00", label: "Thứ Hai đến thứ Bảy" },
];

const steps = [
  { title: "Đặt lịch", text: "Chọn dịch vụ, bác sĩ và giờ trống. Hệ thống chặn ngay nếu giờ đó vừa có người đặt." },
  { title: "Nhận xác nhận", text: "Email xác nhận được gửi khi lịch được đặt, lễ tân xác nhận lại trước buổi khám." },
  { title: "Đến khám", text: "Báo tên tại quầy, bác sĩ phụ trách khám và ghi bệnh án điện tử." },
  { title: "Xem kết quả", text: "Bệnh án đã ký, đơn thuốc và kế hoạch điều trị có trong cổng bệnh nhân." },
];

const slots = [
  { time: "08:00", taken: true },
  { time: "08:30" },
  { time: "09:00", selected: true },
  { time: "10:15" },
  { time: "14:00", taken: true },
  { time: "15:30" },
];

const navLinks = [
  { href: "#dich-vu", label: "Dịch vụ" },
  { href: "#bac-si", label: "Bác sĩ" },
  { href: "#quy-trinh", label: "Quy trình" },
  { href: "#lien-he", label: "Liên hệ" },
];

const vnd = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 });

const primaryLink = "focus-ring items-center justify-center gap-2 rounded-[var(--radius-control)] bg-[var(--teal)] font-semibold text-white transition hover:bg-[var(--teal-dark)]";
const outlineLink = "focus-ring items-center justify-center rounded-[var(--radius-control)] border border-[#cfd8d4] bg-[var(--paper)] font-semibold text-[var(--ink)] transition hover:bg-[var(--teal-soft)]";

export type LandingSession = { href: string; label: string } | null;

export function LandingPage({ session }: { session: LandingSession }) {
  // Đã đăng nhập: nút chính dẫn vào khu làm việc; chưa đăng nhập: dẫn tới trang đăng nhập.
  const bookHref = session?.href ?? "/login";
  const bookLabel = session ? session.label : "Đặt lịch khám";

  return (
    <div className="min-h-screen bg-[var(--canvas)] text-[15px] leading-relaxed text-[var(--ink)]">
      <header className="sticky top-0 z-20 border-b border-[var(--line)] bg-[rgba(255,254,250,.94)] backdrop-blur">
        <nav aria-label="Điều hướng chính" className="mx-auto flex max-w-[1200px] items-center gap-6 px-4 py-3.5 sm:px-6">
          <Link href="/" className="focus-ring flex items-center gap-2.5 rounded-lg whitespace-nowrap text-[17px] font-bold text-[var(--ink)]">
            <span className="inline-flex size-[38px] items-center justify-center rounded-xl bg-[var(--teal)] text-[var(--paper)]"><ToothIcon size={20} /></span>
            Nha khoa An Tâm
          </Link>
          <div className="ml-auto hidden items-center gap-7 text-sm font-medium md:flex">
            {navLinks.map((link) => <a key={link.href} href={link.href} className="focus-ring rounded hover:text-[var(--teal-dark)]">{link.label}</a>)}
          </div>
          <div className="ml-auto flex items-center gap-2.5 md:ml-0">
            {session ? null : <Link href="/login" className={`${outlineLink} hidden min-h-11 whitespace-nowrap px-[18px] text-sm sm:inline-flex`}>Đăng nhập</Link>}
            <Link href={bookHref} className={`${primaryLink} hidden min-h-11 whitespace-nowrap px-[18px] text-sm sm:inline-flex`}>{bookLabel}</Link>
            <details className="relative md:hidden">
              <summary aria-label="Mở menu" className="focus-ring flex size-11 cursor-pointer list-none items-center justify-center rounded-[var(--radius-control)] border border-[#cfd8d4] bg-[var(--paper)] [&::-webkit-details-marker]:hidden">
                <Menu size={20} aria-hidden="true" />
              </summary>
              <div className="absolute right-0 mt-2 flex w-52 flex-col rounded-2xl border border-[var(--line)] bg-[var(--paper)] p-2 shadow-[var(--shadow-raised)]">
                {navLinks.map((link) => <a key={link.href} href={link.href} className="rounded-lg px-3 py-2.5 font-medium hover:bg-[var(--teal-soft)]">{link.label}</a>)}
                {session ? null : <Link href="/login" className="rounded-lg px-3 py-2.5 font-medium hover:bg-[var(--teal-soft)] sm:hidden">Đăng nhập</Link>}
                <Link href={bookHref} className="mt-1 rounded-lg sm:hidden bg-[var(--teal)] px-3 py-2.5 text-center font-semibold text-white">{bookLabel}</Link>
              </div>
            </details>
          </div>
        </nav>
      </header>

      <main id="top">
        <section className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-12 px-4 pb-16 pt-12 sm:px-6 sm:pt-[72px]">
          <div className="min-w-0 flex-[1_1_520px]">
            <p className="inline-flex rounded-full bg-[var(--teal-soft)] px-3 py-1.5 text-[13px] font-semibold text-[var(--teal-dark)]">Phòng khám nha khoa · Hồ sơ bệnh án điện tử</p>
            <h1 className="mt-5 text-[38px] font-bold leading-[1.12] tracking-[-.03em] text-[var(--nav)] sm:text-[52px]">Đặt lịch khám răng trong một phút, xem lại hồ sơ mọi lúc</h1>
            <p className="mt-5 max-w-[540px] text-[17px] text-[#4f6266]">Chọn dịch vụ, bác sĩ và giờ trống ngay trên web. Sau buổi khám, kết quả, đơn thuốc và kế hoạch điều trị được lưu trong cổng bệnh nhân của bạn.</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href={bookHref} className={`${primaryLink} inline-flex min-h-[52px] rounded-[14px] px-6 text-base`}>{bookLabel}<ArrowRight size={18} aria-hidden="true" /></Link>
              <a href="#dich-vu" className={`${outlineLink} inline-flex min-h-[52px] rounded-[14px] px-6 text-base`}>Xem dịch vụ và bảng giá</a>
            </div>
            <ul className="mt-8 flex flex-wrap gap-5 text-sm text-[#4f6266]">
              {["Không lo trùng lịch", "Email xác nhận khi đặt lịch", "Hồ sơ chỉ bạn và bác sĩ xem"].map((item) => (
                <li key={item} className="inline-flex items-center gap-2"><Check size={18} className="text-[var(--teal)]" aria-hidden="true" />{item}</li>
              ))}
            </ul>
          </div>

          <div className="flex min-w-0 flex-[1_1_400px] justify-center">
            <div className="w-full max-w-[440px] rounded-3xl border border-[var(--line)] bg-[var(--paper)] p-6 shadow-[var(--shadow-raised)]">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[.06em] text-[var(--muted)]">Ví dụ khung giờ trống</p>
                  <p className="mt-1 text-lg font-bold text-[var(--nav)]">Khám tổng quát · 30 phút</p>
                </div>
                <span className="inline-flex size-11 items-center justify-center rounded-[14px] bg-[var(--teal-soft)] text-[var(--teal)]"><CalendarDays size={22} aria-hidden="true" /></span>
              </div>
              <div className="mt-[18px] flex items-center gap-3 rounded-2xl bg-[var(--canvas)] p-3">
                <Avatar initials="MQ" size="sm" />
                <div className="leading-snug">
                  <p className="font-semibold">BS. Trần Minh Quân</p>
                  <p className="text-[13px] text-[var(--muted)]">Nha khoa tổng quát · Ghế 01</p>
                </div>
              </div>
              <div className="mt-[18px] grid grid-cols-3 gap-2.5" aria-label="Các khung giờ minh họa">
                {slots.map((slot) => (
                  <span key={slot.time} className={
                    slot.selected ? "rounded-xl bg-[var(--teal)] py-2.5 text-center font-semibold text-[var(--paper)]"
                      : slot.taken ? "rounded-xl border border-[var(--line)] py-2.5 text-center font-semibold text-[#9aa7aa] line-through"
                        : "rounded-xl border border-[var(--line)] py-2.5 text-center font-semibold"
                  }>{slot.time}</span>
                ))}
              </div>
              <Link href={bookHref} className="focus-ring mt-[18px] flex min-h-12 items-center justify-center rounded-[14px] bg-[var(--nav)] font-semibold text-[var(--paper)] transition hover:bg-[var(--teal-dark)]">Chọn giờ khám</Link>
              <p className="mt-3 text-center text-xs text-[var(--muted)]">Giờ gạch ngang là đã có người đặt</p>
            </div>
          </div>
        </section>

        <section aria-label="Phòng khám trong số liệu" className="mx-auto max-w-[1200px] px-4 pb-[72px] sm:px-6">
          <div className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
            {stats.map((item) => (
              <div key={item.label} className="rounded-[20px] border border-[var(--line)] bg-[var(--paper)] p-6">
                <p className="text-[40px] font-bold leading-tight tracking-[-.03em] text-[var(--teal)]">{item.value}</p>
                <p className="mt-1 font-semibold">{item.label}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="dich-vu" className="scroll-mt-20 border-y border-[var(--line)] bg-[var(--paper)]">
          <div className="mx-auto max-w-[1200px] px-4 py-[72px] sm:px-6">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <SectionTitle eyebrow="Dịch vụ">Giá niêm yết, thời lượng rõ ràng</SectionTitle>
              <p className="max-w-[420px] text-[#4f6266]">Giá dự kiến tại thời điểm đặt được lưu cùng lịch hẹn, không đổi nếu bảng giá cập nhật sau đó.</p>
            </div>
            <div className="mt-9 grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-5">
              {services.map((service) => {
                const Icon = service.icon;
                return (
                  <article key={service.name} className="flex flex-col gap-3 rounded-[20px] border border-[var(--line)] bg-[var(--canvas)] p-7">
                    <IconTile><Icon size={24} aria-hidden="true" /></IconTile>
                    <h3 className="mt-1 text-xl font-bold text-[var(--nav)]">{service.name}</h3>
                    <p className="text-[#4f6266]">{service.description}</p>
                    <div className="mt-auto flex items-center justify-between gap-3 border-t border-[var(--line)] pt-3">
                      <span className="text-sm text-[var(--muted)]">{service.minutes} phút</span>
                      <span className="text-xl font-bold text-[var(--nav)]">{vnd.format(service.price)}</span>
                    </div>
                    <Link href={bookHref} className="focus-ring flex min-h-11 items-center justify-center rounded-[var(--radius-control)] border border-[var(--teal)] font-semibold text-[var(--teal-dark)] transition hover:bg-[var(--teal-soft)]">Đặt dịch vụ này</Link>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        <section id="bac-si" className="mx-auto max-w-[1200px] scroll-mt-20 px-4 py-[72px] sm:px-6">
          <SectionTitle eyebrow="Đội ngũ bác sĩ">Chọn đúng bác sĩ bạn muốn gặp</SectionTitle>
          <div className="mt-8 grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-5">
            <article className="flex items-center gap-4 rounded-[20px] border border-[var(--line)] bg-[var(--paper)] p-6">
              <Avatar initials="MQ" size="lg" />
              <div className="leading-snug">
                <h3 className="text-lg font-bold text-[var(--nav)]">BS. Trần Minh Quân</h3>
                <p className="mt-0.5 text-[#4f6266]">Nha khoa tổng quát</p>
                <p className="mt-1.5 text-[13px] text-[var(--muted)]">Thứ Hai – thứ Bảy · 8:00–18:00</p>
              </div>
            </article>
            <article className="flex items-center gap-4 rounded-[20px] border border-dashed border-[#cfd8d4] p-6">
              <span className="inline-flex size-16 flex-none items-center justify-center rounded-full bg-[var(--teal-soft)] text-xl font-bold text-[var(--teal)]" aria-hidden="true">+</span>
              <div className="leading-snug">
                <h3 className="text-lg font-bold text-[var(--nav)]">Xem lịch của từng bác sĩ</h3>
                <p className="mt-0.5 text-[#4f6266]">Sau khi đăng nhập, bạn chọn bác sĩ và xem giờ trống theo ca làm việc thực tế.</p>
              </div>
            </article>
          </div>
        </section>

        <section id="quy-trinh" className="scroll-mt-20 border-y border-[var(--line)] bg-[var(--paper)]">
          <div className="mx-auto max-w-[1200px] px-4 py-[72px] sm:px-6">
            <SectionTitle eyebrow="Quy trình">Bốn bước từ đặt lịch đến xem kết quả</SectionTitle>
            <ol className="mt-9 grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-5">
              {steps.map((step, index) => (
                <li key={step.title} className="rounded-[20px] border border-[var(--line)] bg-[var(--canvas)] p-6">
                  <span className="inline-flex size-10 items-center justify-center rounded-xl bg-[var(--teal)] font-bold text-[var(--paper)]">{index + 1}</span>
                  <h3 className="mt-4 text-lg font-bold text-[var(--nav)]">{step.title}</h3>
                  <p className="mt-1.5 text-[#4f6266]">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section aria-labelledby="bao-mat" className="mx-auto max-w-[1200px] px-4 py-[72px] sm:px-6">
          <div className="flex flex-wrap items-center gap-10 rounded-[28px] bg-[var(--nav)] p-8 text-[#e6f1eb] sm:p-12">
            <div className="min-w-0 flex-[1_1_420px]">
              <p className="text-[13px] font-semibold uppercase tracking-[.06em] text-[#9fd3c5]">Bảo mật hồ sơ</p>
              <h2 id="bao-mat" className="mt-2 text-[28px] font-bold leading-tight tracking-[-.02em] text-[var(--paper)] sm:text-[32px]">Hồ sơ sức khỏe của bạn được bảo vệ như dữ liệu nhạy cảm</h2>
              <p className="mt-4 text-[#c6dcd6]">Hồ sơ bệnh án điện tử theo hướng dẫn của Thông tư 13/2025/TT-BYT. Dữ liệu sức khỏe được xử lý như dữ liệu cá nhân nhạy cảm theo Luật Bảo vệ dữ liệu cá nhân 2025.</p>
            </div>
            <ul className="flex min-w-0 flex-[1_1_360px] flex-col gap-3.5">
              <SecurityItem icon={<ShieldCheck size={22} aria-hidden="true" />} title="Chỉ người được phân công mới xem.">Bác sĩ chỉ mở được ca mình phụ trách, lễ tân không xem nội dung bệnh án.</SecurityItem>
              <SecurityItem icon={<LockKeyhole size={22} aria-hidden="true" />} title="Bệnh án đã ký không thể sửa.">Mọi điều chỉnh là một bản bổ sung có ghi lý do.</SecurityItem>
              <SecurityItem icon={<FileText size={22} aria-hidden="true" />} title="Mọi thay đổi đều được ghi lại.">Ai thao tác, lúc nào, trên hồ sơ nào.</SecurityItem>
            </ul>
          </div>
        </section>
      </main>

      <footer id="lien-he" className="scroll-mt-20 bg-[var(--nav)] text-[#c6dcd6]">
        <div className="mx-auto grid max-w-[1200px] grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-8 px-4 pb-8 pt-14 sm:px-6">
          <div>
            <p className="text-lg font-bold text-[var(--paper)]">Nha khoa An Tâm</p>
            <p className="mt-2.5 text-sm">Đặt lịch khám và quản lý hồ sơ bệnh án điện tử.</p>
          </div>
          <div>
            <p className="font-semibold text-[var(--paper)]">Liên hệ</p>
            <p className="mt-2.5 text-sm">Hồ Chí Minh</p>
            <p className="mt-1 text-sm">Email demo: Mailpit (môi trường phát triển)</p>
          </div>
          <div>
            <p className="font-semibold text-[var(--paper)]">Giờ làm việc</p>
            <p className="mt-2.5 text-sm">Thứ Hai – thứ Bảy: 8:00–18:00</p>
            <p className="mt-1 text-sm">Chủ nhật: nghỉ</p>
          </div>
          <div className="flex flex-col items-start gap-2.5">
            <Link href={bookHref} className={`${primaryLink} inline-flex min-h-11 px-5`}>{bookLabel}</Link>
            {session ? null : <Link href="/login" className="focus-ring rounded font-semibold text-[#9fd3c5] hover:text-[var(--paper)]">Đăng nhập cổng bệnh nhân</Link>}
          </div>
        </div>
        <div className="mx-auto max-w-[1200px] border-t border-white/10 px-4 pb-7 pt-4 text-[13px] text-[#9fb4ae] sm:px-6">Bài tập lớn — dữ liệu demo, không dùng cho bệnh nhân thật.</div>
      </footer>
    </div>
  );
}

function SectionTitle({ eyebrow, children }: { eyebrow: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-[13px] font-semibold uppercase tracking-[.06em] text-[var(--teal)]">{eyebrow}</p>
      <h2 className="mt-2 text-[28px] font-bold leading-tight tracking-[-.02em] text-[var(--nav)] sm:text-[34px]">{children}</h2>
    </div>
  );
}

function IconTile({ children }: { children: ReactNode }) {
  return <span className="inline-flex size-12 items-center justify-center rounded-[14px] bg-[var(--teal-soft)] text-[var(--teal)]">{children}</span>;
}

function Avatar({ initials, size }: { initials: string; size: "sm" | "lg" }) {
  const box = size === "lg" ? "size-16 text-xl" : "size-10 text-sm";
  return <span className={`inline-flex ${box} flex-none items-center justify-center rounded-full bg-[var(--nav)] font-bold text-[var(--paper)]`} aria-hidden="true">{initials}</span>;
}

function SecurityItem({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <li className="flex items-start gap-3">
      <span className="mt-0.5 flex-none text-[#9fd3c5]">{icon}</span>
      <span><strong className="text-[var(--paper)]">{title}</strong> {children}</span>
    </li>
  );
}

function ToothIcon({ size = 24, ...props }: { size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M7 3c-2 0-3.5 1.6-3.5 4 0 2 .8 3.4 1.4 5.2.6 1.9.8 4 1.4 6.3.3 1.3 1.6 1.4 2 .1l1.2-4c.3-1 1.7-1 2 0l1.2 4c.4 1.3 1.7 1.2 2-.1.6-2.3.8-4.4 1.4-6.3.6-1.8 1.4-3.2 1.4-5.2 0-2.4-1.5-4-3.5-4-1.6 0-2.6.9-3.5.9S8.6 3 7 3z" />
    </svg>
  );
}
