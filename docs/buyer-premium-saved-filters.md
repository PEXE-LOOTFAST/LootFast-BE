# Buyer Premium: bộ lọc deal đã lưu

Bản này triển khai lưu và dùng lại bộ lọc. Thông báo theo bộ lọc đã được bổ sung ở [task tiếp theo](buyer-premium-filter-notifications.md); báo có hàng trở lại chưa triển khai. Tìm deal thông thường và thông báo cơ bản vẫn miễn phí. Không có voucher hoặc báo cáo chi tiêu cá nhân.

## Quyền và API

Admin cấp quyền `BUYER_SAVED_FILTERS` thông qua API quyền dùng thử hiện có. Backend đọc quyền từ database, không tin cờ Premium từ frontend. Đây là cấp quyền thủ công có audit, chưa phải gói trả phí/VNPay. Nếu không gửi `benefit`, API admin vẫn mặc định `MERCHANT_ADVANCED_REPORTS` để tương thích với bản Merchant trước.

Buyer phải có role BUYER, đã xác minh và không bị khóa. Backend lấy tài khoản từ JWT; API bộ lọc không nhận userId. UUID của người khác trả 404.

| API | Công dụng | Cần quyền đang hiệu lực |
| --- | --- | --- |
| GET `/api/v1/buyers/me/premium/benefits` | Cờ savedFilters và savedFilterLimit | Không |
| POST `/api/v1/buyers/me/saved-filters` | Lưu bộ lọc mới | Có |
| GET `/api/v1/buyers/me/saved-filters?page=0&size=20` | Danh sách | Không |
| GET `/api/v1/buyers/me/saved-filters/{id}` | Chi tiết | Không |
| PUT `/api/v1/buyers/me/saved-filters/{id}` | Sửa với expectedRevision | Có |
| DELETE `/api/v1/buyers/me/saved-filters/{id}?expectedRevision=0` | Xóa | Không |
| GET `/api/v1/buyers/me/saved-filters/{id}/deals?date=2026-10-07&page=0&size=20` | Áp dụng bộ lọc để tìm deal | Có |

Giới hạn tạm thời: 10 bộ lọc/tài khoản, bán kính 0.1–50 km, trang 1–50 items. Giờ nhận là `LocalTime` theo Asia/Ho_Chi_Minh, tính lại cho ngày được chọn; không gửi timestamp của hôm qua. Phải gửi cả hai giờ hoặc bỏ cả hai, giờ bắt đầu trước giờ kết thúc, chính xác đến giây; chưa hỗ trợ khoảng qua nửa đêm. Bỏ giờ nghĩa là mọi giờ trong ngày chọn. `date` mặc định hôm nay tại Việt Nam. Giá là VND/đơn vị; maxPrice lọc discountedPrice; minDiscountPercent dùng phần trăm chính xác trước khi làm tròn để hiển thị. Kết quả có khung nhận giao với khoảng đã lưu, đang mở bán tại thời điểm request, còn hàng, cửa hàng không bị khóa/tạm ngưng.

Bộ lọc không giữ chỗ hay giá cũ. Khi đặt đơn vẫn phải kiểm tra tồn kho và giới hạn đặt hiện có. Hết hạn/thu hồi Premium giữ nguyên dữ liệu, cho xem/xóa; tạo/sửa/tìm bằng bộ lọc trả 403 PREMIUM_REQUIRED. Xóa là xóa bộ lọc, không ảnh hưởng deal hoặc đơn hàng. Danh mục tắt vẫn giữ bộ lọc cũ; tạo/sửa cần chọn danh mục đang bật.

## Luồng test Swagger

1. Đăng nhập Buyer, Authorize bằng access token. Gọi GET `/api/v1/users/me` lấy `data.publicId`, dùng làm userId trong request cấp quyền của admin. GET benefits trả savedFilters=false; POST bộ lọc hợp lệ khi chưa có quyền trả 403 PREMIUM_REQUIRED.
2. Đăng nhập Admin, Authorize token admin. POST `/api/v1/admin/premium/entitlements`:

```json
{
  "id": "b7d1efc4-b7b8-4c8c-8848-2e92206efddc",
  "userId": "UUID_CUA_BUYER",
  "benefit": "BUYER_SAVED_FILTERS",
  "startsAt": "2026-10-07T00:00:00+07:00",
  "endsAt": "2026-11-07T00:00:00+07:00",
  "reason": "Dùng thử bộ lọc đã lưu"
}
```

Thay userId và ngày theo thời điểm test. Dùng UUID mới cho lượt cấp mới; chỉ retry cùng id với cùng payload và cùng admin. Thời hạn tối đa 366 ngày; quyền hiệu lực ở startsAt và hết hiệu lực tại endsAt. Nếu account chỉ là Buyer mà cấp quyền báo cáo Merchant sẽ bị từ chối.

3. Authorize lại Buyer, GET benefits phải true. POST `/api/v1/buyers/me/saved-filters`:

```json
{
  "name": "Gần nhà dưới 50k",
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radiusKm": 5,
  "keyword": null,
  "categoryCode": "MEAL",
  "maxPrice": 50000,
  "minDiscountPercent": 20,
  "pickupFrom": "18:00:00",
  "pickupTo": "20:00:00"
}
```

