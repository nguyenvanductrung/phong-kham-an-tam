# Chạy và vận hành môi trường local

## Khởi động lần đầu

Yêu cầu Docker Desktop ở chế độ Linux containers, Java 21, Node.js 24 và Corepack.

```powershell
./scripts/bootstrap.ps1
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --build --wait --wait-timeout 300 -d
```

Bootstrap sinh secret và mật khẩu demo vào `.env`; giữ tệp này riêng tư. Compose seed dữ liệu tổng hợp trong profile `dev`. Các lần sau dùng lệnh `up --wait ... -d` không cần chạy bootstrap lại.

| Thành phần | Địa chỉ local |
|---|---|
| Giao diện Next.js | http://localhost:3000 |
| API gateway | http://localhost:8081 |
| RabbitMQ management | http://localhost:15673 |
| RabbitMQ AMQP | `localhost:5673` |
| Redis | `localhost:6380` |
| Mailpit | http://localhost:8025 |
| MinIO console | http://localhost:9001 |
| MinIO S3 API | http://localhost:9000 |

RabbitMQ, Redis và gateway dùng cổng host có thể đổi bằng `RABBITMQ_HOST_PORT`, `RABBITMQ_MANAGEMENT_HOST_PORT`, `REDIS_HOST_PORT`, `GATEWAY_HOST_PORT`. Cổng service bên trong Compose giữ nguyên.

Tài khoản demo: `receptionist@antam.local`, `doctor@antam.local`, `patient@antam.local`, `manager@antam.local`. Mật khẩu dùng chung được bootstrap in một lần và lưu trong `.env`.

Quản lý phòng khám có thể vào **Danh mục** trong thanh điều hướng để quản lý dịch vụ, ghế, ca làm, nghỉ phép và lịch đóng cửa. API kiểm tra các lịch hẹn sắp tới trong cùng transaction trước khi áp dụng thay đổi có thể chặn lịch.

## Kiểm tra và log

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml ps
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml logs -f api-gateway clinical-service scheduling-service
./mvnw.cmd -B verify
corepack pnpm --dir frontend/web api:generate
corepack pnpm --dir frontend/web lint
corepack pnpm --dir frontend/web typecheck
corepack pnpm --dir frontend/web build
corepack pnpm --dir frontend/web exec playwright install chromium
corepack pnpm --dir frontend/web exec playwright test
```

Playwright dùng password từ `.env`, không ghi password vào test report. Nếu máy đã cài Chrome hoặc Edge có thể đặt `PLAYWRIGHT_CHROMIUM_CHANNEL=chrome` hoặc `msedge`.

## Dừng, sao lưu và khôi phục

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml down
./scripts/backup.ps1
./scripts/restore.ps1
```

`down` giữ named volumes. Chỉ dùng `down --volumes` khi muốn xóa toàn bộ dữ liệu demo. Restore ghi đè dữ liệu đích; kiểm tra đúng bản backup và môi trường trước khi chạy. Backup gồm các database theo service và object storage MinIO.

## Giới hạn môi trường

Đây là môi trường demo một cơ sở, không dùng dữ liệu bệnh nhân thật. Trước production cần secret manager/KMS, TLS, chính sách retention, backup ngoài máy và quy trình khôi phục được diễn tập.
