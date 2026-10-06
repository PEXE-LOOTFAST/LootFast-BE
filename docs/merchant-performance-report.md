# Task 059 — Báo cáo hiệu quả cửa hàng

API: `GET /api/v1/merchants/me/reports/performance?from=2026-10-01&to=2026-10-06`.
Bearer token của merchant đã được duyệt. Không nhận merchantId từ client.
from/to là ngày yyyy-MM-dd, bao gồm cả hai ngày, tối đa 366 ngày.
Múi giờ: Asia/Ho_Chi_Minh; đơn ở đúng đầu ngày được tính, đúng đầu ngày sau
khoảng chọn không được tính.

## Định nghĩa response

- `placedOrders`: đơn có createdAt trong khoảng chọn.
- `ordersByStatus`: phân loại các đơn đó theo trạng thái hiện tại, có đủ trạng thái
  kể cả khi giá trị bằng 0. Không phải lịch sử trạng thái tại ngày đã chọn.
- `completedRevenue`: VND, tổng totalAmount của đơn COMPLETED và CONFIRMED,
  theo finalizedAt. Không phải lợi nhuận hoặc tổng số tiền đã chuyển ngân hàng.
- `discountedPortions`: Phần ăn giảm giá — tổng quantity của đơn COMPLETED,
  theo finalizedAt, bất kể trạng thái thanh toán của dữ liệu lịch sử.
- `customerSavings`: tổng (originalUnitPrice - unitPrice) * quantity của các đơn
  COMPLETED. Dùng snapshot khi đặt, không dùng giá deal hiện tại.
- `daily`: mỗi ngày một dòng, bao gồm ngày không có giao dịch với giá trị 0.
- `topDeals`: tối đa 10 deal, sắp doanh thu giảm dần rồi UUID để ổn định.
  Điều kiện COMPLETED/CONFIRMED giống completedRevenue. Tên là tên deal hiện tại,
  số tiền vẫn theo snapshot đơn. discountedPortions trong từng dòng top chỉ tính
  các đơn đủ điều kiện doanh thu của dòng đó.

Số đơn đặt và doanh thu có cơ sở thời gian khác nhau: đơn đặt hôm qua nhưng giao
hôm nay chỉ xuất hiện trong doanh thu hôm nay. Hủy/no-show/merchant-cancelled
không có doanh thu đã giao hoặc phần ăn giảm giá.

## Flow Swagger

Backend local dùng database riêng `lootfast_reports_dev`, schema tương thích dev
đến V32. Đã sao chép dữ liệu tương thích (3 tài khoản, 0 đơn tại thời điểm chuẩn bị)
từ database gốc; không copy Flyway history hoặc email_outbox. Database gốc vẫn giữ
V28/V29 của Outbox và không bị thay đổi. Hai database độc lập, dữ liệu mới không tự
đồng bộ qua lại. Không chạy nhánh này trên database gốc, không tắt Flyway hoặc xóa
dữ liệu để vượt qua validation.
Swagger dùng effective CONTEXT_PATH + `/swagger-ui` theo application.yaml.

1. Merchant đăng nhập và Authorize. Hồ sơ merchant cần được duyệt.
2. Merchant tạo deal ở `/api/v1/merchants/me/deals`, upload ảnh và publish qua
   các API tương ứng. Ví dụ giá gốc 100.000, giá giảm 50.000, tồn kho ít nhất 2.
   Lịch sale/nhận và giờ mở cửa phải hợp lệ theo các quy tắc hiện có.
3. Gọi báo cáo trong ngày: số liệu ban đầu 0 nếu chưa có đơn trong khoảng chọn.
4. Đăng nhập buyer, Authorize token buyer, `POST /api/v1/orders` với một UUID
   mới ở header Idempotency-Key:
   ```json
   {"dealId":"UUID-deal", "quantity":2, "paymentMethod":"CASH"}
   ```
   Lưu orderId và pickupCode từ response.
5. Authorize merchant và gọi báo cáo: placedOrders tăng 1, RESERVED tăng 1;
   doanh thu và phần ăn giảm giá vẫn 0 nếu chưa có đơn hoàn tất khác.
6. Sau khi giao món và nhận tiền thật, merchant gọi
   `POST /api/v1/merchants/me/orders/pickup-confirmations`:
   ```json
   {"credential":"LF-XXXXXX"}
   ```
   CASH sẽ được ghi nhận thanh toán và hoàn tất đơn. BANK_TRANSFER cần báo cáo
   thanh toán và merchant xác nhận đã nhận tiền trước khi xác nhận giao món.
7. Gọi lại báo cáo gồm ngày hoàn tất: doanh thu tăng 100.000 VND,
   discountedPortions tăng 2, customerSavings tăng 100.000 VND. Đơn xuất hiện
   trong COMPLETED nếu ngày tạo nằm trong khoảng chọn, daily và topDeals cập nhật.
8. Tạo đơn khác rồi buyer hủy trước hạn: số đơn đặt vẫn tăng nhưng không cộng
   doanh thu/phần ăn giảm giá. Chọn ngày trống để kiểm tra các giá trị 0.
9. Kiểm tra bảo vệ: buyer gọi báo cáo bị 403; không có token bị 401; thiếu/sai
   ngày hoặc khoảng vượt 366 ngày bị 400; from > to trả INVALID_REPORT_RANGE.

## Kỹ thuật và giới hạn

Ba truy vấn tổng hợp tại PostgreSQL, giới hạn 366 dòng daily và 10 dòng top.
Service dùng transaction read-only, REPEATABLE_READ, timeout 10 giây để overview,
daily và top đọc cùng snapshot. Không tải toàn bộ đơn vào Java hoặc khóa ghi đơn.
V32 thêm index merchant/createdAt và index merchant/finalizedAt cho COMPLETED.
Không thay schema giao dịch hay bổ sung dependency/env key.

PostgreSQL-specific timezone aggregation được test trên PostgreSQL 17 và 18;
không giả định H2 tương đương. Test kiểm tra dữ liệu nhiều merchant, snapshots,
các trạng thái, ngày Việt Nam, giới hạn top, quyền API và OpenAPI.
Chưa load-test dung lượng production; khoảng ngày bị giới hạn không có nghĩa
số đơn trong khoảng đó được giới hạn. Cần đo query plan/latency với dữ liệu thật
và quyết định rate limit/cache nếu báo cáo được gọi nhiều.

Version matrix: Java 21.0.12, Spring Boot 3.5.16, Maven Wrapper 3.9.16,
Flyway 11.20.3, PostgreSQL JDBC 42.7.11, Testcontainers 1.21.4, Redis 7.
Integration startup được kiểm tra trên PostgreSQL 17.11 và 18.6, Flyway tới V32.
Theo yêu cầu khởi động tiếp theo, local .env (vẫn ignored) đã đổi DB_NAME sang
lootfast_reports_dev. Backend chạy từ chính checkout này; giữ nguyên credentials
và database gốc. Health và OpenAPI được kiểm tra khi khởi động. Bản sao dữ liệu
là snapshot, không phải cơ chế đồng bộ database hoặc hợp nhất nhánh Outbox.
