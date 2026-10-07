# Task 076 — Quản lý cấu hình gói Premium

Catalog hỗ trợ BUYER và MERCHANT. Đây là cấu hình để hiển thị/chọn gói, **chưa có đơn
mua hoặc thanh toán VNPay**, chưa tự cấp quyền cho người dùng khi publish gói.
Không cần key VNPay, không thêm biến môi trường hoặc dependency.

## Quy tắc

- Code `[A-Z0-9_]{1,64}` và audience cố định suốt vòng đời gói.
- Giá VND là số nguyên JSON (không dùng chuỗi hoặc số có phần lẻ), từ 1.000 đến 999.999.999; thời hạn 1–366 ngày (không phải tháng lịch).
  Admin nhập giá, không có giá production được seed. Giá ví dụ dưới chỉ để thử local.
- Tên tối đa 120, mô tả tối đa 2.000 ký tự, text thuần; FE render text an toàn.
- Quyền lợi chỉ chọn enum backend đã triển khai: BUYER_SAVED_FILTERS cho BUYER,
  MERCHANT_ADVANCED_REPORTS cho MERCHANT. Không chọn role khác hoặc quyền chưa có.
- BUYER_SAVED_FILTERS hiện gồm lưu/tìm theo bộ lọc và thông báo theo bộ lọc. Giới hạn
  lưu hiện tại vẫn là 10 của feature đã triển khai. Task này không cho cấu hình quota
  tùy ý khi runtime chưa hỗ trợ; không đổi chức năng Free hoặc quyền hiện có.
- Gắn với `termsDocumentId` của TERMS_OF_USE. Draft có thể dùng tài liệu draft, nhưng
  publish gói cần tài liệu đã phát hành. Nội dung điều khoản do người vận hành cung cấp,
  không tự tạo chính sách pháp lý/hoàn tiền production.
- Một gói tối đa một DRAFT và một PUBLISHED. Bản đã publish không sửa cấu hình,
  không republish bản ARCHIVED. Tạo revision mới để đổi giá, mô tả, kỳ hoặc quyền lợi.
- Publish bản mới tự archive bản đang bán cũ trong cùng transaction; code/audience
  không đổi. Archive không hard-delete cấu hình hoặc audit. Bản nháp cũng có thể archive.
- `version` là số phiên bản của gói; `revision` là bộ đếm thay đổi để chống ghi đè.
  Mỗi edit/publish/archive làm revision tăng 1. Request dùng revision cũ trả 409.
- Archive lại với revision hiện tại là no-op, không tạo audit trùng; gửi revision cũ
  vẫn trả 409. Retry create cùng code trả 409, không tự tạo thêm phiên bản.
- Audit lưu actor UUID, action, reason, snapshot cấu hình, timestamp. EDIT có lý do
  hệ thống “Update draft”; publish/archive bắt buộc admin nhập reason.
- Không có API DELETE. Giao dịch/subscription chưa có ở task này; các bước sau phải lưu
  snapshot đơn mua và tham chiếu revision giữ lại, không đọc giá gói mới để sửa đơn cũ.

## API

| API | Tác dụng |
| --- | --- |
| POST `/api/v1/admin/premium/plans` | Tạo gói và draft version 1 |
| GET `/api/v1/admin/premium/plans?audience=BUYER&code=BUYER_30D&page=0&size=20` | Tất cả phiên bản, có lọc |
| POST `/api/v1/admin/premium/plans/{code}/revisions` | Tạo draft tiếp theo với configuration mới |
| GET `/api/v1/admin/premium/plan-revisions/{id}` | Đọc một phiên bản, kể cả đã archive |
| PUT `/api/v1/admin/premium/plan-revisions/{id}` | Sửa draft với expectedRevision |
| POST `/api/v1/admin/premium/plan-revisions/{id}/publish` | Công bố và thay phiên bản đang bán |
| POST `/api/v1/admin/premium/plan-revisions/{id}/archive` | Ngừng bán hoặc bỏ draft |
| GET `/api/v1/admin/premium/plans/{code}/audit` | Lịch sử thao tác có snapshot, phân trang |
| GET `/api/v1/premium/plans?audience=BUYER&page=0&size=20` | Public, chỉ bản PUBLISHED |
| GET `/api/v1/premium/plans/{id}` | Public, draft/archive hoặc ID không có trả 404 |

List size 1–50, page >= 0, offset không vượt Integer.MAX_VALUE; sort code ASC/version DESC.
Admin phải có role ADMIN, VERIFIED, chưa khóa, kiểm tra lại từ DB. Các API public không
trả actor/audit và không yêu cầu token. Response trong `data` của ApiResponse.

## Luồng test Swagger

1. Đăng nhập ADMIN và **Authorize** bằng access token.
2. Dùng task 073 tạo điều khoản tại POST `/api/v1/admin/legal-documents`:

```json
{
  "document": "TERMS_OF_USE",
  "documentVersion": "premium-local-v1",
  "title": "Điều khoản Premium thử nghiệm local",
  "body": "Nội dung thử nghiệm local do người vận hành nhập; không dùng bản này làm chính sách production."
}
```

Lấy `data.id` và `data.revision`. Gọi POST `/api/v1/admin/legal-documents/{id}/publish`
với `{"expectedRevision":0}` nếu revision hiện là 0. Dùng đúng revision server trả.
Lưu ý: API này làm thay đổi điều khoản hiện hành trong môi trường đang test; chỉ thực
hiện ví dụ trên local, không công bố điều khoản ví dụ vào production.

3. POST `/api/v1/admin/premium/plans`, thay UUID bằng id điều khoản đã phát hành:

```json
{
  "code": "BUYER_30D",
  "audience": "BUYER",
  "configuration": {
    "name": "Buyer Premium",
    "description": "Lưu bộ lọc và nhận thông báo deal theo bộ lọc",
    "priceVnd": 49000,
    "durationDays": 30,
    "benefits": ["BUYER_SAVED_FILTERS"],
    "termsDocumentId": "00000000-0000-0000-0000-000000000000"
  }
}
```

Lấy id của **plan revision**, khác id tài liệu. Ban đầu `status=DRAFT`, version=1,
revision=0. GET public catalog chưa thấy bản này.

4. POST `/api/v1/admin/premium/plan-revisions/{id}/publish`:

```json
{"expectedRevision":0,"reason":"Công bố gói thử nghiệm local"}
```

Response PUBLISHED/revision=1. GET public catalog lọc BUYER sẽ thấy gói giá 49.000.
Nếu điều khoản chưa publish, bước này 400 và gói vẫn DRAFT.

5. Đổi giá bằng POST `/api/v1/admin/premium/plans/BUYER_30D/revisions`. Body chính là
`configuration` phía trên (không bọc trong configuration), đổi priceVnd thành 59000.
Response version=2, DRAFT, revision=0. Public vẫn thấy bản 1 giá 49.000.

6. Có thể PUT `/plan-revisions/{id-bản-2}` trước publish:

```json
{
  "expectedRevision": 0,
  "configuration": {
    "name": "Buyer Premium",
    "description": "Lưu bộ lọc và nhận thông báo deal theo bộ lọc",
    "priceVnd": 59000,
    "durationDays": 30,
    "benefits": ["BUYER_SAVED_FILTERS"],
    "termsDocumentId": "00000000-0000-0000-0000-000000000000"
  }
}
```

Lại thay UUID thật; edit làm revision tăng. Publish dùng revision mới. Sau đó public
chỉ thấy bản 2; bản 1 ARCHIVED vẫn đọc được bằng admin. Audit có AUTO_ARCHIVE và PUBLISH.

7. Tạo merchant gói khác: `code=MERCHANT_30D`, audience MERCHANT, benefit
MERCHANT_ADVANCED_REPORTS. Giá admin chọn (ví dụ local 199000), durationDays=30.
Publish và GET public lọc MERCHANT. Không trộn benefit buyer vào gói merchant.

8. Archive bản đang bán với expectedRevision hiện tại và reason: public không thấy
nữa, admin/audit vẫn thấy. Sửa bản PUBLISHED/ARCHIVED hoặc dùng revision cũ -> 409
PREMIUM_PLAN_CONFLICT. Duplicate code hoặc draft thứ hai cùng gói -> 409.
9. BUYER/MERCHANT gọi POST admin -> 403; không token -> 401. Giá âm/quá biên,
thiếu field, durationDays=0, benefit sai role/không biết -> 400.

## Production review

V40 thêm bảng catalog/revision/benefit/audit và partial unique index một draft/một bản
đang bán; không sửa migration đã chạy, không seed gói, không sửa quyền trial hiện có.
Khóa hàng premium_plans tuần tự hóa sửa/tạo revision/publish/archive, revision kiểm tra
trong lock tránh lost update. Chuyển trạng thái và audit atomic, thất bại rollback toàn bộ.
Điều khoản published bất biến theo API task 073; FK giữ liên kết bản điều khoản chính xác.

Jdbc query parameterized, count/list trong snapshot REPEATABLE_READ, transaction timeout
5 giây, output tối đa 50 cấu hình. Benefit được load cùng query SQL, không truy vấn riêng
mỗi entity trong Java. Audit snapshot tối đa theo các DTO đã giới hạn; history phân trang.
Không có network/email/payment call trong transaction. Catalog không có API được tính
phí nên không thêm quota Redis mới; chưa kiểm chứng tải production, cần đo độ trễ/DB
với traffic thực trước khi đưa ra cam kết capacity.

Checkout: `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`.
Nhánh `feature/merchant-premium-analytics`, HEAD trước thay đổi `2da6e24`.
Giữ phần chưa commit trước đó; chưa commit/push task này. Backend local giữ OFF theo yêu
cầu trước đó, không có kiểm tra HTTP persistent health; test API/OpenAPI dùng MockMvc
trong app context PostgreSQL thật. Local PostgreSQL 17.11 vẫn V32, source mới V40.

## Xác minh ngày 2026-10-07

`./mvnw --batch-mode clean verify`: **429 tests, 0 failures/errors/skips, BUILD SUCCESS**,
kết thúc 16:14:31 giờ Việt Nam. 18 test mới (9 mỗi PostgreSQL major) kiểm tra catalog
buyer/merchant, publish/archive/giá bất biến/audit, draft uniqueness, version/revision,
roles/paging/DTO/OpenAPI, giá VND có phần lẻ, concurrency và rollback khi audit thất bại.

Java 21.0.12; Spring Boot 3.5.16; Maven 3.9.16; Flyway 11.20.3; JDBC 42.7.11;
Testcontainers 1.21.4; PostgreSQL test 17.11 và 18.6 apply Flyway V40/validate thành công;
Redis 7 dùng các test hiện có. Không đổi phiên bản hoặc môi trường local.
`.env` tồn tại, tên biến cần thiết đầy đủ, vẫn ignored; không thêm secrets vào source.
Backend persistent OFF, port 8080 không có listener, health HTTP chưa xác minh theo
yêu cầu giữ tắt của người dùng. Không commit/push, chưa chứng minh tải production.
