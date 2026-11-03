# Baseline hiệu năng ban đầu

Ngày: 04/11/2026. Đây là ảnh chụp máy local khi stack đang chạy, chưa phải benchmark tải hay kết quả sau thay đổi.

## Môi trường đã ghi nhận

- Windows: RAM khả dụng 15,3 GiB; còn trống khoảng 0,8 GiB.
- Docker Desktop dùng WSL2; `vmmemWSL` khoảng 5.411 MiB.
- `.wslconfig`: memory 6GB, swap 4GB, processors 8; chưa cấu hình `autoMemoryReclaim`.
- Stack Compose có sáu JVM, Next.js production, PostgreSQL, Redis, RabbitMQ, MinIO và Mailpit.
- Tại thời điểm lấy số đo, CPU container thấp; chưa có kịch bản tải đang chạy.

## RAM container mẫu

| Container | MiB |
|---|---:|
| web | 93,34 |
| api-gateway | 363,9 |
| clinical-service | 377,3 |
| scheduling-service | 353,2 |
| patient-service | 357,8 |
| notification-service | 345,1 |
| identity-service | 357,9 |
| postgres | 138,3 |
| redis | 8,95 |
| rabbitmq | 159,5 |
| minio | 62,29 |
| mailpit | 19,56 |
| **Tổng các container phòng khám** | **2.637,14 MiB (~2,58 GiB)** |

Đã có thêm hai container từ stack PostgreSQL/Redis khác, tổng cộng khoảng 43 MiB trong ảnh chụp. Sáu JVM dùng khoảng 2.155 MiB (~2,10 GiB). VmmemWSL và tổng container không cộng trực tiếp: WSL còn phần overhead/cache và container khác.

## Giới hạn bằng chứng

Chưa thu được mẫu liên tục RAM/CPU, heap/GC, Hikari pool, PostgreSQL query plan, latency p50/p95, browser trace, memory paging hoặc thử tải. Chưa thể xác định có rò rỉ bộ nhớ, truy vấn chậm hay ứng dụng wrapper nặng. Snapshot này chỉ dùng làm mốc tham khảo; tạo baseline lặp lại theo hướng dẫn G0 trước khi tuyên bố mức cải thiện.

Khi đo lại, lưu CSV/JSON trong `docs/performance/results/`, không ghi cookie, bearer token, dữ liệu bệnh nhân, password hoặc nội dung request/response.