Thay vị trí bằng vị trí cửa hàng test; kiểm tra code danh mục qua GET `/api/v1/categories`. Lưu `data.id` và `data.revision` (ban đầu 0).

4. GET danh sách/chi tiết. GET `/{id}/deals`, để trống date để dùng hôm nay. Chỉ xuất hiện deal phù hợp đang mở bán và còn hàng; items rỗng không có nghĩa API lỗi. Tạo deal dưới tài khoản Merchant với vị trí/giá/giờ khớp trước nếu chưa có dữ liệu.
5. PUT `/{id}` body gồm `{"expectedRevision":0,"criteria":{...toàn bộ bộ lọc mới...}}`. revision tăng thành 1. Gửi lại expectedRevision=0 phải trả 409 SAVED_FILTER_CONFLICT, rồi GET lấy bản mới. DELETE cũng cần revision đang hiện hành.
6. Tạo đến 10 bộ lọc; bộ thứ 11 trả 409 SAVED_FILTER_LIMIT_REACHED. Xóa một bộ rồi tạo lại được. Account Buyer khác không đọc/sửa/xóa/tìm qua id này: 404.
7. Authorize Admin, POST `/api/v1/admin/premium/entitlements/{grantId}/revoke` với `{"reason":"Kết thúc dùng thử"}`. Authorize Buyer, benefits=false (nếu không còn lượt cấp khác hiệu lực), danh sách vẫn có dữ liệu; tạo/sửa/tìm bị chặn. Xóa vẫn được. Tìm deal thông thường qua GET `/api/v1/deals` vẫn dùng được.

## Triển khai và tính đồng thời

Migration V36 mở rộng check constraint của bảng quyền đã có ở V35, thêm bảng bộ lọc và index theo owner/thời điểm/id. Không sửa V35 hoặc migration cũ. Nhánh này còn chứa phần Merchant Premium chưa commit và phụ thuộc V34 quản trị nội dung. Không triển khai riêng V36 khi thiếu các migration trước.

Tạo/sửa/xóa khóa hàng users trước, dùng chung thứ tự khóa với cấp/thu hồi quyền và khóa tài khoản. Hai request tạo đồng thời không vượt 10; hai request sửa cùng revision chỉ một thành công. Không dùng biến đếm trong RAM nên áp dụng được nhiều instance. Tạo POST dùng UUID mới ở server, chưa có idempotency key: không tự động retry POST khi timeout; đọc lại danh sách trước.

Tìm kiếm giới hạn 30 request/phút/Buyer bằng Redis Lua INCR+EXPIRE nguyên tử, dùng chung quota giữa instance. Redis lỗi thì từ chối tìm kiếm với RESOURCE_BUSY, không mở hết tải vào PostgreSQL. Đọc/xóa bộ lọc không phụ thuộc Redis. Precheck quyền/owner ở transaction ngắn, đóng trước khi gọi Redis; recheck trong snapshot database REPEATABLE_READ để tổng số và trang nhất quán. Hai query count + trang có LIMIT/OFFSET; không nạp toàn bộ entity hoặc N+1. Có lọc dải vĩ độ trước tính khoảng cách Haversine, timeout transaction 10 giây; CRUD 5 giây. Bán kính lớn/khu vực đông deal vẫn cần đo tải thực tế trước khi chốt năng lực production.

Không thêm thư viện Maven hoặc biến .env. Vị trí đã lưu chỉ account chủ sở hữu truy cập qua API, không đưa vào API công khai. Chưa tạo subscription, gia hạn hoặc IPN. Email thông báo theo bộ lọc nay được xử lý qua Outbox; xem hướng dẫn task thông báo.

## Kết quả kiểm chứng ngày 2026-10-07

- `./mvnw --batch-mode clean verify` thành công: 342 tests ở thời điểm hoàn thành riêng bước lưu bộ lọc, không fail/error/skip; lệnh này build và chạy toàn bộ kiểm tra, không bật server lâu dài.
- 18 kiểm tra mới: hai bộ PostgreSQL (17.11 và 18.6), quyền/expiry/ownership/revision, phân trang/visibility/giờ nhận/tìm không dấu/từ khóa literal/đường đổi ngày, tạo và sửa đồng thời; Redis 7 kiểm tra quota giữa hai instance và trường hợp offline.
- Java 21.0.12, Maven Wrapper 3.9.16, Spring Boot 3.5.16, Flyway 11.20.3, JDBC PostgreSQL 42.7.11, Testcontainers 1.21.4. Không đổi phiên bản dependency hoặc container local.
- Spring Boot test contexts khởi tạo thành công trên PostgreSQL và apply đến V36; OpenAPI kiểm tra bằng MockMvc, bao gồm giờ nhận dạng string. Chưa đo khả năng chịu tải production.
- Checkout `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`, branch `feature/merchant-premium-analytics`, HEAD trước thay đổi `2da6e24fb9fd826cbe28779b5bde8e5b6145a6ba`. Chưa commit/push.
- Backend local được giữ tắt theo yêu cầu trước đó; không có kết quả HTTP health/Swagger trên server lâu dài. Cấu hình hiện tại dùng cổng 8080, context path rỗng; khi được bật URL là `http://localhost:8080` và Swagger `/swagger-ui`. PostgreSQL local 17.11 / Flyway V32, chưa áp dụng V33–V36 vào dữ liệu local.
