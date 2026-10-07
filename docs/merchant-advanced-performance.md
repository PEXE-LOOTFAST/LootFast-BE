# Merchant Premium — Báo cáo nâng cao

Nhánh `feature/merchant-premium-analytics` kế thừa commit task 073 đã push, vì task
073 chưa có trên origin/dev lúc bắt đầu. Nhập PR 073 trước PR này để migration
V34 chạy trước V35. Không sửa migration đã áp dụng, không thêm Maven dependency
hoặc biến môi trường. Không triển khai mua gói, giá gói, tự động gia hạn, VNPay hay
quyền lợi khác trong thay đổi này.

## Phạm vi và quyền truy cập

- Báo cáo cơ bản `/api/v1/merchants/me/reports/performance` vẫn miễn phí.
- Báo cáo nâng cao `/api/v1/merchants/me/reports/performance/advanced` yêu cầu merchant
  đã được duyệt, tài khoản không khóa và quyền MERCHANT_ADVANCED_REPORTS đang hoạt động.
- Quyền đọc trực tiếp từ database; không tin cờ Premium trong frontend/JWT.
- `/api/v1/merchants/me/premium/benefits` trả advancedReports để frontend hiển thị.
- Chưa có subscription/thanh toán: admin có thể cấp quyền dùng thử thủ công qua
  `/api/v1/admin/premium/entitlements`, có thời điểm bắt đầu/kết thúc, lý do, actor.
  Đây không phải bằng chứng đã thanh toán. VNPay IPN và activation từ đơn mua gói
  sẽ là use case riêng; không cho khách tự gọi endpoint cấp quyền.
- Quyền hiệu lực ở startsAt, hết hiệu lực đúng endsAt; không cần worker chạy đúng giờ.
  Không xóa lịch sử đơn hoặc quyền hết hạn. Thu hồi một grant không thu hồi grant
  khác còn hiệu lực. Request đã bắt đầu được phép hoàn tất trên snapshot đã kiểm tra;
  quyền được kiểm tra lại ở request kế tiếp.

## Chỉ số

Ngày bao gồm hai đầu, múi giờ Asia/Ho_Chi_Minh. Mỗi kỳ tối đa 366 ngày.

- Current period do from/to chỉ định.
- Mặc định comparison period là kỳ liền trước, cùng số ngày. Có thể gửi cả
  compareFrom/compareTo cho kỳ khác; phải nằm trước và không chồng lấn current.
- Nếu số ngày khác nhau, samePeriodLength=false và mỗi period ghi days. Không tự
  coi tăng tổng doanh thu do kỳ dài hơn là tăng hiệu quả; frontend hiển thị rõ khoảng ngày.
- placedOrders và các trạng thái dùng created_at trong kỳ, trạng thái hiện tại.
- completionRatePercent = COMPLETED / placedOrders * 100.
- cancellationRatePercent = (CANCELLED + MERCHANT_CANCELLED) / placedOrders * 100;
  trả riêng customerCancelledOrders và merchantCancelledOrders.
- noShowRatePercent = NO_SHOW / placedOrders * 100. RESERVED vẫn tính vào mẫu số và
  có pendingOrders riêng. Các tỷ lệ lịch sử thay đổi khi đơn chưa xong chuyển trạng thái.
- Nếu không có đơn: tỷ lệ là null, không kết luận hiệu quả 0%. Làm tròn 2 chữ số.
- completedRevenue dùng finalized_at trong kỳ, chỉ COMPLETED + payment CONFIRMED,
  tổng total_amount snapshot; khớp báo cáo cơ bản. Không phải lợi nhuận, không phải
  số tiền của các đơn chỉ mới báo chuyển khoản.
- discountedPortions dùng finalized_at của COMPLETED, khớp báo cáo cơ bản (bao gồm
  completed legacy chưa có CONFIRMED). Không gọi là phần ăn giải cứu.
- So sánh revenue/placedOrders/discountedPortions: absoluteChange và percentChange.
  Khi kỳ trước bằng 0, percentChange=null và zeroBaseline=true; vẫn trả chênh lệch
  tuyệt đối, không tự nhận tăng 100% hoặc vô hạn.
