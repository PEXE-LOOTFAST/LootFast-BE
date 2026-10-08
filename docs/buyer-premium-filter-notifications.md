# Buyer Premium: thông báo theo bộ lọc đã lưu

## Hành vi

Buyer chủ động bật thông báo cho từng bộ lọc. Mặc định bộ lọc mới và bộ lọc đã tồn tại đều tắt. Cần quyền `BUYER_SAVED_FILTERS` đang hiệu lực để bật; được tắt kể cả hết Premium. Không thay đổi thông báo miễn phí và không gửi bù các deal đã mở bán trước khi bật.

API mới:

`PUT /api/v1/buyers/me/saved-filters/{id}/notifications`

```json
{
  "enabled": true,
  "expectedRevision": 0
}
```

Response vẫn là SavedFilterResponse, có thêm `notificationsEnabled`. Bật/tắt làm revision tăng; gửi trạng thái đang có với revision hiện tại là no-op. Sửa nội dung bộ lọc giữ trạng thái bật nhưng đặt lại mốc theo dõi để không gửi bù deal cũ. Bật/sửa vẫn kiểm tra danh mục đang hoạt động; dữ liệu cũ không bị xóa khi hết hạn.

API chỉ dùng tài khoản JWT; không nhận userId hoặc email từ Buyer. Backend kiểm tra account verified, role BUYER, không bị khóa, Premium và sở hữu bộ lọc. Bật bộ lọc không tự bật email hoặc ghi đè cài đặt thông báo chung.

Một deal chỉ sinh tối đa một thông báo MATCHING_DEAL cho một Buyer, kể cả khớp nhiều bộ lọc hoặc đã có thông báo từ luồng miễn phí. Thông báo tạo bởi luồng Premium có matchReason=SAVED_FILTER; frontend cần hỗ trợ giá trị enum mới này. Lịch sử thông báo đã tạo không bị xóa khi người dùng tắt bộ lọc.

## Flow xử lý

1. Merchant publish deal: cùng transaction với đổi trạng thái, thêm một job theo deal vào `premium_deal_notification_jobs`. Publish rollback thì job cũng rollback. Một deal chỉ có một job; chỉnh tồn kho hoặc bật lại deal không tạo lượt thông báo mới.
2. Event time là thời điểm muộn hơn giữa publish và bắt đầu mở bán. Nếu Merchant đăng trước giờ sale, job chờ giờ sale. Chỉ nhận bộ lọc đã bật trước hoặc đúng event time; bật sau không gửi bù.
3. Worker chạy mỗi 10 giây, tối đa 20 nhóm/lượt. Mỗi transaction xử lý tối đa 100 tài khoản có quyền Premium đang hiệu lực, dùng cursor userId; không nạp toàn bộ người dùng vào RAM. Cả nhóm không khớp vẫn tiến cursor, tránh quét lặp vô hạn.
4. Worker kiểm tra deal đang mở bán, còn hàng, merchant không khóa/tạm ngưng, Buyer verified/không khóa, Premium, matchingDeals và bộ lọc. Điều kiện gồm Haversine theo bán kính, danh mục, discountedPrice <= maxPrice, mức giảm chính xác, từ khóa không dấu (ký tự `%`/`_` là ký tự thường), giờ nhận giao với khung giờ Việt Nam đã lưu.
5. Insert thông báo và email Outbox cùng transaction. Email chỉ enqueue khi matchingDeals=true và emailEnabled=true; chưa có preference thì matchingDeals mặc định true, email mặc định false. Deal khớp nhiều bộ lọc vẫn chỉ enqueue một email. Nếu luồng miễn phí đã tạo thông báo, worker Premium không tạo thêm thông báo/email.
6. Email worker claim bản ghi đã commit, đóng transaction rồi gọi Brevo. Trước claim, kiểm tra lại account, email/matchingDeals, Premium, trạng thái bộ lọc và deal. Không còn đủ điều kiện thì đánh dấu CANCELLED. Tắt trong lúc request gửi email đã bắt đầu không thể thu hồi email đã được provider chấp nhận.

Deal bị ẩn/hết hàng/merchant bị khóa thì job chờ 30 giây để kiểm tra lại, cho tới khi sale/khung nhận kết thúc. Đây vẫn là lần thông báo đầu tiên của deal; chưa phải tính năng đăng ký báo có hàng trở lại sau khi Buyer đã nhận thông báo.

## Luồng Swagger

Backend hiện được giữ tắt theo yêu cầu; chỉ thực hiện các bước dưới khi developer bật đúng checkout và database. Swagger URL dự kiến theo cấu hình local hiện tại: `http://localhost:8080/swagger-ui`.

