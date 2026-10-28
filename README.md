# Nha Khoa An Tâm

Base project quản lý lịch khám và bệnh án điện tử cho một phòng khám nha khoa. Giao diện tiếng Việt; API và mã nguồn tiếng Anh. Thời gian lưu UTC, lịch làm việc theo `Asia/Ho_Chi_Minh`, tiền VND.

## Chạy bản demo local

Yêu cầu Docker Desktop chạy Linux containers. Lần đầu trên Windows chạy:

```powershell
Copy-Item .env.example .env
./scripts/bootstrap.ps1
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --build --wait --wait-timeout 300 -d
```

Chờ các healthcheck xanh, sau đó mở `http://localhost:3000`. Gateway: `http://localhost:8081`; RabbitMQ: `http://localhost:15673`; Mailpit: `http://localhost:8025`; MinIO Console: `http://localhost:9001`. RabbitMQ dùng AMQP cổng `5673`, Redis cổng `6380`. Các cổng host có thể đổi qua biến `RABBITMQ_HOST_PORT`, `RABBITMQ_MANAGEMENT_HOST_PORT`, `REDIS_HOST_PORT` và `GATEWAY_HOST_PORT`. Hợp đồng API được duy trì tại [`contracts/openapi/clinic-api.yaml`](contracts/openapi/clinic-api.yaml).

Tài khoản demo tạo bởi seed dev: lễ tân `receptionist@antam.local`, bác sĩ `doctor@antam.local`, bệnh nhân `patient@antam.local`, quản lý `manager@antam.local`. Mật khẩu được sinh vào `.env` khi chạy bootstrap và in một lần trong terminal. Không dùng tài khoản demo trong môi trường thật.

## Luồng demo

1. Đăng nhập vai lễ tân, tạo hồ sơ bệnh nhân và đặt lịch theo thời lượng dịch vụ trên các mốc 15 phút.
2. Xác nhận và tiếp đón; đổi lịch hoặc thử cùng lúc đặt cùng ghế/bác sĩ để thấy mã lỗi `APPOINTMENT_CONFLICT`.
3. Quản lý phòng khám dùng trang **Danh mục** để chỉnh dịch vụ/giá, ghế, ca bác sĩ, ghi nhận nghỉ và lịch đóng cửa; lịch hẹn đang có sẽ được bảo vệ khi thay đổi cấu hình.
4. Đăng nhập bác sĩ, mở lượt khám, lưu bản nháp, thêm thủ thuật, đính kèm ảnh/PDF riêng tư và chốt bệnh án.
5. Lập kế hoạch điều trị theo snapshot giá dịch vụ, duyệt kế hoạch sau khi chốt; phát hành đơn thuốc, xem lịch sử và email trong Mailpit.

Các trang chỉ hiển thị dữ liệu nhận từ API thật. Khi dịch vụ chưa sẵn sàng, UI hiển thị trạng thái lỗi và nút thử lại.

## Build và kiểm chứng

```powershell
./mvnw.cmd -B verify
corepack pnpm --dir frontend/web api:generate
corepack pnpm --dir frontend/web lint
corepack pnpm --dir frontend/web typecheck
corepack pnpm --dir frontend/web build
corepack pnpm --dir frontend/web exec playwright install chromium
corepack pnpm --dir frontend/web exec playwright test
```

`api:generate` sinh `frontend/web/src/lib/api.generated.ts` từ OpenAPI. Frontend dùng kiểu contract cho các luồng bệnh nhân, đặt lịch và danh mục; CI kiểm tra file sinh ra khớp với contract đã commit.

Integration tests cần Docker/Testcontainers. Không báo pass cho lệnh chưa chạy thành công. Seed chỉ chạy với profile `dev` và dùng dữ liệu tổng hợp.

## Dữ liệu và vận hành

Mỗi service sở hữu DB, user DB và Flyway riêng. Không có FK/JOIN giữa database. Dùng `scripts/backup.ps1` và `scripts/restore.ps1` để sao lưu/khôi phục các database và object storage. Cấu hình chạy qua biến môi trường; `.env` không được commit.

Xem [architecture overview](docs/architecture/overview.md), [appointment state machine](docs/architecture/appointment-state-machine.md), [database ownership](docs/database/ownership.md), [runbook](docs/runbooks/local-development.md) và [known limits](docs/roadmap/limits.md).