- Chênh lệch tỷ lệ là điểm phần trăm, không phải % tăng tương đối. Nếu một kỳ không
  có đơn thì chênh lệch tỷ lệ null.
- deals phân trang theo doanh thu hoàn tất giảm dần, UUID làm thứ tự phụ. Tên lấy
  snapshot order.deal_name; không dùng giá/tên mới của deal để sửa báo cáo lịch sử.
- Dòng deal cũng có số đơn tạo trong kỳ, trạng thái hiện tại, các tỷ lệ và doanh thu
  hoàn tất trong kỳ. Doanh thu có thể từ đơn đặt trước kỳ; dòng có doanh thu nhưng
  không có đơn đặt trong kỳ thì các tỷ lệ null. Không cộng doanh thu hai lần.
- Mỗi dealId là một lần đăng; hai deal cùng tên vẫn tách riêng. Chưa gom các lần đăng
  theo công thức/mẫu món vì dữ liệu hiện tại chưa lưu liên kết đó trên deal.
- Chưa tính thời gian bán hết hoặc tỷ lệ phần đã giao / tồn kho đăng ban đầu: chưa
  có lịch sử tồn kho đầy đủ để tính đúng khi merchant sửa số lượng hoặc khách hủy.

## API admin cấp thử và thu hồi

POST `/api/v1/admin/premium/entitlements`:
```json
{
  "id": "d23d3971-cc8b-476d-8ad6-017c9366c6b0",
  "userId": "UUID_CUA_MERCHANT",
  "startsAt": "2026-10-07T00:00:00+07:00",
  "endsAt": "2026-11-07T00:00:00+07:00",
  "reason": "Dùng thử báo cáo nâng cao"
}
```
Thay userId bằng UUID thật và thời gian bằng lịch thử hiện tại. Mỗi lần cấp mới
phải có UUID id mới. Khoảng tối đa 366 ngày, endsAt của grant mới phải ở tương lai.
User đích phải có role MERCHANT, VERIFIED, không khóa. Đăng nhập merchant, GET
`/api/v1/users/me` lấy userId; không nhầm với id số nội bộ.

Gửi lại cùng id + payload + cùng admin: trả grant cũ, không tạo/ gia hạn lần hai.
Dùng id cũ cho payload/admin khác: PREMIUM_ENTITLEMENT_CONFLICT. Chuẩn hóa thời
điểm đến microsecond để retry an toàn với độ chính xác timestamp PostgreSQL.

GET `/api/v1/admin/premium/entitlements?userId=...&page=0&size=20` xem lịch sử.
POST `/api/v1/admin/premium/entitlements/{id}/revoke`:
```json
{"reason":"Kết thúc dùng thử"}
```
Thu hồi lặp giữ thời điểm, actor, lý do lần đầu. Retry grant đã thu hồi không kích
hoạt lại. Muốn cấp một đợt khác dùng id mới. Không có DELETE hoặc chỉnh sửa grant.

## Swagger flow

1. Merchant login -> Authorize -> GET benefits trả false; gọi advanced trả
   PREMIUM_REQUIRED; gọi báo cáo cơ bản vẫn được.
2. Lưu public userId từ GET users/me. Admin login -> Authorize -> POST cấp quyền
   với id UUID mới và lịch hiệu lực hiện tại. GET lịch sử có một grant; retry cùng
   payload vẫn chỉ một. Buyer/merchant gọi admin endpoint bị 403 trước validation.
3. Merchant Authorize -> GET benefits=true -> GET advanced:
   `/api/v1/merchants/me/reports/performance/advanced?from=2026-10-01&to=2026-10-07&page=0&size=20`.
   So sánh mặc định 2026-09-24..2026-09-30. Không có dữ liệu thì số đếm/tiền bằng 0,
   tỷ lệ null. Có thể dùng ngày khác có đơn thật trong database đang test.
