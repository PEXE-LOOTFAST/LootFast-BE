> Cập nhật: đã bổ sung QueryDr sandbox để phục hồi IPN mất.
> Xem [premium-querydr.md](premium-querydr.md). Ghi chú dưới mô tả phạm vi bước IPN trước đó.

# Premium: VNPay IPN và trang kết quả

Đã triển khai xác nhận thanh toán sandbox cho cả gói BUYER và MERCHANT. Backend chỉ
cấp quyền từ IPN có chữ ký hợp lệ; dữ liệu frontend báo thành công không có hiệu lực.
Chưa mở thanh toán production, chưa có QueryDr/hoàn tiền và email vòng đời gói.

## Cấu hình

Giữ `VNPAY_ENABLED=false` khi chưa có khóa sandbox; backend vẫn khởi tạo được.
Khi thử thanh toán thật trên sandbox, cấu hình trong `.env` hoặc Railway Variables:

- `VNPAY_ENABLED=true`.
- `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`: thông tin VNPay cấp, không đưa lên frontend/Git.
- `VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- `VNPAY_RETURN_URL=https://<backend-domain>/<context-if-any>/api/v1/payments/vnpay/return`.
- `VNPAY_FE_RESULT_URL=https://<frontend-domain>/premium/payment-result`: URL cố định,
  không có query/fragment. Local frontend được dùng HTTP localhost. Nếu bỏ trống,
  ReturnURL trả JSON để debug thay vì redirect.

Đăng ký IPN HTTPS public với VNPay tại
`https://<backend-domain>/<context-if-any>/api/v1/payments/vnpay/ipn`.
IPN URL không phải biến môi trường: đây là route backend đã cố định và địa chỉ đăng ký
với nhà cung cấp. Localhost không nhận được callback từ VNPay server.
Các biến được đọc khi Spring khởi động; sửa `.env` cần khởi động lại đúng checkout.

## Backend xử lý

GET `/api/v1/payments/vnpay/ipn` không yêu cầu JWT nhưng xác minh HMAC-SHA512 trước
khi truy cập dữ liệu đơn. Kiểm tra merchant, reference, số tiền VND nhân 100, trạng
thái và mã giao dịch. Giới hạn query 8192 ký tự, 24 tham số, không nhận tham số lặp.

Success yêu cầu đồng thời `vnp_ResponseCode=00` và `vnp_TransactionStatus=00`.
Một transaction PostgreSQL ghi receipt, liên kết order với period, cấp entitlements
và chuyển order sang FULFILLED. Callback chỉ nhận ACK sau commit; lỗi giữa chừng
rollback toàn bộ để lần retry có thể thực hiện lại. Khóa user rồi order đồng nhất
với checkout; unique constraint bảo vệ callback trùng và mã giao dịch dùng cho hai đơn,
kể cả khi có nhiều instance backend.

- `RspCode=00`: đã ghi nhận callback (có thể thành công, thất bại hoặc cần đối soát).
  Đây không phải trạng thái thanh toán hiển thị cho khách.
- `02`: callback đã xử lý, không cấp thêm quyền.
- `01`: không tìm thấy đơn; `04`: sai số tiền; `97`: chữ ký/merchant không hợp lệ.
- `99`: dữ liệu không hợp lệ, giao dịch chưa kết thúc hoặc lỗi xử lý; cho phép retry.

Thanh toán hợp lệ đến sau TTL vẫn được ghi nhận và cấp đủ thời hạn từ lúc xử lý.
Thời điểm ngân hàng thanh toán được lưu riêng; VNPay dùng độ chính xác giây nên
không so sánh sai với timestamp microsecond của checkout.
Tài khoản bị khóa vẫn được ghi nhận tiền, nhưng quyền truy cập vẫn bị chặn.
Giao dịch nghi ngờ, đảo giao dịch, hai mã thanh toán cho một đơn hoặc một mã dùng
cho hai đơn được lưu REVIEW_REQUIRED để xử lý tiếp. Không tự hủy quyền đã cấp khi
callback thất bại cũ đến sau thành công; hoàn tiền cần quy trình riêng.

