> Cập nhật: IPN/ReturnURL và cấp quyền sandbox đã được bổ sung ở bước tiếp theo.
> Xem [premium-vnpay-ipn.md](premium-vnpay-ipn.md). Phần dưới mô tả phạm vi bước checkout ban đầu.

# Premium checkout — 085+087+088+103

Bước 3: tạo đơn Premium, lưu consent/snapshot, khởi tạo URL VNPay **sandbox**.
Không có IPN, QueryDr, ReturnURL handler hay cấp quyền từ thanh toán ở bước này.
Chưa mở thu tiền thật. Frontend/kênh bán và chính sách CH Play tiếp tục được chốt riêng;
backend hiện không tuyên bố một nút/link thanh toán Android đã tuân thủ Google Play.

## API và flow Swagger

1. Admin publish gói theo [catalog](premium-plan-catalog.md); cần điều khoản đã publish.
2. Buyer/merchant đăng nhập và Authorize access token. Merchant phải được duyệt;
   tài khoản xác minh, không khóa và có role đúng audience của gói.
3. `GET /api/v1/premium/plans?audience=BUYER` hoặc MERCHANT. Ghi lại revision `id`
   và `termsDocumentId`; website phải hiển thị đầy đủ gói/điều khoản trước consent.
4. `POST /api/v1/premium/orders`, header `Idempotency-Key` là một UUID mới:

```json
{
  "planRevisionId": "UUID-revision-dang-ban",
  "termsDocumentId": "UUID-dieu-khoan-cua-revision",
  "acceptTerms": true
}
```

Backend tính giá, thời hạn/quyền từ catalog. Consent thiếu/sai version bị từ chối.
Cùng key + payload trả lại order cũ, kể cả catalog đã archive revision; key khác
payload trả 409. Client không được đổi price/duration/benefits bằng request.

5. Lưu `data.id`; `GET /api/v1/premium/orders/{id}` để xem đơn. Danh sách owned:
   `GET /api/v1/premium/orders?page=0&size=20` (size 1–50).
6. `POST /api/v1/premium/orders/{id}/payment-attempts` không cần body. Khi VNPay
   tắt trả 503 PAYMENT_NOT_CONFIGURED; order vẫn giữ nguyên, chưa có payment attempt.
   Khi bật sandbox hợp lệ, trả `paymentUrl` để mở trên trình duyệt, reference và hạn.
   Bấm lại trong hạn trả **chính URL/attempt cũ**, không sinh reference mới.
7. Theo dõi đơn bằng GET. Chưa có xử lý IPN nên bước này không chuyển thành FULFILLED,
   không gia hạn subscription, không ghi entitlement và không gửi email thành công.
   Không dùng redirect từ VNPay hoặc frontend tự khai "success" để cấp quyền.

## Trạng thái và giới hạn

Order có thời hạn 15 phút (quy tắc LootFast, cần đối chiếu cấu hình merchant VNPay).
GET trả EXPIRED khi hết hạn mà chưa có attempt; nếu từng có attempt thì trả
AWAITING_RECONCILIATION. Đây là trạng thái API suy ra theo thời gian; DB vẫn lưu
PENDING_PAYMENT để không mất ý định/receipt cần đối soát. Không có worker đối soát
ở bước này. Hết URL không chứng minh tiền thất bại; callback đến muộn sẽ xử lý
ở bước IPN/QueryDr, không được bỏ qua chỉ vì expiresAt đã qua.

Một user/audience có tối đa một order PENDING_PAYMENT chưa hết hạn. Tối đa 10 order
mới/user/giờ; retry cùng key không tính thêm. Khóa row user trong PostgreSQL tuần tự
hóa create và quota trên nhiều backend instance; khóa order và unique order_id ở
attempt chống hai URL cho một order. Một order chỉ có một attempt trong v1; tạo
attempt mới sau timeout bị từ chối, chờ thiết kế đối soát thay vì thử thanh toán lại.

Giá, tên, quyền, duration, terms và version lưu snapshot JSONB ở order. Publish giá
mới/archive không làm thay đổi đơn đã tạo. Event CREATED/PAYMENT_INITIATED lưu cùng
transaction với order/attempt. FK không cascade xóa lịch sử thanh toán. History có
index owner/createdAt; pagination hữu hạn; lỗi truy cập đơn người khác trả 404.

