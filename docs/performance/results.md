# Kết quả triển khai tối ưu hiệu năng

Ngày ghi nhận: 04/11/2026. Kiến trúc microservices được giữ nguyên.

## Thay đổi đã triển khai

- Thêm Compose override bật riêng cho máy local: giới hạn heap/container cho sáu JVM, heap Node cho web và Hikari pool tối đa 5, tối thiểu 1 kết nối idle ở năm service JDBC.
- Thêm connect/read timeout 2/3 giây cho RestClient nội bộ; BFF truyền tín hiệu hủy xuống gateway và đặt deadline 8 giây cho GET. Request ghi/upload vẫn theo tín hiệu client để tránh timeout tùy tiện trong lúc commit hoặc truyền tệp.
- Giảm tải frontend bằng điều hướng Link phía client, debounce tìm kiếm 300 ms, hủy truy vấn cũ qua AbortSignal, thời gian giữ cache phù hợp và xóa cache khi logout/đổi phiên.
- Giữ nội dung form bệnh án đang sửa khi query refetch; đổi record sẽ reset draft có chủ ý và hỏi trước khi rời form có thay đổi.
- Patient summary query chỉ lấy các cột cần hiển thị thay cho `SELECT *`.
- Thêm công cụ PowerShell lấy mẫu container/Windows/WSL và benchmark GET đã đăng nhập qua BFF.

## Số đo

| Chỉ số | Ban đầu | Sau triển khai, một lượt tải |
|---|---:|---:|
| Tổng container phòng khám | Snapshot 2.637 MiB (~2,58 GiB), idle không kiểm soát | Tải: trung bình 2.112 MiB (~2,06 GiB), 8 mẫu/70 giây. Idle 9,9 phút: median 2.101 MiB (~2,05 GiB), p95 2.149 MiB (64 mốc) |
| Sáu JVM | Snapshot khoảng 2.155 MiB (~2,10 GiB) | Tải: trung bình khoảng 1.577 MiB (~1,54 GiB). Idle 9,9 phút: median 1.625 MiB (~1,59 GiB), p95 1.628 MiB |
| Web container | Snapshot 93 MiB | Tải: trung bình 123 MiB. Idle 9,9 phút: median 67 MiB, p95 68 MiB |
| GET bệnh nhân qua web/BFF | Chưa có baseline latency trước thay đổi | 3 lượt 60 giây, 5 worker liên tục; 94.677 request tổng; 0 lỗi; tất cả HTTP 200 |
| Latency GET | Chưa đo | p50 8,3–9,3 ms; p95 12,9–17,5 ms; p99 18,9–25,1 ms; max 72,1–91,2 ms |
| RAM Windows khả dụng | Snapshot ban đầu khoảng 0,8 GiB | Khi benchmark khoảng 0,47–1,26 GiB; idle 9,9 phút: median 2.502 MiB (~2,44 GiB), min 1.928 MiB |
| WSL working set | Snapshot ban đầu khoảng 5,28 GiB | Tải: khoảng 3,73–3,84 GiB. Idle 9,9 phút: median 2.984 MiB (~2,91 GiB) |

Kết quả từng lượt GET:

| Lượt | Requests | Throughput | p50 | p95 | p99 | Lỗi |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 29.086 | 484,6 req/s | 9,3 ms | 17,5 ms | 25,1 ms | 0 |
| 2 | 31.944 | 532,3 req/s | 8,8 ms | 13,7 ms | 19,6 ms | 0 |
| 3 | 33.647 | 560,6 req/s | 8,3 ms | 12,9 ms | 18,9 ms | 0 |

Ảnh trước là snapshot idle cũ; các ảnh sau không tái tạo chính xác workload nền khi lấy baseline. So với snapshot đó, median idle mới thấp hơn khoảng 20% cho tổng container và 25% cho sáu JVM, đạt mục tiêu thử nghiệm ban đầu 15% trong cửa sổ sau thay đổi này. Đây chưa phải A/B cùng điều kiện nên mức tiết kiệm vẫn là ước lượng. RAM Windows khả dụng có median khoảng 2,44 GiB, đạt mục tiêu 2 GiB trong cửa sổ đo nhưng min khoảng 1,88 GiB. Web tăng tạm lên khoảng 300 MiB sau tải rồi giảm; median idle 67 MiB không cho thấy xu hướng tăng kéo dài trong cửa sổ này.

Benchmark GET là tải kín: mỗi worker gửi request kế tiếp ngay sau response, nên không đại diện đầy đủ cho nhịp thao tác người dùng. Nó đo một endpoint danh sách bệnh nhân, không chứng minh p95 cho tìm kiếm, đặt lịch, lưu bệnh án hoặc thao tác trình duyệt.

Benchmark JSON và CSV mẫu được giữ ngoài repository tại `C:\tmp\clinic-after-load-run1.json`, `C:\tmp\clinic-after-load-run2.json`, `C:\tmp\clinic-after-load-run3.json`, `C:\tmp\clinic-after-load-resources.csv` và `C:\tmp\clinic-after-idle-10m.csv`; file chứa latency/status và tài nguyên, không chứa cookie hoặc nội dung response bệnh nhân.

## Kiểm tra đã chạy

- `tsc --noEmit`: qua.
- ESLint toàn frontend: qua.
- Next.js production build: qua.
- `docker compose config --quiet`: qua.
- Smoke test local: health, login, session và patient GET qua BFF đều HTTP 200.
- Playwright E2E hiện có: 2 passed.
- 7 application containers (web, gateway, năm service): healthy sau build/deploy; các container dependency cũng healthy.
- Script Node syntax, PowerShell parser và `git diff --check`: qua.
- Docker build backend dùng `-Dmaven.test.skip=true`; Maven test/verify chưa được chạy. Không ghi nhận backend unit/integration tests là pass.

## Phần cần đo hoặc triển khai tiếp

- Đối chiếu baseline trên cùng image/dataset/nền ứng dụng; thêm trace trình duyệt cho tìm kiếm, điều hướng và chỉnh sửa bệnh án.
- Chạy Maven verify trong phiên riêng, sau đó soak 60 phút và kiểm tra OOM/restart, GC, Hikari pending, queue age.
- Khi có dữ liệu tổng hợp đủ lớn, dùng `EXPLAIN (ANALYZE, BUFFERS)` trước khi quyết định index, filter hoặc đổi paging API; hiện không thay đổi contract.
- Chỉ chỉnh polling/outbox/SMTP khi đo được truy vấn rỗng, pool wait hoặc dependency chậm; giữ nguyên cơ chế worker trong lượt này.
- Chưa chỉnh `.wslconfig`; cần thử riêng vì sẽ tác động Docker/WSL của cả máy.

## Chạy và hoàn tác

Lệnh áp dụng, đo lại và quay về Compose mặc định nằm trong [local-runtime.md](local-runtime.md). Profile giới hạn RAM hiện được áp dụng cho stack local; không dùng các giới hạn này làm thông số production nếu chưa đo ở môi trường đích.
