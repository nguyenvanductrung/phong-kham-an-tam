# Kế hoạch tối ưu hiệu năng, giữ nguyên kiến trúc microservices

Ngày lập: 04/11/2026. Trạng thái: đang triển khai; G1–G2 đã áp dụng, G0/G3–G6 còn một phần hoặc cần đo tiếp. Kết quả tạm thời ở [results.md](results.md).

## 1. Phạm vi và dữ liệu hiện có

Giữ nguyên Next.js/BFF, API Gateway, năm service nghiệp vụ, PostgreSQL theo quyền sở hữu dữ liệu từng service, Redis, RabbitMQ và MinIO. Tối ưu cho máy Windows hiện tại trước, cấu hình production được quyết định bằng phép đo riêng.

Ảnh chụp tài nguyên trong phiên khảo sát, không phải kết quả benchmark tải:

| Thành phần | Quan sát |
|---|---|
| Web production | 93,34 MiB |
| Sáu JVM, gồm gateway | Khoảng 2,10 GiB; mỗi tiến trình 345–377 MiB |
| Tổng container phòng khám | Khoảng 2,58 GiB |
| Tiến trình vmmemWSL | Khoảng 5,28 GiB |
| RAM Windows | Tổng 15,3 GiB; còn trống khoảng 0,8 GiB |
| Cấu hình WSL | memory=6GB, swap=4GB, processors=8; chưa khai báo autoMemoryReclaim |

Chênh lệch giữa WSL và container chưa được phân rã: có thể bao gồm kernel, cache, tiến trình và workload khác. Chưa có bằng chứng memory leak, áp lực GC, truy vấn chậm hoặc phân trang hệ điều hành; cần đo ở G0. CPU tại thời điểm chụp thấp, chưa phản ánh lúc người dùng thao tác.

## Trạng thái triển khai (04/11/2026)

- G0: đã có script lấy mẫu tài nguyên, benchmark đăng nhập tự động qua web/BFF và baseline snapshot. Đã thu 64 mốc idle trong 9,9 phút và lặp GET tải liên tục 5 worker ba lượt, mỗi lượt 60 giây; baseline cũ là snapshot khác thời điểm nên A/B vẫn chưa hoàn toàn cùng điều kiện.
- G1: đã thêm Compose override tự chọn cho giới hạn heap/container và Hikari pool; đang chạy profile này local. Không đổi kiến trúc service hay volume.
- G2: đã áp dụng stale/gc time, xóa cache khi đổi phiên, debounce/cancel tìm kiếm, Link nội bộ và bảo toàn form nháp khi refetch. TypeScript/lint/build và E2E hiện có đã qua.
- G3: truy vấn summary bệnh nhân chỉ chọn các cột cần thiết; paging/filter API, EXPLAIN và index chưa đổi vì chưa có dataset/kế hoạch truy vấn đủ để chứng minh lựa chọn.
- G4: đã thống nhất timeout RestClient, BFF GET deadline 8 giây và truyền hủy request. Worker/outbox và SMTP chưa chỉnh khi chưa có số đo queue, pool hoặc thời gian chờ dependency.
- G5: có hướng dẫn bật/tắt profile và đo; `.wslconfig` chưa sửa.
- G6: smoke test và E2E chính qua. Docker image build của backend hiện dùng `-Dmaven.test.skip=true`, nên báo cáo không coi kiểm thử Maven backend là đã chạy; soak 60 phút và các luồng hồi quy mới còn lại.

Chi tiết số đo ban đầu và giới hạn so sánh ở [results.md](results.md). Snapshot sau thay đổi là thử tải đầu tiên, không thay thế số đo lặp cùng điều kiện.

Bằng chứng từ mã nguồn:

