# Task 075 — Báo cáo toàn hệ thống

API: `GET /api/v1/admin/reports/overview?from=2030-01-02&to=2030-01-31`.
Chỉ ADMIN đã xác minh, chưa khóa được xem. Không yêu cầu Premium.
`from` và `to` bắt buộc, định dạng `yyyy-MM-dd`, bao gồm cả hai ngày, tối đa 366 ngày,
trong các năm 1–9998. Múi giờ kinh doanh: `Asia/Ho_Chi_Minh`.
Response dùng envelope `ApiResponse`, nội dung báo cáo trong `data`.

## Ý nghĩa số liệu

| Field | Quy tắc |
| --- | --- |
| `asOf` | Thời điểm đánh giá trạng thái đang mở bán, lấy một lần cho báo cáo. Dữ liệu đọc trong cùng snapshot database. |
| `accounts.totalAccounts` | Tổng số tài khoản hiện còn trong hệ thống, gồm cả admin, chưa xác minh và tài khoản khóa. |
| `accounts.buyerAccounts/merchantAccounts/adminAccounts` | Số tài khoản có từng role hiện tại. Một tài khoản có nhiều role được tính ở từng nhóm, không cộng các nhóm để suy ra tổng tài khoản. |
| `accounts.lockedAccounts` | Số tài khoản đang khóa. |
| `accounts.newAccounts` | Tài khoản tạo trong khoảng ngày chọn. `newBuyerAccounts/newMerchantAccounts` là role hiện tại của các tài khoản đó; không phải lịch sử ngày cấp role. |
| `stores.totalStores/approvedStores` | Hồ sơ cửa hàng hiện tại / hồ sơ có đơn đăng ký APPROVED hiện tại, kể cả cửa hàng đang khóa hoặc đình chỉ. |
| `stores.pendingApplications` | Đơn đăng ký merchant đang PENDING, gồm cả người chưa được cấp role MERCHANT; UPLOADING không tính là chờ duyệt. |
| `deals.createdDeals` | Số bản ghi deal tạo trong khoảng ngày, gồm DRAFT. Chưa có timestamp lần xuất bản độc lập nên không gọi đây là số deal xuất bản trong kỳ. |
| `deals.createdByCurrentStatus` | Các deal tạo trong kỳ, phân nhóm theo trạng thái hiện tại. |
| `deals.currentByStatus` | Tất cả deal hiện tại theo DRAFT/ACTIVE/HIDDEN/SOLD_OUT/REMOVED, không phụ thuộc khoảng ngày. |
| `deals.openForSaleNow` | ACTIVE, còn phần ăn, đã đến giờ mở bán, chưa kết thúc bán/nhận; chủ cửa hàng không khóa và cửa hàng không đình chỉ tại `asOf`. Khác với tổng ACTIVE, có thể có deal ACTIVE chưa đến giờ hoặc đã hết giờ. |
| `placedOrders/ordersByStatus` | Đơn tạo trong khoảng ngày, nhóm theo trạng thái hiện tại. |
| `completedOrders` | Đơn COMPLETED có `finalized_at` trong khoảng ngày, gồm cả đơn chưa xác nhận thanh toán. |
| `completedTransactionValue` | Tổng `total_amount` snapshot của đơn COMPLETED + CONFIRMED, theo ngày hoàn tất. Đây là giá trị giao dịch cửa hàng, **không phải doanh thu LootFast**. |
| `discountedPortions/customerSavings` | Tổng quantity / `(original_unit_price-unit_price)*quantity` từ snapshot đơn COMPLETED trong khoảng ngày. Không đọc giá deal hiện tại để tính lại lịch sử. |
| `daily` | Theo từng ngày Việt Nam: đơn tạo, đơn hoàn thành, giá trị giao dịch, phần ăn giảm giá, tiền tiết kiệm. Ngày trống trả đủ field bằng 0. |
| `topStores` | Tối đa 10 cửa hàng có đơn hoàn thành trong kỳ, xếp giá trị giao dịch giảm dần, rồi số đơn hoàn thành giảm dần, rồi UUID tăng dần. `storeId` là UUID tài khoản chủ cửa hàng, tương thích API public store. Tên cửa hàng là tên hiện tại. |

Đơn tạo hôm trước và giao hôm nay có thể làm `completedOrders` lớn hơn `placedOrders`
trong một ngày. Không tính tỷ lệ hoàn thành bằng cách chia hai số này.
Đơn trả tiền nhưng CANCELLED/NO_SHOW/MERCHANT_CANCELLED không được cộng vào giá trị
đơn đã giao. Đây không phải báo cáo đối soát tiền, hoàn tiền hay sổ kế toán.
Lịch sử giao dịch không bị loại chỉ vì cửa hàng/deal hiện bị khóa, ẩn hoặc gỡ.
Không có doanh thu Premium trong báo cáo vì luồng mua gói chưa được triển khai.

## Luồng test Swagger

1. Đăng nhập tài khoản ADMIN, dùng access token trong **Authorize**.
2. Mở nhóm **Admin reports**, gọi `GET /api/v1/admin/reports/overview`.
3. Chọn `from`/`to` là ngày có dữ liệu local hoặc ngày hiện tại. Không nhập ngày 2030
   của ví dụ nếu dữ liệu của bạn ở năm 2026.