4. Tạo vài đơn CASH/BANK_TRANSFER bằng luồng đã có, xác nhận thanh toán và giao;
   so với đơn CANCELLED/NO_SHOW/MERCHANT_CANCELLED/RESERVED. Advanced revenue phải
   khớp báo cáo cơ bản ở cùng from/to. Không dùng API mới để tự đổi trạng thái đơn.
5. Thử compareFrom/compareTo, page/size; thiếu một ngày so sánh, chồng lấn, quá 366
   ngày hoặc size>50 bị từ chối. Kỳ trước 0 không trả % tăng giả.
6. Admin revoke -> merchant benefits=false và advanced bị 403; dữ liệu đơn/báo cáo
   cơ bản vẫn giữ. Nếu có grant khác còn hiệu lực thì quyền vẫn true.
7. Quyền bắt đầu trong tương lai/hết hạn: ngoài window phải bị từ chối. Không thay
   ngày giờ máy hoặc sửa DB production để thử; test tự động dùng Clock riêng.

## Production review và kiểm tra

PostgreSQL tính aggregate, không nạp mọi entity đơn hàng vào JVM. Có 4 truy vấn
aggregate cho hai summary và trang deal/count, dùng index merchant+created_at và
index COMPLETED merchant+finalized_at đã có ở V32. Kết quả query bounded theo trang
<=50, khoảng ngày <=366. Cả báo cáo dùng một REPEATABLE_READ snapshot, timeout 10s.
Đã kiểm tra tình huống request khác hoàn tất đơn sau summary mà trước bảng deal:
response vẫn nhất quán; request sau thấy số liệu mới.

Kiểm tra quyền ban đầu trong transaction ngắn; HTTP flow đóng kết nối đó trước
khi gọi Redis rate limiter, rồi mở snapshot và kiểm tra quyền lại. Redis Lua dùng
chung quota 20 request/phút/merchant giữa các instance, key tự hết hạn sau 60s.
Redis lỗi/null trả RESOURCE_BUSY, quá quota trả REPORT_RATE_LIMITED (429). Không
có cache báo cáo hoặc queue chờ; người dùng có thể thử lại sau. Redis timeout dùng
cấu hình client hiện có; database timeout không thay thế timeout mạng Redis.

Admin cấp/thu hồi serialize theo user row; ON CONFLICT + đối chiếu payload đảm bảo
request cấp quyền lặp chỉ ghi một grant. Grant lưu actor/time/reason và revoke
metadata; không ghi nhận doanh thu Premium. V35 bổ sung index quyền và lịch sử,
không đổi user/order schema hoặc xóa dữ liệu. Rollback deployment phải giữ schema
mới và chọn checkout có lịch sử Flyway phù hợp; không downgrade/xóa volume.

Test PostgreSQL 17/18: số liệu snapshot, biên ngày VN, tách merchant, phân trang,
zero baseline, tỷ lệ, giới hạn input, quyền/trial start/expiry/revoke/idempotency,
precision timestamp, concurrent grant và concurrent order update. Redis 7 thật:
25 request đồng thời từ hai limiter instance chỉ 20 được qua, quota có TTL;
Redis lỗi từ chối. Không phải load test capacity production, chưa chứng minh SLO
hoặc số người dùng tối đa. Không kiểm thử VNPay vì chưa có luồng mua gói.

Backend local giữ tắt theo yêu cầu người dùng. Migration V35 chỉ áp dụng trong
container test; chưa cập nhật DB local hoặc Railway. Không có thêm env/key.

Kết quả xác minh: `./mvnw --batch-mode clean verify` đạt 324 test, 0 failure,
0 error, 0 skipped. Matrix quan sát: Java 21.0.12, Spring Boot 3.5.16, Maven Wrapper
3.9.16, Flyway 11.20.3, JDBC PostgreSQL 42.7.11, Testcontainers 1.21.4, PostgreSQL
17.11/18.6 trong test và Redis 7. DB local PostgreSQL 17.11 đang ở Flyway V32,
không chạy migration vào DB local; không có kết quả health HTTP của server thật
vì backend được giữ tắt theo yêu cầu.
