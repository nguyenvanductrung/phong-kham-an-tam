import { expect, test, type Page } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const demoPassword = process.env.DEMO_LOGIN_PASSWORD ?? readDemoPassword();
const receptionist = "receptionist@antam.local";
const manager = "manager@antam.local";
const doctor = "doctor@antam.local";

test.setTimeout(180_000);

test("staff records a visit and the linked patient can read the signed result", async ({ page }) => {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const patientName = `E2E Patient ${suffix}`;
  const patientPhone = `09${suffix.slice(-8)}`;
  const patientEmail = `e2e-${suffix}@example.test`;
  const accountEmail = `portal-${suffix}@example.test`;
  const patientPassword = `Patient-E2E-${suffix}`;
  const diagnosis = `E2E diagnosis ${suffix}`;
  const medication = `E2E medicine ${suffix}`;

  await signIn(page, receptionist, demoPassword);
  await page.goto("/patients");
  await page.getByRole("button", { name: "Thêm bệnh nhân" }).click();
  const patientDialog = page.getByRole("dialog");
  await patientDialog.getByLabel("Họ và tên").fill(patientName);
  await patientDialog.getByLabel("Số điện thoại").fill(patientPhone);
  await patientDialog.getByLabel("Email").fill(patientEmail);
  await patientDialog.getByRole("button", { name: "Lưu hồ sơ" }).click();
  await expect(page.getByText(patientName).first()).toBeVisible();

  await page.goto("/appointments");
  await page.getByRole("button", { name: "Tạo lịch hẹn" }).click();
  const bookingDialog = page.getByRole("dialog");
  await bookingDialog.getByLabel("Tìm bệnh nhân").fill(patientName);
  await bookingDialog.getByRole("button", { name: new RegExp(patientName) }).click();
  await bookingDialog.getByLabel("Dịch vụ").selectOption({ index: 1 });
  await bookingDialog.getByLabel("Bác sĩ").selectOption({ index: 1 });

  const bookingDate = await findBookableDate(page, bookingDialog);
  const availableSlot = bookingDate.slots[0];
  const slotLabel = new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(availableSlot.startsAt));
  await bookingDialog.getByRole("button", { name: slotLabel, exact: true }).click();
  await bookingDialog.getByRole("button", { name: "Tạo lịch", exact: true }).click();

  const createdNotice = page.getByRole("status");
  await expect(createdNotice).toContainText("Đã tạo lịch AP-");
  const appointmentNumber = (await createdNotice.innerText()).match(/AP-[A-Z0-9]+/)?.[0];
  expect(appointmentNumber).toBeTruthy();
  await page.getByLabel("Ngày bắt đầu").fill(bookingDate.date);
  const appointmentRow = page.locator("article").filter({ hasText: appointmentNumber! });
  await appointmentRow.getByRole("button", { name: "Xác nhận" }).click();
  await appointmentRow.getByRole("button", { name: "Tiếp đón" }).click();

  await signOut(page);
  await signIn(page, doctor, demoPassword);
  await page.goto("/clinical");
  await page.getByLabel("Ngày khám").fill(bookingDate.date);
  await page.getByRole("button").filter({ hasText: appointmentNumber! }).click();
  await page.getByRole("button", { name: "Bắt đầu khám" }).click();
  await page.getByRole("button", { name: "Mở bệnh án" }).click();

  await page.getByLabel("Lý do đến khám").fill("Kiểm tra răng định kỳ.");
  await page.getByLabel("Khám lâm sàng").fill("Ghi nhận từ kiểm thử đầu cuối.");
  await page.getByLabel("Chẩn đoán").fill(diagnosis);
  await page.getByLabel("Hướng điều trị").fill("Theo dõi và tái khám.");
  await page.getByLabel("Số răng").fill("36");
  await page.getByRole("button", { name: "Thêm tình trạng răng" }).click();
  await page.getByRole("button", { name: "Chốt bệnh án" }).click();
  await expect(page.getByText("Bệnh án đã chốt, chỉ đọc")).toBeVisible();

  await page.getByLabel("Tệp PDF hoặc ảnh").setInputFiles({
    name: `e2e-${suffix}.pdf`,
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\nE2E attachment\n%%EOF"),
  });
  await page.getByRole("button", { name: "Tải tệp lên" }).click();
  await expect(page.getByText(`e2e-${suffix}.pdf`)).toBeVisible();

  await page.getByRole("button", { name: "Mở hoặc tạo kế hoạch" }).click();
  await page.getByLabel("Dịch vụ dự kiến").selectOption({ index: 1 });
  await page.getByRole("button", { name: "Thêm hạng mục" }).click();
  await expect(page.getByText("Kế hoạch điều trị · phiên bản 1")).toBeVisible();
  await page.getByRole("button", { name: "Duyệt và khóa kế hoạch" }).click();
  await expect(page.getByText("APPROVED")).toBeVisible();

  await page.getByLabel("Tên thuốc").fill(medication);
  await page.getByLabel("Liều dùng").fill("500 mg");
  await page.getByLabel("Đường dùng").fill("Uống");
  await page.getByLabel("Tần suất").fill("Ngày 2 lần");
  await page.getByLabel("Số ngày").fill("5");
  await page.getByLabel("Số lượng").fill("10 viên");
  await page.getByRole("button", { name: "Phát hành đơn thuốc" }).click();
  await expect(page.getByText(medication)).toBeVisible();

  await signOut(page);
  await signIn(page, manager, demoPassword);
  await page.goto("/accounts");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  const accountDialog = page.getByRole("dialog");
  await accountDialog.getByLabel("Họ và tên").fill(patientName);
  await accountDialog.getByLabel("Email đăng nhập").fill(accountEmail);
  await accountDialog.getByLabel("Mật khẩu tạm thời").fill(patientPassword);
  await accountDialog.getByLabel("Bệnh nhân").check();
  await accountDialog.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page.getByText(accountEmail).first()).toBeVisible();

  await page.goto("/patients");
  await page.getByLabel("Tìm bệnh nhân").fill(patientName);
  const patientRow = page.getByRole("row").filter({ hasText: patientName });
  await patientRow.click();
  const candidateInput = page.getByLabel("Tìm tài khoản bệnh nhân");
  await candidateInput.fill(accountEmail);
  await page.getByRole("button", { name: new RegExp(accountEmail) }).click();
  await page.getByRole("button", { name: "Liên kết tài khoản" }).click();
  await expect(page.getByRole("status")).toContainText("Đã liên kết");

  await signOut(page);
  await signIn(page, accountEmail, patientPassword);
  await page.goto("/records");
  await expect(page.getByText(diagnosis)).toBeVisible();
  await expect(page.getByText(medication)).toBeVisible();
  await expect(page.getByText("36")).toBeVisible();
  await expect(page.getByText(`e2e-${suffix}.pdf`)).toBeVisible();
  await expect(page.getByText("Kế hoạch điều trị đã được duyệt")).toBeVisible();
});

