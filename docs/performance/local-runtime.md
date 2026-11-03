# Chạy stack với ngân sách RAM thử nghiệm

Tệp `infrastructure/compose/docker-compose.performance.yml` là override tự chọn cho máy local 16 GB. Nó đặt Xms/Xmx và giới hạn bộ nhớ cho sáu JVM, đặt pool database tối đa 5 kết nối với tối thiểu 1 kết nối rỗi, và giới hạn Node/Web. Không dùng override này làm giới hạn production nếu chưa đo theo môi trường production.

Trong PowerShell ở thư mục gốc dự án:

```powershell
$Compose = @(
  "--context", "default", "compose",
  "--env-file", ".env",
  "-f", "infrastructure/compose/docker-compose.yml",
  "-f", "infrastructure/compose/docker-compose.performance.yml"
)

docker @Compose config --quiet
docker @Compose up --wait --wait-timeout 300 -d
docker @Compose ps
```

`docker compose up` tái tạo những container cần đổi môi trường/giới hạn bộ nhớ. Named volumes giữ database và object storage. Khi thay đổi mã Java hoặc cấu hình đóng gói trong image, build riêng từng service để tránh nhiều tiến trình Maven cùng lúc; đợi healthcheck hoàn tất trước khi mở UI.

Kiểm tra RAM khi idle trong 10 phút:

```powershell
./scripts/performance/measure-resources.ps1 -DurationSeconds 600 -IntervalSeconds 5
```

Đo GET đã đăng nhập qua web/BFF. Script đăng nhập bằng tài khoản demo local, chạy benchmark rồi đăng xuất; mật khẩu lấy từ `.env` hoặc biến môi trường, cookie không được ghi ra file/kết quả:

```powershell
$env:BENCHMARK_URL = "http://localhost:3000/api/bff/api/v1/patients?page=0&size=20"
$env:BENCHMARK_CONCURRENCY = "5"
$env:BENCHMARK_DURATION_SECONDS = "60"
$env:BENCHMARK_OUTPUT = "docs/performance/results/patients-5-users-run1.json"
node ./scripts/performance/benchmark-local.mjs
Remove-Item Env:BENCHMARK_URL, Env:BENCHMARK_CONCURRENCY, Env:BENCHMARK_DURATION_SECONDS, Env:BENCHMARK_OUTPUT
```

Chỉ benchmark endpoint đọc với dữ liệu demo/tổng hợp. Endpoint ghi cần payload, khóa idempotency và dữ liệu riêng cho từng người chạy; không đưa thông tin bệnh nhân thật hoặc token vào URL, câu lệnh lưu lại hay báo cáo. Đổi tên file output khi chạy lại vì script từ chối ghi đè.

Kiểm tra nhanh đăng nhập/session và một GET qua BFF mà không in thông tin tài khoản hoặc nội dung hồ sơ:

```powershell
node ./scripts/performance/smoke-local.mjs
```

Đối chiếu idle, throughput, latency, lỗi và restart. Benchmark này đo qua web/BFF tới API Gateway nhưng không đo thao tác bàn phím/render. Dùng Playwright/browser trace riêng để đo tìm kiếm và chuyển trang. Lặp mỗi cấu hình ít nhất ba lần trong điều kiện ứng dụng nền và dữ liệu tương đương.

Quay lại cấu hình mặc định bằng Compose base:

```powershell
docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --wait --wait-timeout 300 -d
```

Chỉ đổi tệp `.wslconfig` sau phép đo riêng và trong thời gian có thể restart Docker/WSL; override Compose không đụng vào file cấu hình Windows đó.
