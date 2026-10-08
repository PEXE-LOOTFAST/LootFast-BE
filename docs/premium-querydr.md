# Premium — đối soát QueryDr

QueryDr phục hồi đơn Premium khi không nhận được IPN. Cả BUYER và MERCHANT dùng
cùng bộ xử lý thanh toán/period/entitlement của IPN, không nhận kết quả do FE tự báo.
Giai đoạn này chỉ sandbox; chưa hoàn tiền hoặc mở thanh toán production.

## Cấu hình

Trong ignored `.env` hoặc Railway Variables, cần các khóa VNPay đã dùng cho checkout,
`VNPAY_ENABLED=true`, và:

```properties
VNPAY_QUERY_ENABLED=true
VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_QUERY_IP=<IP máy chủ backend gửi yêu cầu>
```

Query URL phải là endpoint sandbox cố định; không cho client truyền URL/IP để tránh
SSRF và gửi chữ ký tới bên khác. Server IP nhận dạng literal IPv4/IPv6, không nhận DNS.
Mặc định query tắt, IP local 127.0.0.1; không bật hoặc dùng giá trị local thay cho
cấu hình triển khai đã xác nhận với VNPay. Không cần thêm khóa mới ngoài TMN/hash secret.
`.env.example` chỉ có giá trị an toàn. Sửa môi trường cần khởi động lại đúng checkout.
Không cần frontend hay trang kết quả đang mở để worker hoạt động.

## Flow

1. Khi tạo payment attempt, backend thêm job đối soát trong cùng transaction.
2. Nếu IPN đã đưa đơn đến trạng thái cuối, worker dừng job mà không gọi VNPay.
3. Nếu đơn còn chờ, lần tự động đầu tiên là 5 phút sau khi URL thanh toán hết hạn.
4. Worker claim bằng PostgreSQL `FOR UPDATE SKIP LOCKED`, cấp lease 60 giây;
   commit claim rồi mới gọi HTTP VNPay, không giữ transaction trong lúc chờ mạng.
5. Request QueryDr dùng ngày tạo attempt ban đầu (GMT+7), request ID mới và chữ ký.
   HTTP timeout tổng 8 giây, connect timeout 3 giây, tối đa 16 KiB phản hồi,
   không theo redirect và không retry HTTP âm thầm trong một lần claim.
6. Chỉ phản hồi đúng chữ ký/merchant/reference, đúng loại giao dịch thanh toán,
   đúng amount và transaction status thành công mới cấp quyền. API response 00
   chỉ cho biết tra cứu thành công; transaction status 02 vẫn là thanh toán thất bại.
7. Kết quả được commit cùng cấp quyền, receipt source QUERYDR và audit run.
   IPN/QueryDr đến đồng thời chỉ tạo một period. Worker mất lease không được ghi
   kết quả cũ; lỗi giữa lúc xử lý rollback và job sẽ được reclaim khi lease hết hạn.

Scheduler riêng một thread, mỗi sweep xử lý tối đa 5 job, cách nhau một phút.
Retry chờ 5, 10, 20, 40 phút rồi tối đa 60 phút, giới hạn 12 lượt và cửa sổ 48 giờ.
Đơn không tìm thấy, giao dịch chưa hoàn tất, timeout hoặc chữ ký sai không bị coi
là đã thanh toán hoặc thất bại cuối cùng. Hết retry giữ EXHAUSTED để hỗ trợ kiểm tra.
Các lỗi cấu hình provider đã xác minh, sai amount, giao dịch hoàn tiền/đảo/nghi ngờ
chuyển MANUAL_REVIEW hoặc REVIEW_REQUIRED; không tự bỏ quyết định kiểm tra bất thường
của IPN. Chưa có UI/admin thao tác giải quyết những trường hợp này hoặc API hoàn tiền.

## API cho frontend / Swagger

Các API cần Bearer token và chỉ cho phép xem/yêu cầu đơn của chính tài khoản:

- POST `/api/v1/premium/orders/{id}/reconciliation`: không cần body. HTTP 202 nghĩa
  là yêu cầu đã vào worker, không có nghĩa đã trả tiền. Có thể yêu cầu sớm hơn lịch
  tự động sau ít nhất 5 phút từ tạo attempt. Giới hạn yêu cầu mỗi đơn 5 phút/lần,
  tôn trọng backoff của lần tra cứu trước. Chưa bật QueryDr trả 503; yêu cầu quá sớm,
  job đang chạy/hết lượt/cần kiểm tra thủ công trả 429 hoặc conflict.