- `backend/Dockerfile`: tất cả JVM dùng `-XX:MaxRAMPercentage=75.0`; Compose chưa có giới hạn bộ nhớ từng container.
- Các `application.yml` của năm service có JDBC chưa khai báo pool Hikari theo tải.
- `providers.tsx`: QueryClient ở root, staleTime chung 20 giây; đổi tài khoản cần xử lý cache trước khi tăng thời gian giữ dữ liệu.
- `app-shell.tsx`: menu nội bộ dùng thẻ a, gây điều hướng toàn tài liệu.
- Các màn patients/accounts và tìm bệnh nhân đặt lịch dùng trực tiếp chuỗi đang nhập làm query key, chưa debounce; query function chưa truyền signal xuống fetch.
- `JdbcPatientDao.search`: SELECT * và tìm chứa chuỗi bằng ILIKE/LIKE `%...%`; hiện có index thường trên lower(full_name)/phone, cần kiểm tra execution plan thực tế.
- Outbox hai service poll mỗi 1 giây; worker email mỗi 2 giây. Cả hai giữ transaction trong lúc gọi mạng. Đây là giả thuyết gây chờ khi dependency chậm, chưa đo được mức ảnh hưởng.
- Gateway đã có connect timeout 2 giây và response timeout 5 giây. BFF chưa đặt deadline rõ ràng; timeout của các RestClient chưa thống nhất.
- Chưa có cấu hình Electron/Tauri trong repo được khảo sát; đo web trước, đo thêm tiến trình wrapper nếu thực tế có bản desktop riêng.

## 2. Mục tiêu và cách nghiệm thu

Các con số dưới đây là mục tiêu thử nghiệm, không phải cam kết mức tiết kiệm. Chốt khả năng đạt sau baseline và ghi rõ trường hợp không đạt.

| Chỉ số | Mục tiêu ban đầu | Cách đánh giá |
|---|---|---|
| RAM container phòng khám sau warm-up, idle | Giảm ít nhất 15% so với baseline cùng điều kiện | Trung vị mẫu 5 giây/lần trong 10 phút; so với baseline mới ở G0 |
| RAM Windows khả dụng | Hướng tới >=2 GiB với cùng ứng dụng nền | Ghi cả RAM khả dụng, paging và WSL; tách lợi ích do tối ưu app và thay đổi workload nền |
| API đọc thường dùng | p95 <=500 ms ở 5 phiên hoạt động | Đo end-to-end qua BFF, tách tìm kiếm, lịch hẹn, workspace; báo thêm ở 10/20 phiên |
| Lưu bệnh án/đặt lịch | p95 <=1 giây ở 5 phiên | Tách thời gian HTTP commit và cập nhật bất đồng bộ; không tính upload vào nhóm này |
| Nhập tìm kiếm | Hiển thị phản hồi nhập p95 <=200 ms; kết quả p95 <=1 giây sau ký tự cuối | Đo bằng browser trace; thời gian kết quả đã gồm debounce 300 ms |
| Chuyển màn đã warm-up | p95 <=1 giây tới nội dung chính dùng được | Dùng route thông thường và menu thật, không chỉ đo HTTP |
| Số request tìm kiếm | Một request cuối cho một đợt gõ liên tục có khoảng cách <300 ms | Đếm theo từng ô tìm kiếm; đợt gõ có khoảng dừng dài có thể có request trung gian |
| Sự kiện chốt hồ sơ -> lịch hoàn tất | p95 <=3 giây khi hệ thống khỏe | Đo từ transaction chốt tới consumer cập nhật lịch; ghi riêng khi phục hồi dependency |
| Độ ổn định | Không OOM/restart; không mất dữ liệu, sai quyền hoặc lỗi cạnh tranh mới | Chạy tải hỗn hợp 60 phút và kiểm tra hồi quy nghiệp vụ |

Đo baseline và từng phương án trên cùng dataset, image production, số phiên, ứng dụng nền và kịch bản. Mỗi trường hợp lặp ba lần; báo p50/p95, số mẫu và lỗi. Tách cold start khỏi warm-up; không build image hay chạy integration test cùng lúc benchmark. Chỉ kết luận lợi ích khi khác biệt lớn hơn dao động giữa các lần chạy.

## 3. G0 — Công cụ đo và baseline (0,5–1 ngày)

Đầu ra dự kiến:

- `scripts/performance/measure-resources.ps1`: lấy mẫu container RAM/CPU/restart, Windows available memory và tiến trình WSL; xuất CSV có timestamp/service/image ID.
- `scripts/performance/benchmark.mjs`: tải API bằng Node có sẵn, giới hạn concurrency, thời gian nghỉ và ghi latency/error; credential chỉ đọc từ môi trường, không ghi token/body bệnh án vào kết quả.
- `docs/performance/baseline.md`: môi trường, dữ liệu, kịch bản, kết quả và điểm nghẽn đã được chứng minh.
- Nếu Actuator hiện tại chưa cung cấp đủ số liệu, bổ sung metrics/GC log trong cấu hình đo riêng. Hiện khai báo exposure prometheus chưa đủ để kết luận registry và endpoint hoạt động.