URL có chữ ký được lưu để retry đúng URL cũ khi config thay đổi; không log URL, hash
secret hay thông tin ngân hàng. Order GET/list không trả paymentUrl; chỉ endpoint
khởi tạo thanh toán trả nó. Payment DTO toString đã ẩn URL. IP lấy từ request server,
không nhận qua JSON; deployment phải cấu hình proxy tin cậy cho forwarded headers.

## Cấu hình

Đã thêm đồng thời `.env` ignored và `.env.example` placeholder, mặc định:

```properties
VNPAY_ENABLED=false
VNPAY_TMN_CODE=
VNPAY_HASH_SECRET=
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=
```

Thiếu key khi false không ngăn Spring context khởi tạo. Khi true kiểm tra mã merchant
8 ký tự, secret không rỗng, payment URL đúng sandbox và Return URL HTTPS cố định,
không userinfo/query/fragment, tối đa 255 ký tự ASCII. URL return do server cấu hình;
không nhận URL bất kỳ từ người dùng. Sandbox có thể tạm return về trang test do mình
quản lý; endpoint return backend sẽ có ở bước 4. Chưa thêm IPN_URL/QUERY_URL/FE_URL
vì chưa dùng; IPN sẽ cần đăng ký địa chỉ HTTPS public với VNPay.

Không điền key production, không lấy key giả từ test. Đăng ký sandbox với VNPay,
điền key vào `.env` local/Railway Variables. Backend cần khởi động lại để nhận env.
Ứng dụng sandbox không tự đại diện cho việc được phép thu tiền production.

Dùng Java JCA HMAC-SHA512/URLEncoder hiện có, không thêm dependency. VNPay version
2.1.0; amount là số VND nguyên nhân 100; thời gian GMT+7; reference UUID bỏ dấu gạch,
unique ở DB. Test chữ ký dùng vector độc lập tính bằng Python chuẩn, không gọi lại
hàm signer để tính expected.
Nguồn giao thức: [VNPay PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

## Giới hạn và bước tiếp theo

Chưa có fulfill/refund/cancel public API, xác minh receipt, QueryDr và email vòng đời.
Không thêm FK từ period.purchase_reference đến order ở bước này: primitive subscription
hiện chưa có caller runtime, test foundation dùng purchase UUID độc lập. Bước IPN phải
liên kết order và kỳ bằng ràng buộc, kiểm tra authenticity/amount/merchant/provider
transaction, atomic fulfillment và callback trùng. Giao dịch thành công đến sau khi
user khóa phải được lưu/xử lý đúng; không áp quy tắc "không checkout mới" cho receipt
đã thu tiền. Cần chốt refund/cap gia hạn trước khi mở bán production.

Config production checkout hiện bị chặn đến khi IPN hoàn thiện; đây là giới hạn rõ
ràng của giai đoạn 3. Chưa load test, không khẳng định capacity production. Code mới
trong `premium/checkout`; migration V42 mới, không sửa migration cũ; không commit/push.
Checkout `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`, branch
`feature/merchant-premium-analytics`, HEAD gốc `2da6e24`.
Backend local giữ OFF theo yêu cầu trước đó; không có health HTTP UP để báo.

## Kết quả kiểm chứng 2026-10-07

`./mvnw --batch-mode clean verify`: BUILD SUCCESS lúc 17:59:10 +07:00;
489 tests, 0 failure/error/skip. Trong đó 6 unit tests signer/config và 11 integration
tests mỗi PostgreSQL 17.11/18.6. Spring context + Flyway V42 khởi tạo thành công
trên container riêng; OpenAPI API mới trả thành công bằng MockMvc. Log:
`/tmp/lootfast-checkout-final-verify.log`.

Java 21.0.12, Boot 3.5.16, Maven Wrapper 3.9.16, Flyway 11.20.3, JDBC 42.7.11,
Testcontainers 1.21.4; Docker PostgreSQL 17/Redis 7 không thay đổi. Database local
`lootfast_reports_dev` vẫn PostgreSQL 17.11/Flyway V32; source V42. Không migrate
local hoặc chạy persistent backend; không có URL local đang phục vụ để báo UP.
