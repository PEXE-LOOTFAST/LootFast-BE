# Kế hoạch Premium buyer và merchant

Trạng thái: **đã có catalog, subscription foundation, checkout, IPN/cấp quyền và QueryDr sandbox; chưa mở bán thật hoặc có hoàn tiền/email vòng đời**.
Ngày lập: 2026-10-07. Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`.
Nhánh: `feature/merchant-premium-analytics`, HEAD `2da6e24`.

## 1. Hiện trạng trước bước 1 và phạm vi

Đã có quyền admin cấp/thu hồi thủ công trong `premium_entitlements`, kiểm tra thời hạn
ở backend, merchant báo cáo nâng cao, buyer lưu/tìm theo bộ lọc và thông báo theo bộ lọc.
Outbox hiện có phục vụ email Premium theo bộ lọc, chưa có email vòng đời gói.
Chưa có gói bán, phiên bản giá, đơn mua, subscription trả phí, thanh toán VNPay,
đối soát hay lịch sử giao dịch Premium.

Task 075 và nền tảng Premium/checkout đã được merge vào dev. Bước IPN được triển khai riêng trên feature/premium-vnpay-ipn.
Trước khi bắt đầu coding nhóm mới cần lưu phần hiện tại theo quy trình Git và xác nhận
mốc dev đã chứa code phụ thuộc. Không tự commit, chuyển nhánh bỏ thay đổi, merge PR
hay mang migration trùng số từ nhánh Outbox cũ sang.

Runtime hiện tại: Java 21.0.12, Spring Boot 3.5.16, Maven 3.9.16,
Flyway 11.20.3, PostgreSQL JDBC 42.7.11, Testcontainers 1.21.4,
PostgreSQL 17 local/18 Railway, Redis 7. Dùng stack hiện có, không cần BullMQ/Kafka.
Database local V32; source V39. Migration kế tiếp phải kiểm tra dev mới nhất để cấp số
hợp lệ, không tự giả định V40 khi dev đã có người thêm migration.

## 2. Quyết định sản phẩm đang chờ chốt

- Đề xuất hai audience BUYER/MERCHANT độc lập. Tài khoản nhiều role chọn gói cho role
  nào thì chỉ mở quyền cho role đó; chưa có gói bundle.
- Đề xuất kỳ đầu 30 ngày, admin cấu hình giá VND nguyên dương trước khi publish.
  30 ngày khác một tháng lịch. Chưa tự đặt giá production.
- Gia hạn do người dùng chủ động thanh toán; chưa tự trừ tiền định kỳ.
- Merchant phải được duyệt mới mua gói merchant. Buyer phải xác minh; tài khoản khóa
  không tạo checkout mới. Một merchant có role BUYER vẫn có thể mua gói buyer riêng.
- Gói đầu chỉ bán quyền đã có code và đã kiểm tra. Buyer: bộ lọc đã lưu + thông báo
  theo bộ lọc. Merchant: báo cáo nâng cao. Tính năng chưa có không được quảng cáo/mở bán.
- Mẫu deal hiện miễn phí và chưa giới hạn; không tự chuyển sang thu phí hoặc đặt quota
  Free chỉ để tạo quyền lợi Premium. Restock, lịch đăng deal, cảnh báo bán chậm là phần
  phát triển tiếp, cần đặc tả riêng nếu đưa vào gói.
- Chính sách hoàn tiền, xử lý khi tài khoản bị khóa, kỳ gia hạn tối đa và việc mua
  trong thời gian trial cần chốt trước khi mở bán. Đề xuất trial giữ riêng, gói trả phí
  bắt đầu theo đồng hồ subscription trả phí; không tự cộng trial vào thời hạn đã mua.
  Nếu muốn bảo lưu trial còn lại, phải có quy tắc chuyển đổi và audit riêng.

## 3. Task 076: danh mục gói và phiên bản cấu hình

Mỗi gói có code cố định, audience, tên/mô tả, giá VND, durationDays, benefits/limits,
phiên bản điều khoản, trạng thái DRAFT/PUBLISHED/ARCHIVED và audit admin.
Benefit lấy từ danh sách backend đã hỗ trợ, không nhập tùy ý để giả vờ có tính năng.

Bản PUBLISHED bất biến. Sửa giá/quyền lợi tạo revision mới; đơn đã tạo giữ snapshot
revision cũ. ARCHIVED ngừng bán mới, không hủy đơn hợp lệ đang chờ thanh toán hay
quyền của người đã mua. Không hard-delete revision có giao dịch tham chiếu.

API dự kiến: admin tạo draft/sửa draft/publish/archive và xem lịch sử; user GET
`/api/v1/premium/plans?audience=BUYER|MERCHANT` chỉ thấy bản đang bán, không thấy secret.

Nghiệm thu: quyền admin, input hợp lệ, publish phiên bản bất biến, concurrent edit,
archive không làm mất lịch sử, chưa publish thì không tạo đơn mua được.

## 4. Mô hình dữ liệu và vòng đời gói

Tách rõ:

| Bản ghi | Mục đích |
| --- | --- |
| Plan + revision | Cấu hình gói đang bán và các phiên bản bất biến |
| Premium order | Ý định mua/gia hạn, user/audience, snapshot giá/thời hạn/quyền/điều khoản |
| Payment attempt | Một lần khởi tạo VNPay, reference duy nhất, thời hạn URL, kết quả provider |
| Subscription | Một đầu mối subscription trả phí theo `(user, audience)` |
| Subscription period | Kỳ quyền lợi do một đơn đã thanh toán tạo ra, không cấp lại khi callback trùng |
| Payment/audit events | Lịch sử thay đổi, dữ liệu callback được chọn lọc, thao tác admin và đối soát |
| Entitlements | Quyền thực tế theo benefit và khoảng hiệu lực, phân biệt MANUAL và PURCHASE |

Không dùng cờ `isPremium` chung cho tài khoản có nhiều role. Giữ tương thích endpoint
cấp trial và các API benefit cũ. Migration forward-only bổ sung source/order link cho
entitlements; không giả mạo `granted_by` của admin cho một quyền mua từ thanh toán.

Subscription API hiển thị NONE/ACTIVE/EXPIRED/SUSPENDED. NONE có thể không có row.
Trial hiển thị nguồn riêng; khoản mua chưa đến kỳ hiển thị upcomingPeriods, không coi
PENDING_PAYMENT là subscription đang dùng được. SUSPENDED là bị chặn nghiệp vụ,
không có nghĩa tiền đã hoàn hay thời hạn được tạm dừng.

Quyền hiệu lực dựa trực tiếp `startsAt <= now < endsAt` và nguồn chưa bị thu hồi,
không dựa vào cron đã kịp đổi trạng thái hay cờ trong JWT.

Gia hạn cùng audience: `newStart=max(now,currentPaidEnd)`, `newEnd=newStart+duration`.
Ví dụ còn 10 ngày, mua thêm 30 ngày -> còn 40 ngày. Callback xử lý trùng không cộng
thêm lần nữa. Hai đơn khác nhau trả tiền thành công đồng thời được tuần tự hóa bằng
khóa cùng subscription/user và cấp hai kỳ hợp lệ. Revision mới có quyền khác áp dụng
ở kỳ mới; không sửa quyền đã hứa của kỳ hiện tại. Chưa làm upgrade/downgrade/proration.

Đồng hồ dùng Clock UTC; hiển thị ngày và định dạng VNPay theo Việt Nam. Tiền lưu số
VND nguyên, chuyển đổi sang đơn vị VNPay bằng phép tính có kiểm tra overflow, không
float/double. Giới hạn số kỳ/thời gian gia hạn trước để tránh mua không giới hạn.

## 5. Tạo đơn và thanh toán

1. User chọn audience và plan revision đang bán, xem đầy đủ giá/kỳ/quyền/chính sách.
2. POST tạo order với Idempotency-Key. Backend tính tiền từ revision; không lấy amount,
   duration hoặc quyền do FE gửi. Cùng key/cùng payload trả đơn cũ; key khác payload 409.
3. Backend lưu snapshot rồi mới tạo URL có chữ ký. FE nhận order UUID, hạn thanh toán,
   payment URL. Đề xuất URL có hiệu lực 15 phút; TTL cuối cùng kiểm tra với cấu hình
   VNPay cấp cho dự án, không coi 15 phút là chính sách provider cố định.
4. Một order chỉ có một attempt còn sử dụng được tại một thời điểm. Retry HTTP trả lại
   attempt cũ; tạo attempt mới chỉ sau khi xác định attempt trước không còn pending.
5. Người dùng thanh toán trên VNPay. Đơn meal hiện tại/VietQR trực tiếp cửa hàng giữ
   flow riêng; đơn Premium thanh toán cho đơn vị vận hành LootFast qua cấu hình VNPay.
6. IPN hợp lệ cập nhật payment, order, period, entitlements và enqueue thông báo trong
   một transaction. ACK thành công chỉ sau commit. Không gọi Brevo trong transaction.
7. FE chuyển về trang kết quả và GET order thuộc user để hiển thị kết quả backend.

Order dự kiến: PENDING_PAYMENT/FULFILLED/FAILED/CANCELLED/EXPIRED/REVIEW_REQUIRED.
Payment attempt giữ trạng thái riêng, có UNKNOWN/RECONCILING và các trạng thái tiền
như SUCCEEDED/FAILED/REFUND_PENDING/REFUNDED. Hết URL hoặc người dùng đóng browser
không chứng minh tiền thất bại. Không đánh dấu EXPIRED rồi bỏ qua một xác nhận thu tiền
hợp lệ đến muộn; phải đối soát và cấp đúng một lần hoặc đưa vào xử lý hoàn tiền.
Một order nhận hai khoản tiền thực sự: không cấp hai lần, lưu khoản thứ hai và mở case
đối soát/hoàn tiền. Không âm thầm bỏ khoản tiền đó.

## 6. VNPay IPN, ReturnURL và đối soát

- IPN public vì VNPay không có JWT của khách. Xác minh HMAC SHA-512 theo canonical
  encoding VNPay, so sánh an toàn; giới hạn request size, reject query parameter trùng
  và input sai định dạng. Kiểm tra merchant code, reference, amount, trạng thái thành
  công và provider transaction ID; không tin một query `success=true` từ FE.
- Reference unique ở DB, provider transaction identity có constraint; cấp quyền unique
  theo order/benefit và period unique theo order. Callback trùng/đảo thứ tự không giảm
  trạng thái thành công và không gia hạn lại. Callback mâu thuẫn ghi event/case review.
- Trả RspCode/Message đúng giao thức VNPay; endpoint này không dùng envelope ApiResponse
  cho phản hồi provider. Unknown error/DB failure không ACK như đã xử lý thành công.
- ReturnURL kiểm tra input, đưa user về URL FE được cấu hình/allowlist với order UUID;
  không dùng return URL tùy ý của client, không chuyển toàn bộ callback nhạy cảm lên FE.
  Không cấp quyền tại ReturnURL. FE có thể thấy “đang xác nhận” và poll có giới hạn.
- Worker đối soát dùng API QueryDr, xác minh cả response signature và transaction status.
  Khóa/claim DB chia việc nhiều instance; network call ngoài transaction, timeout,
  retry/backoff có jitter, quota, giới hạn số attempt và dead-letter/review khi quá hạn.
  IPN và QueryDr dùng cùng hàm xác nhận thanh toán để không có hai bộ quy tắc.
- Network timeout kết quả không rõ -> đối soát, không kết luận FAILED. QueryDr không
  còn trả kết quả/không thống nhất -> case review, không tạo thanh toán thử vô hạn.
- Audit chỉ giữ field cần đối soát, request fingerprint và kết quả xử lý; không log
  secret, full signed URL, token hay dữ liệu ngân hàng không cần thiết.

## 7. Lịch sử, chính sách, hoàn tiền

User GET danh sách orders/payments phân trang và chỉ truy cập dữ liệu của mình;
hiển thị snapshot tên gói, audience, số tiền, thời điểm, trạng thái và kỳ đã cấp.
Admin có lịch sử/audit và danh sách giao dịch cần đối soát. Không dùng API confirm
thủ công của tiền meal để tự nhận đã thanh toán Premium.

Không tự chốt điều khoản “không hoàn tiền”. Nội dung/version phải được xác nhận trước
mở bán và được lưu cùng order. Chính sách phí, hoàn tiền khi thu trùng, giao dịch lỗi,
khóa tài khoản và yêu cầu hoàn tiền cần rõ ràng. Nếu làm refund API: quyền admin riêng,
reason bắt buộc, idempotency key, unique refund reference, kiểm tra số tiền hoàn còn lại,
trạng thái REFUND_PENDING trước khi biết kết quả, đối soát retry. Thu hồi/điều chỉnh
quyền của order theo chính sách đã chốt, không ghi đè hoặc xóa payment gốc.

Báo cáo doanh thu Premium dùng giao dịch Premium đã xác nhận, tách riêng gross/refund/net.
Đây mới là tiền bán gói của nền tảng; không gộp với giá trị đơn món của task 075.

## 8. Hết hạn, dữ liệu và thông báo

Buyer: giữ bộ lọc và lịch sử; được xem/xóa/tắt thông báo. Không tạo/sửa/tìm bằng bộ lọc
Premium và không gửi thông báo Premium khi không có quyền. Mua lại khôi phục quyền,
không tự bật setting người dùng đã tắt. Không gửi bù deal trong thời gian mất quyền;
điều kiện event-time/resume cutoff và recheck ở worker phải xử lý backlog đúng.

Merchant: giữ deal, mẫu, đơn và báo cáo nguồn. Báo cáo cơ bản còn dùng được; nâng cao
bị khóa khi quyền hết hạn. Không xóa giao dịch hay ảnh hưởng đơn khách đang đặt.
Nếu sau này có lịch đăng tự động: dừng job chưa thực thi khi hết quyền, không gỡ deal
đã đăng; vẫn cần đặc tả số lượng/ngày và chống trùng từng cửa hàng/ngày.

Thông báo mua/gia hạn thành công, sắp hết hạn và đã hết hạn qua in-app và Outbox.
Đề xuất nhắc trước 3 ngày và 1 ngày, mỗi period/type chỉ tạo một notification.
Tôn trọng preference/email của user, recheck kỳ đã gia hạn trước gửi nhắc cũ.
Email thất bại không rollback quyền đã mua; worker không quyết định quyền còn hạn.
Cần mở rộng category/template Outbox vì hiện chỉ có category thông báo deal/đơn món.

## 9. API dự kiến

- GET `/api/v1/premium/plans?audience=...`
- GET `/api/v1/premium/me/subscriptions`, `/me/benefits?audience=...`
- POST `/api/v1/premium/orders`; GET `/orders`, `/orders/{id}` (owned).
- POST `/api/v1/premium/orders/{id}/payment-attempts`; thao tác hủy chỉ cho order chưa
  thu tiền xác nhận, không phải nút hủy gói đang trả phí.
- GET `/api/v1/payments/vnpay/ipn`, `/vnpay/return` theo tích hợp PAY.
- Admin endpoints dưới `/api/v1/admin/premium/...` cho cấu hình, audit/reconcile/refund.
- Giữ các benefit API buyer/merchant hiện tại để frontend không phải đổi cùng lúc.

Đường dẫn cuối cùng chốt sau khi kiểm tra naming và DTO hiện có ở từng bước.

## 10. Thứ tự triển khai và nghiệm thu

| Bước | Nhóm task | Kết quả kiểm chứng được |
| --- | --- | --- |
| 1 | 076 | Gói/revision/admin/public catalog; chưa cần VNPay key |
| 2 | 093+094+096+097+102+105 nền tảng, 079... nền tảng | Subscription/period/entitlement source, rule gia hạn và API xem quyền; chưa mở mua thật |
| 3 | 085+087+088+103 | Order snapshot, idempotency, attempt và URL VNPay sandbox |
| 4 | 089+090+091+092 | IPN xác minh, cấp quyền atomic, chống trùng; ReturnURL không cấp quyền |
| 5 | 086+095+100+101+104 và lỗi thanh toán | QueryDr, reconcile, lịch sử/chính sách, case thu trùng/hoàn tiền đã chốt |
| 6 | Hoàn thiện 093... và 083+098 | Expiry, gia hạn đồng thời, dữ liệu giữ lại, thông báo Outbox, restore không gửi bù |
| 7 | Hoàn thiện 079+080+081+082+084+099 | Tất cả quyền gói công bố, limit và compatibility; không đánh Done quyền chưa triển khai |
| 8 | Nghiệm thu E2E (task 106 trước đó) | Sandbox thật, tiền -> quyền hai audience, lỗi/mất callback, hồi phục worker; chuẩn bị production |

Nếu giữ quyền lợi v1 chỉ gồm tính năng hiện có, bước 7 chủ yếu nối paid source vào
các kiểm tra hiện tại. Nếu thêm restock/lịch đăng/cảnh báo bán chậm, chia thành task
riêng rồi publish revision mới, không để gói đang bán hứa tính năng chưa có.

Mỗi bước inspect/explain trước code, migration mới và test/Swagger riêng; chạy full
`./mvnw --batch-mode clean verify`, review diff/secret/concurrency. Test bắt buộc:
amount/signature/merchant/reference sai; IPN trùng và đồng thời; một providerTxn gắn
hai order; hai đơn gia hạn thành công; callback đến sau TTL/cancel; IPN/QueryDr race;
admin đổi giá/archive trong checkout; timeout/DB lỗi/crash sau commit trước ACK;
khóa user sau tạo order; hết hạn đúng biên; trial + paid; nhiều role; dữ liệu/setting
sau hết hạn; email retry không cấp quyền lại; hoàn tiền trùng/nhiều lần nếu thuộc scope.
Không dùng endpoint giả thanh toán production. Mock/test signature chỉ dùng trong test.
Load test và mục tiêu latency/traffic cần xác định trước khi tuyên bố capacity.

## 11. Cấu hình và điều kiện mở bán

Đề xuất VNPAY_ENABLED mặc định false. Khi false, backend/đơn món vẫn start được nếu
chưa có VNPay key; catalog/quyền hiện có vẫn đọc được, checkout báo provider unavailable.
Khi true, kiểm tra cấu hình đầy đủ, thông báo lỗi có tên biến thiếu nhưng không có giá trị.
Cần TMN_CODE, HASH_SECRET, PAYMENT_URL, QUERY_URL, backend RETURN_URL/IPN URL và URL FE
cho màn hình kết quả. IPN HTTPS public đăng ký với VNPay, không dùng localhost để nhận
callback từ máy chủ VNPay. Sandbox local dùng public tunnel cho thử nghiệm; production
sử dụng domain backend ổn định. Client không được truyền tùy ý các URL này.

Tên env cụ thể chốt khi implement; thêm/sửa đồng thời `.env` ignored và `.env.example`
placeholder, secrets production trong Railway Variables, không trong Git.
Sandbox/production config và giao dịch tách biệt. Cần cấu hình và thủ tục merchant
production được VNPay chấp nhận; code pass test không đồng nghĩa đã được mở thanh toán thật.
Không thêm biến VNPay ngay trong bước kế hoạch này.

## 12. Nguồn giao thức chính thức

- [VNPay PAY: tạo URL, IPN và ReturnURL](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- [VNPay QueryDr và Refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

Các quy tắc giá, số ngày, role, quyền lợi, giới hạn, gia hạn và hoàn tiền trong tài liệu
là đề xuất sản phẩm LootFast, không phải chính sách mặc định của VNPay.

## Tiến độ thực hiện

2026-10-07: Task 076 có code catalog local, admin quản lý draft/revision/publish/archive,
public catalog và audit; full verify 429 tests pass. Xem [flow Swagger](premium-plan-catalog.md).
Catalog chưa mở checkout, chưa tạo subscription trả phí. Giá do admin nhập; chỉ chọn
quyền lợi đã có, không cấu hình quota runtime tùy ý ở bước này. Chưa commit/push,
backend vẫn OFF, local schema V32, source V40. Tại thời điểm này, các bước 2–8 chưa triển khai.

2026-10-07: Bước 2 đã có nền tảng subscription/period, source MANUAL/PURCHASE,
quy tắc gia hạn, API đọc quyền và lịch sử của chính tài khoản; V41. Full verify
461 tests pass (32 test mới gồm unit và integration PostgreSQL 17/18). Đã sửa
isolation database H2 giữa các Spring context. Xem [flow đọc và giới hạn](premium-subscription-foundation.md).
Chưa có checkout/IPN, refund hoặc thông báo vòng đời; không đánh Done toàn nhóm
093+094+096+097+102+105 ở thời điểm này. Bước tiếp theo là 085+087+088+103:
đơn Premium, snapshot/consent, idempotency, payment attempt và URL VNPay.
Backend vẫn OFF theo yêu cầu; local DB V32 không đổi, chưa commit/push.

2026-10-07: Bước 3 có API tạo/xem order owned, snapshot/consent, idempotency,
one attempt/order, URL VNPay sandbox HMAC-SHA512 và audit; V42. Full verify
489 tests pass. [Flow Swagger và cấu hình](premium-checkout.md). Đã cập nhật
.env ignored và .env.example, VNPay tắt mặc định; không thêm dependency.
Chưa có IPN/QueryDr/return handler hoặc fulfill; production checkout bị chặn ở
giai đoạn này. Chưa commit/push; backend OFF, local DB V32. Bước tiếp theo
089+090+091+092: xác minh IPN và atomic fulfillment, liên kết order/period,
callback trùng/đến muộn và ReturnURL không cấp quyền. Frontend/kênh bán/CH Play
chưa chốt; backend sandbox không tự cho phép một luồng Android thanh toán ngoài.

## Bước 4 — IPN và atomic fulfillment

Đã có xác minh callback HMAC-SHA512, liên kết order/receipt/period, khóa và constraint
chống cấp trùng, ReturnURL không cấp quyền. Xem [premium-vnpay-ipn.md](premium-vnpay-ipn.md).
QueryDr, hoàn tiền, email vòng đời và nghiệm thu với VNPay thật vẫn còn ở bước sau.
Các ghi chú bước 1–3 phía trên là trạng thái tại thời điểm triển khai từng bước.

## Bước tiếp theo — QueryDr sandbox

Đã bổ sung client xác minh response, job PostgreSQL có lease/retry/backoff, API owner
yêu cầu/xem đối soát và dùng chung fulfillment IPN. Chi tiết [premium-querydr.md](premium-querydr.md).
Chưa giải quyết hoàn tiền, review thủ công, lifecycle email hoặc E2E với VNPay thật.