Trình tự:

1. Ghi image/commit đang chạy và mức chênh với working tree, phiên bản Docker/WSL, trạng thái container. Chụp cấu hình hiệu lực đã lọc secret, không lưu toàn bộ compose config chứa mật khẩu.
2. Thu thập heap dùng sau GC, metaspace/native memory khi công cụ cho phép, thread count, GC pause, Hikari active/idle/pending, pg_stat_activity và hàng đợi RabbitMQ. Image JRE có thể thiếu jcmd: chỉ dùng diagnostic image có JDK trong đợt profiling riêng, không so RSS của diagnostic image với production.
3. Idle: warm-up 5 phút, đo 10 phút. Tải: 5/10/20 phiên, mỗi mức 10 phút. Dùng phiên đã đăng nhập, không đăng nhập cho mỗi request vì gateway giới hạn tốc độ auth.
4. Trộn đọc/tìm kiếm khoảng 80%, thao tác ghi khoảng 20%; đi theo vai trò lễ tân, bác sĩ, quản lý, bệnh nhân. Mỗi luồng ghi có dữ liệu/khung giờ riêng, tách xung đột nghiệp vụ dự kiến khỏi lỗi hệ thống.
5. Dữ liệu tổng hợp trên môi trường benchmark riêng: khoảng 10.000 bệnh nhân, 20.000 lịch hẹn trải theo ngày, 2.000 hồ sơ khám; có thêm ngày >500 lịch để kiểm tra kết quả bị cắt. Ghi seed cố định để tái lập.
6. Browser trace cho tìm kiếm, chuyển màn, mở bệnh án và 100 lượt điều hướng; theo dõi JS heap/DOM/cache query sau warm-up, không coi một đợt heap tăng là bằng chứng leak.
7. Nếu có desktop wrapper, lặp luồng bằng wrapper và ghi thêm renderer/main process, số cửa sổ, trạng thái dev/prod để tách chi phí wrapper khỏi backend.

Hoàn tất G0 khi có baseline tái lập và xác định thiếu RAM/paging, GC, database hay frontend là nút thắt chính. Chưa hạ giới hạn WSL ở bước này.

## 4. G1 — Ngân sách JVM, pool và container (1 ngày)

File dự kiến sửa: `backend/Dockerfile`, thêm `infrastructure/compose/docker-compose.performance.yml`, thêm cấu hình performance cho năm service JDBC và hướng dẫn chạy. Dùng cấu hình bổ sung để có thể bật/tắt độc lập; kiểm tra profile bổ sung không thay thế profile dev đang tạo dữ liệu demo.

Ngân sách thử nghiệm đầu tiên:

| Service | Xms | Xmx | Giới hạn container |
|---|---:|---:|---:|
| identity | 64 MiB | 256 MiB | 512 MiB |
| patient | 64 MiB | 256 MiB | 512 MiB |
| scheduling | 64 MiB | 384 MiB | 640 MiB |
| clinical | 64 MiB | 384 MiB | 640 MiB |
| notification | 64 MiB | 256 MiB | 512 MiB |
| gateway | 64 MiB | 256 MiB | 512 MiB |

Đây là trần phân bổ, không phải RAM sử dụng dự kiến. Tổng trần sáu JVM container là 3.328 MiB; vẫn phải chừa bộ nhớ cho Node, database, broker, MinIO, WSL và workload khác. Xmx không bao gồm toàn bộ native memory. Nếu heap sống sau GC còn nhiều, tăng ngân sách service đó; nếu có dư địa rõ ràng, thử mức thấp hơn trong một phép đo riêng. Đổi MaxRAMPercentage đơn thuần có thể không làm RSS hiện tại giảm.

Công việc:

- Bỏ giá trị 75% cố định khỏi entrypoint; cho phép cấu hình JVM từng service bằng JAVA_TOOL_OPTIONS, không lặp cờ mâu thuẫn trong entrypoint. Giữ mặc định chạy thông thường có hành vi rõ ràng.
- Chỉ đặt limit hạ tầng sau khi ghi được đỉnh thực tế; tránh giới hạn RabbitMQ/PostgreSQL theo con số tùy ý gây restart hoặc memory alarm.
- Hikari: thử minimumIdle=1, maximumPoolSize=5 ở năm service JDBC; pool tối đa cộng lại là 25, cộng thêm kết nối migration, health/diagnostic và tác vụ khác khi tính ngân sách PostgreSQL. Gateway reactive không áp dụng Hikari.
- Đo thời gian chờ pool, đặc biệt worker giữ transaction. Nếu pool pending tăng hoặc request chậm hơn, xử lý transaction/điều chỉnh pool trước khi giữ cấu hình mới.
- Chỉ giảm thread pool servlet nếu thread count/stack memory là nguyên nhân đã đo; định cỡ theo số request đồng thời và thao tác blocking. Gateway Netty được đánh giá riêng.
- Giữ GC mặc định ở lượt đầu. Chỉ so sánh GC khác trong thí nghiệm riêng nếu pause/CPU có bằng chứng; không đồng thời thay GC, Xmx và pool khi cần xác định nguyên nhân.

Nghiệm thu: startup/migration/readiness thành công, tải G0 không OOM hoặc pool timeout mới; RSS và latency có báo cáo trước/sau. Bác bỏ cấu hình làm p95 xấu hơn >10% lặp lại qua ba lần đo.

## 5. G2 — Giao diện, điều hướng và cache theo phiên (1–1,5 ngày)

File chính: `providers.tsx`, `app-shell.tsx`, `login-form.tsx`, `session-context.tsx`, `use-api.ts`, `api-client.ts`, các workspace patients/accounts/appointments/clinical và các trang có link nội bộ.

Thứ tự bắt buộc:

1. Cô lập cache theo identity/session. Khi logout, hết phiên hoặc đổi identity: hủy request đang chạy, xóa cache và xử lý router cache liên quan. Đăng xuất lỗi phải được phản ánh đúng; request cũ không được đưa dữ liệu phiên trước trở lại.
2. Kiểm tra form bệnh án: refetch không được reset dirty fields; thay record phải cô lập form và kết quả mutation theo record đích. Đây là điều kiện trước khi tăng tái sử dụng cache hoặc đổi cách điều hướng tại màn lâm sàng.
3. Truyền AbortSignal từ queryFn qua useApi/apiOperation/apiRequest tới fetch. Helper đã nhận signal ở API operation, phần còn thiếu chủ yếu là các nơi gọi query.
4. Thêm debounce 300 ms cho tìm bệnh nhân, tài khoản và bộ chọn bệnh nhân/tài khoản. Kiểm tra kết quả về sai thứ tự; reset trang khi thay từ khóa/bộ lọc. Không áp dụng debounce lên thao tác lưu/đặt lịch.
5. Chuyển menu/CTA nội bộ thích hợp sang Next Link, ban đầu prefetch=false trên điều hướng workspace để kiểm soát tải nền. Link tải tệp và luồng cần điều hướng tài liệu vẫn xử lý theo mục đích. Bảo vệ bản nháp khi rời trang.
6. Danh mục ít đổi: thử staleTime=5 phút, gcTime=10 phút, invalidate đúng key sau sửa danh mục. Trên màn có khả năng ghi: làm mới danh mục khi mở form/lấy focus hoặc theo version để phản ánh thay đổi từ người dùng khác; backend tiếp tục là nơi kiểm tra tính hợp lệ.
7. Dữ liệu bệnh nhân/bệnh án: chọn staleTime ngắn theo nghiệp vụ; thử gcTime=1–2 phút cho tìm kiếm không còn dùng. Không tăng cache chung toàn ứng dụng; mutation cập nhật/invalidate đúng scope. Tránh giữ kết quả bệnh nhân A làm placeholder của bệnh nhân B.
8. Nếu trace cho thấy render nặng, tách vùng form/danh sách và memoize phần tốn thời gian. Chỉ cân nhắc virtual list khi phân trang chưa đủ và số DOM lớn là điểm nghẽn.

Nghiệm thu: request tìm kiếm giảm đúng kịch bản, chuyển menu không tải lại toàn tài liệu, cache không tăng mãi sau 100 lần đổi màn. Test cùng tab A -> logout -> B bằng UI thật, có mạng chậm/request cũ; không hiển thị dữ liệu A. Test bệnh án đang nhập -> thêm răng/thủ thuật/refetch -> nội dung còn nguyên.

