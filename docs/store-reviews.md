# Đánh giá và phản hồi cửa hàng — task 046+060

## Quy tắc

- Buyer VERIFIED, không bị khóa, chỉ đánh giá đơn COMPLETED của chính mình.
- Tài khoản merchant có thêm role buyer vẫn không được đánh giá cửa hàng của chính mình.
- Mỗi đơn một đánh giá, 1–5 sao; nhận xét tùy chọn, tối đa 1000 ký tự. Không có ảnh ở bản đầu.
- Đánh giá không sửa/xóa ở bản đầu; merchant không được sửa nội dung của buyer.
- Merchant phản hồi đúng cửa hàng của mình một lần, tối đa 1000 ký tự. Gửi lại cùng nội dung trả kết quả cũ; nội dung khác trả REVIEW_REPLY_CONFLICT (409). Không phản hồi đánh giá đang bị ẩn.
- Admin ẩn/hiện lại với lý do bắt buộc và lịch sử xử lý. Gửi lại cùng trạng thái không thêm sự kiện.
- Rating trung bình (hai chữ số thập phân) và số lượt chỉ tính đánh giá công khai. Chưa có đánh giá: averageRating=null, reviewCount=0.
- merchantId là UUID công khai của tài khoản merchant, giống API cửa hàng hiện tại.
- API công khai chỉ trả tên đầu của buyer, không email, buyerId, orderId hoặc lịch sử kiểm duyệt. Nội dung là văn bản thuần; frontend phải escape khi hiển thị, không render HTML.

## API (Swagger tag Store reviews)

| Method | Route | Quyền |
| --- | --- | --- |
| POST | /api/v1/orders/{orderId}/review | Buyer sở hữu đơn |
| GET | /api/v1/orders/{orderId}/review | Buyer sở hữu đánh giá |
| GET | /api/v1/stores/{merchantId}/reviews | Công khai |
| GET | /api/v1/merchants/me/reviews | Merchant, gồm đánh giá bị ẩn |
| PUT | /api/v1/merchants/me/reviews/{reviewId}/reply | Merchant sở hữu cửa hàng |
| GET | /api/v1/admin/reviews | Admin |
| PUT | /api/v1/admin/reviews/{reviewId}/visibility | Admin |
| GET | /api/v1/admin/reviews/{reviewId}/moderation-history | Admin |

GET danh sách dùng page >= 0, size 1–50, mặc định 20, mới nhất trước.
GET cửa hàng và GET chi tiết deal giữ nguyên cấu trúc rating hiện có, thay dữ liệu placeholder bằng đánh giá cửa hàng thực tế. Feed deal không thêm truy vấn rating cho từng món.

## Luồng test Swagger

1. Qua luồng đặt/nhận hàng hiện có, lấy orderId của một đơn COMPLETED. Chuẩn bị token buyer sở hữu đơn, merchant của đơn và admin.
2. Authorize buyer, POST /api/v1/orders/{orderId}/review:
   ```json
   {"rating":5,"comment":"Món ngon, nhận hàng nhanh."}
   ```
   Lấy data.review.reviewId. Gửi lại POST: 409 REVIEW_ALREADY_EXISTS. Đơn chưa hoàn tất: 409 REVIEW_NOT_ALLOWED. Đơn người khác: 404 ORDER_NOT_FOUND.
3. GET /api/v1/orders/{orderId}/review để đọc lại. GET /api/v1/stores/{merchantId}/reviews để xem công khai và GET /api/v1/stores/{merchantId} để kiểm tra rating.
4. Authorize merchant, GET /api/v1/merchants/me/reviews. PUT /api/v1/merchants/me/reviews/{reviewId}/reply:
   ```json
   {"reply":"Cảm ơn bạn đã ghé cửa hàng!"}
   ```
   Gửi lại cùng nội dung: 200, giữ thời điểm phản hồi. Nội dung khác: 409. Merchant khác: 404.
5. Authorize admin, PUT /api/v1/admin/reviews/{reviewId}/visibility:
   ```json
   {"hidden":true,"reason":"Nội dung vi phạm quy định cộng đồng."}
   ```
   GET công khai không còn đánh giá này; rating loại bỏ số sao tương ứng. GET moderation-history có sự kiện với admin, lý do và thời gian. Đổi hidden=false kèm lý do để khôi phục và ghi sự kiện mới.
6. Thử sao 0/6, nhận xét trên 1000 ký tự, phản hồi/lý do rỗng, size=51: 400. Buyer gọi API admin hoặc merchant: 403.

## Database và tính đồng thời

Migration V45 thêm store_reviews và review_moderation_events, không sửa migration đã áp dụng. Unique order_id là lớp bảo vệ cuối. Tạo đánh giá khóa bản ghi đơn; phản hồi/kiểm duyệt khóa bản ghi đánh giá. Lock timeout PostgreSQL 3 giây, transaction ghi 5 giây. Kiểm duyệt và audit cùng transaction; các thao tác độc lập không cần khóa toàn cửa hàng. Danh sách dùng phân trang và entity graph cho quan hệ to-one, có index theo merchant/thời gian và index cho đánh giá công khai.

Testcontainers PostgreSQL 17 và 18 chạy migration + Hibernate validate, kiểm tra hai request đánh giá và hai phản hồi đồng thời, quyền truy cập, dữ liệu công khai, ẩn/hiện và audit. Test khởi động web trên cổng ngẫu nhiên với saved-filter notifications bật, kiểm tra HTTP /actuator/health=UP và /v3/api-docs. Đây là server/container kiểm thử tạm thời; backend local thông thường vẫn tắt theo yêu cầu của developer.

Chưa có push/email khi nhận đánh giá, upload ảnh, sửa đánh giá, công cụ báo cáo đánh giá vi phạm từ buyer hay lọc nội dung tự động. Chưa chứng minh khả năng tải production; cần đo với lượng đánh giá và traffic dự kiến trước khi cam kết công suất.
