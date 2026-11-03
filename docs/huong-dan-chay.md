# Hướng dẫn chạy ứng dụng local

## Yêu cầu

- Windows với Docker Desktop đang chạy ở chế độ Linux containers.
- Docker Compose v2 (`docker compose`).
- Đảm bảo máy còn đủ bộ nhớ và không chạy đồng thời quá nhiều stack Docker lớn. Lần build đầu tiên của các backend có thể tải nhiều thư viện Maven.

## Khởi động lần đầu

Mở PowerShell tại thư mục gốc dự án:

```powershell
.\scripts\bootstrap.ps1
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --build --wait --wait-timeout 300 -d
```

Bootstrap tạo hoặc hoàn thiện các secret và mật khẩu demo trong `.env`. Không gửi file này cho người khác và không commit lên Git. Compose sẽ build image khi cần, khởi động cơ sở dữ liệu, dịch vụ nền, backend và giao diện web; tùy tốc độ mạng/máy, lần build đầu có thể lâu hơn các lần sau.

Khi lệnh hoàn tất với trạng thái healthy, mở:

| Thành phần | Địa chỉ |
|---|---|
| Giao diện phòng khám | <http://localhost:3000> |
| API gateway | <http://localhost:8081> |
| RabbitMQ Management | <http://localhost:15673> |
| Mailpit (email demo) | <http://localhost:8025> |
| MinIO Console | <http://localhost:9001> |

RabbitMQ AMQP dùng cổng `5673`, Redis dùng cổng `6380`, MinIO S3 API dùng cổng `9000`.

## Tài khoản demo

Seed môi trường `dev` tạo các tài khoản:

| Vai trò | Tài khoản |
|---|---|
| Lễ tân | `receptionist@antam.local` |
| Bác sĩ | `doctor@antam.local` |
| Bệnh nhân | `patient@antam.local` |
| Quản lý | `manager@antam.local` |

Các tài khoản dùng chung mật khẩu demo được sinh khi bootstrap và lưu trong `.env`. Xem riêng giá trị đó trong PowerShell:

```powershell
Select-String '^DEMO_LOGIN_PASSWORD=' .env
```

Chỉ dùng tài khoản và dữ liệu seed cho demo local.

## Chạy lại sau khi đã build

Nếu image đã được build và chỉ cần bật lại ứng dụng:

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --wait --wait-timeout 300 -d
```

Khi vừa sửa mã nguồn và muốn build lại image:

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --build --wait --wait-timeout 300 -d
```

## Kiểm tra trạng thái và xem log

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml ps
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml logs -f web api-gateway
```

Để xem log service khác, thay `web api-gateway` bằng tên service, ví dụ `scheduling-service` hoặc `clinical-service`. Nhấn `Ctrl+C` để thoát chế độ theo dõi log; việc này không dừng container.

## Dừng ứng dụng

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml down
```

Lệnh `down` dừng và xóa các container thuộc stack phòng khám, nhưng giữ named volumes để lần chạy sau còn dữ liệu demo. Không thêm `--volumes` nếu muốn giữ dữ liệu trong PostgreSQL, Redis, RabbitMQ, MinIO và Mailpit.

## Khắc phục sự cố thường gặp

- **Không kết nối được Docker Engine:** mở Docker Desktop, chờ engine khởi động xong rồi kiểm tra `docker --context default info`. Nếu Docker Desktop đang dùng context khác, chọn đúng context hoặc dùng context mà Docker Desktop hiển thị.
- **Compose báo container chưa healthy:** xem `docker compose ... ps` và log service lỗi bằng lệnh ở mục trên. Có thể chờ thêm một chút khi database chạy migration lần đầu.
- **Docker quá tải hoặc không phản hồi:** dừng các stack không dùng trong Docker Desktop rồi chờ engine ổn định. Lần đầu nên chạy lệnh build một lần; những lần mở lại dùng lệnh không có `--build`.
- **Cổng đã được sử dụng:** kiểm tra tiến trình/container đang chiếm cổng. Có thể đổi các cổng host `POSTGRES_HOST_PORT`, `RABBITMQ_HOST_PORT`, `RABBITMQ_MANAGEMENT_HOST_PORT`, `REDIS_HOST_PORT` và `GATEWAY_HOST_PORT` trong `.env`. Cổng PostgreSQL mặc định là `5432`; kết nối pgAdmin dùng giá trị `POSTGRES_HOST_PORT`.

Xem [runbook local development](runbooks/local-development.md) để biết thêm chi tiết về vận hành và backup/restore.