1. Admin cấp quyền `BUYER_SAVED_FILTERS` theo [hướng dẫn bộ lọc đã lưu](buyer-premium-saved-filters.md). Buyer gọi GET `/api/v1/buyers/me/premium/benefits` thấy savedFilters=true.
2. Buyer tạo bộ lọc qua POST `/api/v1/buyers/me/saved-filters`; lưu id và revision. Ví dụ dùng vị trí merchant test, categoryCode=MEAL, maxPrice=70000, minDiscountPercent=20, pickupFrom=18:00:00, pickupTo=20:00:00.
3. PUT `/{id}/notifications` với enabled=true và revision hiện tại. Response notificationsEnabled=true, revision tăng 1. Gửi revision cũ phải trả 409 SAVED_FILTER_CONFLICT.
4. Để test email, Buyer PUT `/api/v1/users/me/notification-preferences` (cập nhật toàn bộ cài đặt, nên GET trước để giữ giá trị các loại khác):

```json
{
  "matchingDeals": true,
  "buyerOrderUpdates": true,
  "merchantOrderUpdates": true,
  "pickupReminders": true,
  "emailEnabled": true
}
```

5. Merchant tạo draft MEAL ở gần vị trí đã lưu, originalPrice=100000, discountedPrice=60000 (giảm 40%), sale đang mở hoặc bắt đầu trong vài phút, giờ nhận 18:00–19:00 theo ngày Việt Nam. Upload ảnh, hoàn thiện profile/lịch mở cửa, rồi publish qua API hiện có. Nếu chọn giảm hơn 50% hoặc Buyer đã theo dõi cửa hàng/món/khu vực, luồng miễn phí có thể tạo thông báo trước; backend vẫn giữ một thông báo. Để quan sát riêng reason SAVED_FILTER, dùng giảm 40% và Buyer không có những tùy chọn miễn phí khớp deal này.
6. Sau khi sale bắt đầu, chờ worker xử lý (khoảng 10 giây khi không có backlog). Buyer GET `/api/v1/notifications` và `/unread-count`: có đúng một MATCHING_DEAL cho deal đó; reason SAVED_FILTER nếu không có thông báo miễn phí trước. Email tới địa chỉ account, dùng key ổn định của notification; nhận email còn phụ thuộc tài khoản Brevo, sender và quota.
7. Bật hai bộ lọc cùng khớp rồi publish deal mới: vẫn chỉ một thông báo/email cho deal. Không thử lại bằng deal đã được thông báo vì nó sẽ bị deduplicate.
8. Tắt matchingDeals chung, hoặc tắt từng bộ lọc, hoặc thu hồi Premium: publish deal mới không sinh thông báo theo bộ lọc. Nếu vẫn nhận thông báo miễn phí, kiểm tra cài đặt matchingDeals/favorite; hết Premium không tắt quyền miễn phí.
9. Bật matchingDeals, giữ emailEnabled=false và publish deal mới: có thông báo trong app, không enqueue email Premium. Email tắt không tắt thông báo trong app.
10. Admin revoke grant; Buyer vẫn xem bộ lọc, được gọi enabled=false để tắt, enabled=true trả 403 PREMIUM_REQUIRED. Cần revision hiện tại từ GET.

Không thêm endpoint công khai để ép chạy worker, gửi email hoặc đọc địa chỉ email Outbox.

## Outbox và retry

Migration V37 đưa phần Outbox cần thiết từ nhánh `feature/notification-email-outbox` sang schema hiện tại; V38 thêm bật/tắt, job và reason. Không sửa/đánh lại số migration V28 đã áp dụng ở checkout/database khác. Deploy yêu cầu các migration V35–V38 và V34 quản trị nội dung đi trước. Database đang có migration V28 Outbox từ nhánh cũ cần đối chiếu toàn bộ Flyway history trước; không chạy checkout này vào database có checksum khác.

Trong task này, chỉ email của thông báo theo bộ lọc Premium nối vào Outbox. Email thông báo miễn phí/đơn hàng và OTP hiện có vẫn theo đường gửi cũ; chưa chuyển toàn bộ các đường này vào Outbox.

Email worker dùng scheduler riêng, một thread, tối đa 50 email/lượt rồi nghỉ 5 giây. Claim bằng `FOR UPDATE SKIP LOCKED`, lease dài hơn tổng connect/read timeout + 60 giây, token để worker cũ không ghi đè worker đã reclaim. Chỉ gọi network sau khi transaction claim đã đóng. Tối đa 5 attempts; backoff 30,120,600,1800 giây trước các lượt retry, có jitter; tôn trọng Retry-After giới hạn 6 giờ. 429/408/5xx và lỗi transport retry; lỗi 4xx khác FAILED; đủ attempts FAILED; crash giữ PROCESSING và được reclaim khi lease hết.