Migration V43 bổ sung receipts, định danh giao dịch và liên kết period-order.
Các period legacy từ nền tảng subscription trước IPN giữ liên kết nullable.
Không sửa migration đã áp dụng và không xóa dữ liệu cũ.

## Flow frontend / Swagger

1. Đăng nhập, lấy catalog và tạo Premium order theo `premium-checkout.md`.
2. POST payment-attempts, mở `paymentUrl` trên trình duyệt để thanh toán sandbox.
3. VNPay gọi IPN tới backend; browser được đưa tới backend ReturnURL.
4. ReturnURL chỉ kiểm tra callback và trả `orderId`. Nếu cấu hình FE URL thì trả
   HTTP 303 tới trang kết quả kèm `?orderId=<UUID>`; callback không hợp lệ có
   `?error=invalid_callback`. Không chuyển tiếp chữ ký hoặc dữ liệu ngân hàng.
5. FE dùng token đăng nhập gọi GET `/api/v1/premium/orders/{orderId}`. Chỉ
   FULFILLED mới hiển thị đã xác nhận. Nếu PENDING_PAYMENT hoặc
   AWAITING_RECONCILIATION, hiển thị đang xác nhận, poll có giới hạn rồi cho thử lại.
   FAILED/REVIEW_REQUIRED cần hiển thị trạng thái tương ứng, không tự báo thành công.
6. GET API subscription/benefits để cập nhật quyền. Với Swagger, kiểm tra order,
   period và quyền sau khi thanh toán sandbox; không gọi IPN bằng dữ liệu tự nghĩ ra.

Nếu chưa cấu hình FE URL, ReturnURL JSON có status AWAITING_CONFIRMATION nghĩa là
callback browser hợp lệ; không chứng minh IPN đã hoàn tất. ReturnURL tuyệt đối không
cấp Premium. API xem order vẫn kiểm tra owner nên UUID không thay thế xác thực.

## Kiểm chứng và giới hạn

Integration tests chạy migrations đến V43 trên PostgreSQL 17 và 18: callback trùng,
callback đồng thời, tranh chấp mã giao dịch giữa hai user, gia hạn, sai amount/hash,
callback đến muộn, khóa tài khoản, đảo giao dịch và rollback sau khi tạo period.
Unit tests kiểm tra encode/chữ ký, ngày không hợp lệ, request quá lớn và redirect.
Không log query callback, URL thanh toán, khóa hoặc dữ liệu ngân hàng; metric
`lootfast.vnpay.ipn` dùng nhãn ACK có số lượng giá trị hữu hạn.

Chưa chứng minh tải production hoặc thanh toán end-to-end với tài khoản VNPay thật.
Cần QueryDr/đối soát để phục hồi IPN mất, chính sách hoàn tiền, cảnh báo trường hợp
REVIEW_REQUIRED, giới hạn tải ở ingress và nghiệm thu trước khi mở bán.
Flow mua trong ứng dụng Android/CH Play chưa được quyết định ở bước backend này.

Tham khảo: [Tài liệu tích hợp VNPay PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

## Kết quả kiểm tra checkout này

- Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`.
- Nhánh: `feature/premium-vnpay-ipn`.
- `./mvnw --batch-mode clean verify`: BUILD SUCCESS, 525 tests, không lỗi/skip.
  Lệnh này compile, chạy tests và đóng gói; không bật development server.
- Runtime tests: Java 21.0.12, Spring Boot 3.5.16, Maven Wrapper 3.9.16,
  Flyway 11.20.3, PostgreSQL JDBC 42.7.11, Testcontainers 1.21.4.
  PostgreSQL 17.11 và 18.6 đã khởi tạo Spring context, áp dụng migrations tới V43.
  Redis local giữ image 7-alpine; không thay dependency/image của task này.
- Backend local vẫn OFF theo yêu cầu trước đó, không có process nghe cổng 8080;
  không có kết quả health UP hoặc URL server đang chạy.
- Database development `lootfast_reports_dev` vẫn PostgreSQL 17.11/Flyway V32;
  tests dùng database riêng, không migrate/xóa database development.
- Chưa commit/push phần IPN; chưa thử end-to-end với khóa VNPay thật.