## 6. G3 — Truy vấn và phân trang đúng dữ liệu (1–1,5 ngày)

File chính: DAO/controller/model của patient, identity và scheduling; `contracts/openapi/clinic-api.yaml`; client sinh `frontend/web/src/lib/api.generated.ts`; các workspace liên quan.

Công việc:

- Chạy EXPLAIN (ANALYZE, BUFFERS) cho truy vấn đọc đại diện trên dataset benchmark: tìm tên, điện thoại, mã, bộ lọc archived và lịch theo ngày/7 ngày. Kiểm tra cả chuỗi ngắn, phổ biến và không có kết quả.
- Xem xét pg_trgm + GIN/GiST cho tìm chứa chuỗi khi query plan chứng minh có lợi. Truy vấn rỗng/từ khóa quá ngắn có thể vẫn scan; giữ ngữ nghĩa tìm kiếm hiện có và đo chi phí ghi/kích thước index.
- Dùng migration Flyway mới, không sửa migration đã áp dụng. Xác nhận quyền cài extension ở từng database và phương thức tạo index phù hợp môi trường đích.
- Thay SELECT * bằng cột thực sự cần trong danh sách bệnh nhân; giữ response contract tương thích và không vô tình đưa trường lâm sàng vào danh sách.
- Patients: nối page/size/hasNext sẵn có với UI; reset page khi đổi bộ lọc, thứ tự sắp xếp ổn định. Accounts hiện LIMIT 200, thiết kế page/size hoặc cursor có metadata thay vì âm thầm cắt kết quả.
- Lịch hẹn: đưa filter bác sĩ/ghế xuống server; phân trang trước khi bỏ giới hạn 500 hiện tại. Xác nhận contract thực tế khi triển khai; không tải không giới hạn để giải quyết thiếu dữ liệu.
- Sinh lại OpenAPI client; rà tất cả nơi gọi API thay đổi, gồm patient portal và clinical workspace. Không tạo một request lấy tên bệnh nhân cho mỗi hàng.

Nghiệm thu: tìm/xem được bản ghi ngoài trang đầu, không bỏ sót lịch ở ngày >500 lịch; không lặp/mất hàng do sort không ổn định trong dataset cố định. Có execution plan và p95 chứng minh lợi ích index; tải ghi không suy giảm đáng kể.

## 7. G4 — HTTP và worker nền (1–1,5 ngày, ưu tiên theo G0)

File chính: `server-api.ts`, BFF route `app/api/bff/[...path]/route.ts`, các LookupClient/PatientLifecycleClient, OutboxWorker, MailJobWorker và DAO liên quan.

Công việc ít thay đổi trước:

- Đặt connect/read timeout nhất quán cho RestClient, có ngân sách tổng theo đường gọi. Gateway hiện 5 giây response timeout; xác định nó giới hạn phần nào của request thay vì giả định đó là deadline toàn hành trình.
- BFF/server fetch có deadline phù hợp, truyền cancellation của request đọc khi client hủy. Các luồng upload/download có ngân sách riêng; BFF hiện buffer body mutation qua arrayBuffer, cần đo đỉnh RAM khi upload nhiều tệp 20 MB trước khi thiết kế streaming/replay.
- Không tự retry thao tác ghi nếu chưa chứng minh idempotency. Timeout sau ghi có thể xảy ra dù server đã commit; giữ thông tin giúp đối soát/thử lại đúng cách.
- Đặt SMTP connect/read/write timeout để mail worker không chờ vô hạn.

Worker chỉ chỉnh sau khi đo:

- Trước tiên giữ poll outbox 1 giây và email 2 giây; đo số truy vấn rỗng, pending age, thời gian giữ transaction và chờ pool.
- Nếu truy vấn rỗng đáng kể: thử backoff khi idle, giới hạn thêm độ trễ sao cho sự kiện chốt hồ sơ vẫn đạt mục tiêu. Email có ngân sách trễ riêng; không tắt consumer để giảm RAM.
- Nếu gọi RabbitMQ/SMTP trong transaction là điểm nghẽn: tách thành thay đổi riêng có cơ chế claim/lease, khôi phục khi worker chết và retry. Giữ thứ tự theo aggregate, publisher confirm, DLQ và consumer idempotency hiện có.
- Không chỉ chuyển cuộc gọi mạng ra ngoài transaction mà bỏ cơ chế claim. Không tăng concurrency cho outbox trước khi kiểm tra thứ tự sự kiện.