Idempotency key giữ nguyên khi retry. Brevo có [hỗ trợ key và duplicate_parameter](https://developers.brevo.com/docs/heterogenous-versions-batch-emails), nhưng giới hạn TTL của provider không phải cam kết chống trùng suốt đời. Outbox là at-least-once: trường hợp provider đã nhận email nhưng worker chết trước khi ghi SENT, retry quá TTL vẫn có thể gửi lại. SENT nghĩa là API provider chấp nhận, không xác nhận thư đã tới inbox. Chưa tích hợp webhook delivery/bounce.

## Đồng thời, vận hành và giới hạn

- Một job/transaction, SKIP LOCKED giữa instance. Cursor, thông báo và enqueue commit cùng nhau: lỗi enqueue làm rollback cả nhóm, lượt sau chạy lại. Unique index hiện có và ON CONFLICT bảo vệ một MATCHING_DEAL/account/deal ở database, không dùng tập Set trong RAM cho tính đúng đắn.
- Khóa các tài khoản recipient theo thứ tự id trước kiểm tra điều kiện cuối; cùng hàng khóa với bật/tắt/sửa bộ lọc, thay preference và cấp/thu hồi Premium. Nếu người dùng đang tắt bộ lọc, worker chờ commit rồi kiểm tra lại. Không khóa chủ cửa hàng ở bước này để tránh đảo thứ tự khóa với publish/profile.
- Khóa chỉ nằm trong transaction database timeout 5 giây; không giữ connection hoặc user lock trong lúc gửi email. Candidate window tối đa 100 accounts, mỗi account tối đa 10 filters. Bulk notification inserts và bulk email enqueue; query count không tăng kiểu N+1 theo từng filter.
- Index pending jobs, partial recipient grant index và owner/enabled filter index. Premium matching và email scheduler riêng để network email không chặn pickup/no-show scheduler. Email pending gauge, outcomes/errors counters và log UUID/category lỗi; không log địa chỉ email, body, vị trí hay exception body.
- Các job chỉ chạy sau deploy cho publish mới; không backfill thông báo cho deal cũ. Job hoàn tất giữ cursor để chống chạy lại, email terminal giữ audit; chưa có chính sách retention tự động cho terminal jobs. Cần thống nhất thời hạn và đo tăng trưởng lưu trữ trước khi có volume lớn.
- Job lỗi rollback rồi được defer ở transaction riêng theo backoff 30/120/600/1800 giây, tối đa 5 lần lỗi liên tiếp cho một cursor. Tiến cursor thành công reset bộ đếm. Cập nhật defer có điều kiện cursor để không ghi đè tiến độ mới của worker khác. Sau 5 lỗi đánh dấu failed_at để các job khác tiếp tục; cần vận hành kiểm tra nguyên nhân và retry thủ công sau khi sửa, chưa có API quản trị retry. Nếu database cũng lỗi khi ghi defer, log lỗi và chờ sweep sau. Lỗi job không làm request publish đã commit bị fail.
- Luồng miễn phí vẫn tạo recipient notifications ngay trong transaction publish như code cũ; chưa đo hoặc tối ưu bottleneck của luồng đó trong task này.
- Không thêm dependency hoặc biến .env. Java 21.0.12, Boot 3.5.16, Wrapper 3.9.16, Flyway 11.20.3, JDBC 42.7.11, Testcontainers 1.21.4; PostgreSQL local/test 17.11, thêm test 18.6 tương ứng môi trường Railway, Redis 7. Chưa chứng minh khả năng chịu tải production bằng load test.

## Kết quả kiểm chứng 2026-10-07

`./mvnw --batch-mode clean verify` đã pass **389 tests, 0 failures/errors/skips**; lệnh build và chạy toàn bộ kiểm tra, không bật server lâu dài. 48 kiểm tra Buyer trên PostgreSQL 17/18 (gồm bộ lọc cũ và thông báo mới), 11 kiểm tra Outbox claim/lease/fencing/retry/preferences/rollback, test Brevo bằng HTTP mock; không gửi email thật. Luồng publish được kiểm tra trên H2 với job được lưu đúng event time. Spring contexts PostgreSQL khởi tạo và migrate đến V38; Swagger được kiểm tra bằng MockMvc.

Kiểm tra đồng thời gồm: hai matching workers không gửi trùng, tắt bộ lọc trong lúc batch đang chạy, rollback cursor và thông báo khi enqueue lỗi, một job bị defer không chặn deal khác, dừng retry sau 5 lỗi, claim email giữa worker, reclaim lease và chặn kết quả từ worker cũ. Các kiểm tra này không thay thế load test production.

Checkout `/home/kien/FPTU/Semester 8/EXE201/lootfast-backend`, branch `feature/merchant-premium-analytics`, HEAD nền `2da6e24fb9fd826cbe28779b5bde8e5b6145a6ba`; giữ các thay đổi Merchant/bộ lọc trước đó, chưa commit/push. Backend local vẫn tắt theo yêu cầu trước đó; chưa có kết quả HTTP health trên server lâu dài. Database local PostgreSQL **17.11 / Flyway V32** vẫn giữ nguyên, migration V33–V38 chưa áp dụng vào dữ liệu local. `.env` đầy đủ tên biến bắt buộc, vẫn bị Git ignore; scan file thay đổi không phát hiện giá trị secret.