4. Kiểm tra các tổng tài khoản/cửa hàng/deal và danh sách `daily` có đủ ngày.
5. Với merchant, đăng một deal và buyer đặt đơn; admin gọi lại báo cáo:
   `placedOrders` tăng, giá trị giao dịch và số phần đã nhận chưa tăng.
6. CASH: merchant xác nhận giao và thu tiền bằng pickup credential theo flow đơn hiện có.
   BANK_TRANSFER: xác nhận thanh toán trước, sau đó xác nhận giao bằng pickup credential.
   Gọi lại báo cáo theo ngày hoàn tất: `completedOrders`, giá trị giao dịch, phần ăn
   giảm giá và tiết kiệm tăng. Chỉ xác nhận thanh toán mà chưa giao chưa cộng số tiền.
7. Đổi token BUYER/MERCHANT: HTTP 403. Không token: HTTP 401.
8. Đảo `from > to`, bỏ ngày, ngày sai định dạng hoặc hơn 366 ngày: HTTP 400.
9. Quá 10 lượt hợp lệ/admin trong cửa sổ 60 giây: HTTP 429 `REPORT_RATE_LIMITED`.
   Cửa sổ bắt đầu ở request đầu, không phải phút trên đồng hồ. Redis không dùng được:
   HTTP 409 `RESOURCE_BUSY`; không chạy các truy vấn tốn chi phí.

Không cần seed số liệu giả trong production để test báo cáo.

## Kỹ thuật và giới hạn

- Không thêm thư viện, biến môi trường hay thay phiên bản runtime.
- Precheck quyền trong transaction ngắn, giải phóng connection trước Redis; kiểm tra
  quyền lại trong transaction báo cáo read-only REPEATABLE_READ, timeout 10 giây.
- Redis Lua quota dùng chung giữa các instance, key riêng cho admin reports, 10/phút/admin.
- Bảy truy vấn aggregate SQL cho báo cáo, thêm truy vấn kiểm tra admin. Không tải toàn bộ
  entity/danh sách đơn vào Java, không N+1. DTO tối đa 366 ngày và 10 cửa hàng.
- V39 chỉ thêm index hỗ trợ đơn tạo toàn hệ thống, đơn hoàn thành theo ngày. Không sửa migration cũ hay dữ liệu nghiệp vụ. Index thông thường có thể
  chặn ghi trong lúc tạo trên bảng lớn; lên lịch migration ngoài giờ tải cao và đánh giá
  thời gian tạo index trước khi deploy production. Rollback binary có thể giữ nguyên index.
- Tổng tài khoản/deal hiện tại vẫn cần aggregate dữ liệu toàn hệ thống. Chưa chạy load test;
  chưa chứng minh capacity production. Khi dữ liệu lớn cần đo EXPLAIN ANALYZE, độ trễ,
  CPU/IO, connection-pool, tải nhiều admin và cân nhắc cache/pre-aggregation theo kết quả.
- Không trả email, ngân hàng, mật khẩu, token hay chi tiết cá nhân của khách hàng.
- Test PostgreSQL thật trong container tạm (17 local và 18 Railway), Redis 7 cho quota,
  cùng migration upgrade từ schema cũ. Không sử dụng PostgreSQL local của developer để test.

## Checkout và trạng thái

Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`.
Nhánh: `feature/merchant-premium-analytics`, HEAD trước thay đổi `2da6e24`.
Phần Premium chưa commit được giữ nguyên; chưa commit/push task này.
Backend local giữ tắt theo yêu cầu trước đó của người dùng. Không có xác nhận HTTP health
của server persistent; OpenAPI/API được kiểm tra qua MockMvc trong test PostgreSQL.

## Kết quả xác minh — 2026-10-07

`./mvnw --batch-mode clean verify`: **411 tests, 0 failures/errors/skips**, BUILD SUCCESS
(lúc 14:56:34, giờ Việt Nam). Trong đó 22 test mới cho báo cáo: 10 mỗi PostgreSQL major
và 2 Redis quota/error tests. Kiểm tra ngày Việt Nam, snapshot giá/tiết kiệm, nhiều role,
trạng thái deal, khóa cửa hàng không xóa lịch sử, top 10/tie-break, quyền và OpenAPI,
admin bị thu hồi quyền sau precheck, không giữ transaction khi chờ Redis, snapshot
REPEATABLE_READ khi có đơn ghi đồng thời.

| Thành phần | Phiên bản kiểm tra |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Maven Wrapper runtime | 3.9.16 |
| Flyway | 11.20.3 |
| PostgreSQL JDBC | 42.7.11 |
| Testcontainers | 1.21.4 |
| PostgreSQL test | 17.11 và 18.6, Flyway V39, Hibernate validate thành công |
| Redis quota test | 7-alpine |
| PostgreSQL local | 17.11, Flyway V32, chưa áp dụng V33–V39 |

Local backend **OFF**, cổng 8080 không có listener. Không có health HTTP persistent
được xác minh và không để server chạy. Không commit/push; `.env` vẫn ignored, không
thêm biến môi trường hoặc secret vào source. Chưa kiểm chứng tải production.