Nghiệm thu: mô phỏng dependency chậm/mất kết nối rồi phục hồi trên môi trường benchmark. Không bỏ mất sự kiện, không áp dụng trùng nghiệp vụ, không để job bị kẹt; xác nhận độ trễ khỏe và thời gian drain backlog sau phục hồi. SMTP có thể gửi trùng nếu crash sau gửi nhưng trước đánh dấu; ghi rõ giới hạn at-least-once thay vì hứa exactly-once.

## 8. G5 — WSL và quy trình build (0,5 ngày)

Đầu ra repo: hướng dẫn `docs/performance/local-runtime.md`, không tự sửa cấu hình máy khi chỉ thực hiện kế hoạch.

- Kiểm tra phiên bản WSL hỗ trợ autoMemoryReclaim, cấu hình hiệu lực và khả năng tương thích Docker hiện tại. Thử gradual trong đợt riêng, so RAM/cache trước và sau build/idle.
- Giữ memory=6GB ở phép đo đầu. Chỉ giảm trần nếu tổng workload có đủ dư địa; tăng swap không được coi là tối ưu độ trễ.
- Việc áp dụng .wslconfig cần restart WSL, ảnh hưởng các distro/container khác: xếp vào thời gian triển khai riêng, lưu bản cấu hình cũ để phục hồi.
- Build backend có giới hạn số job đồng thời trên máy ít RAM; ghi lại thời gian build/đỉnh RAM để chọn concurrency. Những lần chạy lại dùng image có sẵn, chỉ rebuild service đổi mã.
- Phân biệt đo runtime và build, không xóa volume hoặc dữ liệu để giảm số đo RAM. Dừng workload khác chỉ trong kịch bản kiểm soát đã ghi nhận; so sánh cả trường hợp dùng máy hằng ngày.

Nghiệm thu: Windows có thêm RAM khả dụng khi idle nếu cache là nguyên nhân; benchmark không xấu hơn do reclaim/paging. Kết quả âm cũng được ghi nhận, không giữ thiết lập chỉ vì RSS thấp hơn.

## 9. G6 — Kiểm thử cuối, phát hành và hoàn tác (1 ngày)

Chạy các kiểm tra phù hợp thay đổi:

```powershell
.\mvnw.cmd -B verify
corepack pnpm --dir frontend/web lint
corepack pnpm --dir frontend/web typecheck
corepack pnpm --dir frontend/web build
corepack pnpm --dir frontend/web test:e2e
```

Chỉ sinh client khi contract thay đổi, rồi kiểm tra diff tương ứng. Verify cần Docker cho integration test; ghi số test thực sự chạy và số test skip, không coi skip là pass. Tách build/test khỏi benchmark để tránh cạnh tranh RAM.

Mở rộng kiểm thử có ý nghĩa cho các thay đổi:

- Cache/phiên: đổi người dùng trong cùng tab, hết phiên, request cũ hoàn tất muộn; kiểm tra cả dữ liệu route và query cache.
- Tìm kiếm: một đợt gõ nhanh, hủy request cũ, response đảo thứ tự, đổi filter/page.
- Nghiệp vụ: E2E hiện có từ tạo bệnh nhân -> đặt lịch -> tiếp đón -> khám/chốt -> portal; thêm bảo toàn dirty form và không lẫn dữ liệu giữa record.
- Cạnh tranh: hai người đặt cùng slot, hai bác sĩ dùng version cũ, mutation trả timeout sau commit; giữ đúng kết quả xung đột/idempotency đã thiết kế.
- API paging/filter/index: đầy đủ dữ liệu, quyền theo vai trò, các trang biên và dữ liệu lớn.
- Worker: lỗi mạng, confirm timeout, restart sau claim và duplicate event nếu có sửa worker.
- Upload: giữ giới hạn hợp lệ, thử đồng thời vừa phải; đo đỉnh RAM và kết quả sau timeout, không gộp với latency API đọc.

Đo lại G0 và chạy soak 60 phút. Theo dõi RAM cuối mỗi cửa sổ 10 phút sau warm-up, GC/pool/queue/restart; điều tra tăng trưởng kéo dài sau khi workload kết thúc và cache hết hạn. Tải 20 phiên là điểm khảo sát khả năng chịu tải, không tự coi toàn bộ mục tiêu 5 phiên là SLA 20 phiên.