async function signIn(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Mật khẩu").fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(/\/(dashboard|portal)$/);
}

async function signOut(page: Page) {
  await page.getByRole("button", { name: "Đăng xuất" }).click();
  await expect(page).toHaveURL(/\/login$/);
}

async function findBookableDate(page: Page, dialog: ReturnType<Page["getByRole"]>) {
  const dateInput = dialog.getByLabel("Ngày khám");
  for (let offset = 1; offset <= 7; offset += 1) {
    const date = clinicDate(offset);
    const weekday = new Intl.DateTimeFormat("en-US", {
      timeZone: "Asia/Ho_Chi_Minh",
      weekday: "short",
    }).format(new Date(`${date}T12:00:00+07:00`));
    if (weekday === "Sun") continue;

    const responsePromise = page.waitForResponse(
      (response) => response.url().includes("availability?") && response.request().method() === "GET",
      { timeout: 20_000 },
    );
    await dateInput.fill(date);
    const response = await responsePromise;
    if (!response.ok()) continue;
    const slots = (await response.json()) as { startsAt: string; availableChairs: string[] }[];
    if (slots.length > 0) return { date, slots };
  }
  throw new Error("Không tìm thấy ngày còn giờ hẹn trong 7 ngày tới.");
}

function clinicDate(offsetDays: number) {
  const date = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

function readDemoPassword() {
  let directory = process.cwd();
  for (let depth = 0; depth <= 4; depth += 1) {
    const envPath = resolve(directory, ".env");
    if (existsSync(envPath)) {
      const value = readFileSync(envPath, "utf8").match(/^DEMO_LOGIN_PASSWORD=(.*)$/m)?.[1]?.trim();
      if (value) return value;
    }
    directory = resolve(directory, "..");
  }
  throw new Error("Set DEMO_LOGIN_PASSWORD or run scripts/bootstrap.ps1 before the E2E flow.");
}
