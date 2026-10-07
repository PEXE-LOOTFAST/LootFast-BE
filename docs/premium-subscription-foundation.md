# Premium: subscription, kỳ sử dụng và gia hạn

Bước 2 của kế hoạch Premium. Có nền tảng lưu kỳ trả phí và API đọc của chủ tài khoản.
**Chưa có checkout, VNPay IPN hay mua gói qua Swagger.** Không nhận cờ thanh toán
thành công từ frontend. Những kỳ PURCHASE trong automated tests là fixture kiểm thử,
không phải giao dịch ngân hàng thật.

## Quy tắc

- Một tài khoản có tối đa một subscription mỗi phía BUYER/MERCHANT; hai phía độc lập.
- Mỗi lần thanh toán được xác minh tạo một kỳ riêng, snapshot phiên bản gói, tên,
  giá VND, thời hạn, quyền lợi và phiên bản điều khoản. Publish giá mới không sửa kỳ cũ.
- Mua mới/hết hạn: bắt đầu khi xử lý xác nhận. Gia hạn còn hiệu lực: bắt đầu lúc kỳ
  trả phí cuối kết thúc. Một ngày là 24 giờ, lưu UTC với độ chính xác microsecond.
- Khoảng sử dụng `[startsAt, endsAt)`: đến đúng endsAt thì hết quyền. Không cần cron
  đổi trạng thái để chặn tính năng khi hết hạn.
- Trial admin có source MANUAL; quyền theo kỳ mua có source PURCHASE. Trial không
  đẩy lùi ngày bắt đầu kỳ trả phí. API admin trial không được revoke quyền PURCHASE.
- Quyền thực tế là hợp các khoảng MANUAL/PURCHASE liên tục đang bao phủ thời điểm
  hiện tại. Không cộng một khoảng tương lai nếu có khoảng trống trước nó.
- `paidEndsAt` là cuối kỳ trả phí; `benefits[].effectiveUntil` là cuối khoảng quyền
  liên tục, có thể dài hơn khi có trial. `activeSources` là nguồn đang bao phủ hiện
  tại; `enabled` còn kiểm tra role, xác minh, khóa tài khoản và duyệt merchant.
- `NONE`: chưa có kỳ trả phí (vẫn có thể có trial); `ACTIVE`: còn thời gian và đủ
  điều kiện; `SUSPENDED`: còn thời gian nhưng thiếu điều kiện; `EXPIRED`: hết kỳ trả phí.
  Khóa tài khoản không xóa receipt hoặc dừng đồng hồ kỳ sử dụng. JWT tài khoản bị
  khóa có thể bị lớp bảo mật từ chối trước API; SUSPENDED là trạng thái nghiệp vụ.
- Gói đã publish rồi archive vẫn giữ nguyên cấu hình: đơn đã tạo trước archive sẽ
  cần dùng đúng revision đó ở bước thanh toán. Draft chưa publish không cấp kỳ.

## API đọc trên Swagger

Đăng nhập rồi Authorize bằng access token. Không truyền userId; dữ liệu lấy theo JWT.

1. `GET /api/v1/premium/me/subscriptions`: trạng thái cả BUYER/MERCHANT.
2. `GET /api/v1/premium/me/benefits?audience=BUYER` (hoặc MERCHANT): quyền thực tế.
3. `GET /api/v1/premium/me/subscriptions/BUYER/periods?page=0&size=20`: lịch sử kỳ;
   size tối đa 50. Tài khoản khác không đọc được kỳ của mình qua các API này.

Chưa mua thì periods rỗng và status NONE. Để thử quyền hiện có qua Swagger, admin
cấp **trial MANUAL** theo flow trước đây; không seed PURCHASE hoặc thêm endpoint
"thanh toán thành công" để thử. Flow mua/gia hạn thật sẽ có sau checkout + IPN.

## Transaction, chống trùng và giới hạn

`SubscriptionService.applyConfirmedPurchase` chỉ là hàm nội bộ, chưa có caller runtime.
`Propagation.MANDATORY` bắt buộc caller có transaction; handler thanh toán đã xác minh
ở bước sau phải ghi trạng thái giao dịch và kỳ/quyền trong **cùng transaction**.
Rollback cũng xóa toàn bộ phân bổ của lần xử lý đó.

Khóa row user/subscription trong PostgreSQL tuần tự hóa gia hạn trên nhiều instance.
Unique purchase_reference chống callback trùng; retry cùng owner/revision/confirmedAt
trả lại kỳ cũ. Payload khác cùng reference bị conflict. Các query read dùng snapshot
REPEATABLE_READ; phân trang và index giới hạn truy vấn lịch sử.

Hiện purchase_reference chưa có FK đến đơn Premium (bảng đơn chưa tồn tại). Bước
checkout/IPN phải thêm ràng buộc liên kết đơn, xác thực chữ ký/amount/provider transaction
và điều khoản đã chấp thuận trước khi gọi hàm này. Hàm không tự xác minh tiền.
Refund/revoke trả phí, lifecycle email/outbox và nghiệm thu end-to-end thuộc bước sau.

Không thêm dependency hoặc biến môi trường. V41 là migration mới; V35–V40 giữ nguyên.
Test dùng database container riêng; chưa áp migration vào database phát triển.
Chưa đo tải production, chưa khẳng định năng lực chịu tải.

## Môi trường kiểm chứng

Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`, branch
`feature/merchant-premium-analytics`, HEAD gốc `2da6e24` (chưa commit).
Java 21.0.12 / Spring Boot 3.5.16 / Maven Wrapper 3.9.16 / Flyway 11.20.3 /
PostgreSQL JDBC 42.7.11 / Testcontainers 1.21.4. Docker local PostgreSQL 17,
Redis 7; các integration test cũng kiểm tra thêm PostgreSQL 18 để giữ tương thích
với môi trường triển khai. Không đổi major version hoặc chạm volume hiện có.

Backend local được giữ OFF theo yêu cầu trước đó; không có health/runtime HTTP
để báo UP. API và OpenAPI kiểm tra bằng MockMvc với Spring context và PostgreSQL
container riêng. Database `lootfast_reports_dev` local: PostgreSQL 17.11, Flyway V32;
source V41. Local DB chưa được migrate trong task này.

Full suite ban đầu phát hiện H2 dùng chung `jdbc:h2:mem:lootfast` giữa nhiều Spring
context. Khi cache context đầy, context cũ đóng với `create-drop` xóa schema của
context khác, gây `Table USERS not found` ở test payment. Đã đổi URL test thành
`jdbc:h2:mem:lootfast-${random.uuid}` để mỗi context có database riêng; giữ nguyên
create-drop và toàn bộ test, không tăng cache để che lỗi. Không đổi cấu hình runtime.

Kết quả cuối: `./mvnw --batch-mode clean verify` BUILD SUCCESS lúc
2026-10-07 16:50:03 +07:00; 461 tests, không lỗi hoặc skip. Trong đó có 4 unit
test RenewalPolicy và 14 integration test mỗi PostgreSQL 17.11/18.6; Spring
context khởi tạo và Flyway V41 thành công trên hai database container. OpenAPI
đọc API mới thành công qua MockMvc. Log: `/tmp/lootfast-subscription-final-verify-2.log`.