Triển khai chia thành các thay đổi có thể hoàn tác độc lập:

| Đợt | Nội dung | Phụ thuộc |
|---|---|---|
| P0 | Công cụ đo + baseline | Không |
| P1 | JVM/container/pool, bật bằng cấu hình bổ sung | P0 |
| P2 | Cô lập session cache + bảo toàn form cần thiết | P0; trước P3 |
| P3 | Debounce/cancel, Link, cache theo loại dữ liệu | P2 |
| P4 | Query/index/paging + contract/client | P0; phối hợp P3 |
| P5 | Timeout/worker theo điểm nghẽn đã đo | P0; pool ở P1 cần kiểm lại nếu worker đổi |
| P6 | WSL, báo cáo cuối và runbook | Kết quả P1–P5 |

Mỗi đợt lưu commit/image cũ, kết quả đo và lệnh áp dụng. Khi thực thi, tạo nhánh riêng theo quy ước codex/; giữ nguyên các tài liệu chưa commit của người dùng.

Rollback khi OOM/restart mới, sai quyền/mất dữ liệu, lỗi ngoài dự kiến tăng, pending event không drain hoặc p95 xấu hơn >10% được tái hiện. Đầu tiên tắt compose override/cấu hình của đợt gây lỗi hoặc phục hồi image tương ứng. Với thay đổi contract, phát hành/hoàn tác các consumer liên quan theo thứ tự tương thích; index thêm có thể giữ khi rollback app. Không sửa lịch sử Flyway hoặc khôi phục database đè dữ liệu mới chỉ để hoàn tác tuning. WSL phục hồi file cấu hình cũ rồi restart trong khung triển khai.

Báo cáo cuối `docs/performance/results.md`: baseline -> từng đợt -> kết quả cuối, thông số được chọn/bác bỏ, p50/p95/số mẫu, RAM/GC/pool/queue, test đã chạy, giới hạn tải và các việc còn lại.

## 10. Ước lượng và thứ tự thực hiện

Với một người triển khai, dự kiến khoảng 6–9 ngày làm việc, phụ thuộc thời gian profiling và phạm vi thay đổi API/worker. Đây là ước lượng công việc, không phải lịch đã cam kết.

1. Ngày 1: G0, chốt điểm nghẽn và chỉ số thực tế.
2. Ngày 2: G1, cấu hình JVM/pool và so sánh.
3. Ngày 3–4: G2, ưu tiên session cache/form rồi mới điều hướng/cache dài hơn.
4. Ngày 5–6: G3 nếu database/payload là nút thắt; cập nhật contract cùng frontend.
5. Ngày 6–8: G4 theo bằng chứng; phần thay đổi cơ chế worker chỉ làm nếu cần, có thể tách đợt sau.
6. Ngày cuối: G5/G6, soak, đối chiếu mục tiêu, runbook và hoàn tác.

Mốc quyết định: sau G1/G2, nếu đã đạt mục tiêu, chỉ thực hiện phần G3/G4 đã có bằng chứng hoặc cần để bảo đảm đầy đủ dữ liệu. Giữ toàn bộ service và cơ chế dữ liệu hiện có trong mọi đợt.

## 11. Tài liệu kỹ thuật đối chiếu

- [Java 21: Xmx, MaxRAMPercentage và GC logging](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): Xmx giới hạn heap; ngân sách container phải tính thêm phần ngoài heap.
- [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby): minimumIdle, maximumPoolSize và tradeoff khi định cỡ pool.
- [Next.js Link](https://nextjs.org/docs/app/api-reference/components/link): điều hướng phía client và kiểm soát prefetch.
- [TanStack Query cancellation](https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation): truyền signal từ query function tới transport để hủy request.
- [PostgreSQL pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html): index hỗ trợ LIKE/ILIKE, hiệu quả phụ thuộc pattern và độ chọn lọc.
- [WSL configuration](https://learn.microsoft.com/en-us/windows/wsl/wsl-config) và [Docker WSL backend](https://docs.docker.com/desktop/features/wsl/): cấu hình memory/reclaim và cache sau build.

Các cấu hình đề xuất là phương án thử nghiệm của dự án; tài liệu nhà cung cấp không bảo đảm mức RAM/latency mục tiêu trên máy này.