- GET `/api/v1/premium/orders/{id}/reconciliation`: trả `state`, `attempts`,
  `nextAttemptAt`, `lastResult`. State gồm WAITING, PROCESSING, DONE, EXHAUSTED,
  MANUAL_REVIEW. DONE chỉ nghĩa việc đối soát dừng; không luôn là trả tiền thành công.
- GET `/api/v1/premium/orders/{id}`: FULFILLED là xác nhận tiền và cấp Premium.
  API `/api/v1/premium/me/benefits` và subscriptions hiển thị quyền tương ứng.

Để test Swagger thực: cấu hình khóa sandbox, tạo order và payment attempt, mở URL
VNPay thanh toán, kiểm tra order/benefits. Với đơn vẫn chờ, chờ đủ 5 phút rồi POST
reconciliation, đợi worker và GET lại trạng thái. Không giả dữ liệu thanh toán trên FE.
Tests tự động mô phỏng mất IPN bằng response VNPay ký với secret riêng của tests;
không thay đổi hay vô hiệu hóa endpoint IPN production để tạo tình huống test.

## Dữ liệu và giới hạn

Migration V44 thêm job/run audit, receipt source và backfill các attempt còn pending.
Lịch, số lần retry, lease đều lưu PostgreSQL; không dựa vào RAM cho chống trùng khi
scale nhiều instance. API owner có cooldown trong DB; scheduler giới hạn theo instance,
chưa có hạn mức tổng merchant qua nhiều instance. Cần chọn giới hạn phù hợp khi tăng tải.

Không log JSON request/response, hash secret, signature hoặc URL thanh toán. Có
metrics `lootfast.vnpay.query` và `lootfast.vnpay.query.errors`, log order UUID/kết quả.
Audit run không lưu dữ liệu ngân hàng/khách hàng thô. Có index cho job đến hạn và lịch sử.
Chưa chứng minh tải production hoặc nghiệm thu bằng tài khoản VNPay thật. Còn cần
cảnh báo vận hành, xử lý thủ công/hoàn tiền và thử endpoint sandbox thật trước mở bán.

Đặc tả giao thức: [VNPay QueryDr](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## Kết quả kiểm tra lần triển khai này

- Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`, nhánh `feature/premium-vnpay-ipn`.
- `./mvnw --batch-mode clean verify`: BUILD SUCCESS, 559 tests, không failure/error/skip.
  Lệnh compile, chạy toàn bộ tests và đóng gói backend; không bật server development.
- Java 21.0.12, Boot 3.5.16, Maven Wrapper 3.9.16, Flyway 11.20.3,
  JDBC PostgreSQL 42.7.11, Testcontainers 1.21.4; không thêm dependency.
- Spring test contexts khởi tạo trên PostgreSQL 17.11/18.6, migrations tới V44;
  kiểm tra HTTP thật bằng server giả local, chữ ký độc lập, cooldown/backoff,
  claim đồng thời, QueryDr/IPN race và rollback cấp quyền/job audit.
- PostgreSQL local vẫn 17.11/Flyway V32, Redis image 7-alpine; không migrate database
  development. Backend giữ OFF theo yêu cầu trước; không có URL đang phục vụ hoặc health UP.
- Các biến QueryDr đều có trong `.env` và `.env.example`; query mặc định tắt.
  `.env` vẫn ignored, không có giá trị secret local trong các file thay đổi.
- Chưa commit/push, chưa thử bằng khóa sandbox VNPay thật hoặc chứng minh tải production.

Khi deploy nhiều instance, áp dụng V44 rồi cập nhật toàn bộ writer checkout trước
khi bật worker: code cũ không thêm job cho attempt tạo sau lần backfill của migration.
Nếu có giai đoạn chạy đồng thời code cũ/mới, cần bổ sung backfill cho khoảng thời gian
đó trước khi bật đối soát. Khi rollback code, giữ nguyên schema/audit và tắt worker;
không downgrade migration hoặc xóa dữ liệu thanh toán.
