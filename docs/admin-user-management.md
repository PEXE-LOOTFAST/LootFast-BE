# Task 072 — Quản trị tài khoản

Sử dụng account ADMIN hiện có; không tạo hoặc nâng quyền admin khi startup.
Tất cả API dưới đây yêu cầu bearer token ADMIN và kiểm tra vai trò trong database.
userId/adminId trong response là public UUID, không phải ID số của database.

## API

- `GET /api/v1/admin/users?keyword=...&role=BUYER&verificationStatus=VERIFIED&locked=false&page=0&size=20`
- `GET /api/v1/admin/users/{userId}`
- `POST /api/v1/admin/users/{userId}/lock`, body `{"reason":"Cần kiểm tra tài khoản"}`
- `POST /api/v1/admin/users/{userId}/unlock`, body `{"reason":"Đã xử lý xong"}`
- `GET /api/v1/admin/users/{userId}/actions?page=0&size=20`

Filters đều tùy chọn; size tối đa 50, keyword tối đa 100 ký tự, reason không rỗng
và tối đa 500 ký tự. Tìm theo tên/email/điện thoại, không phân biệt hoa thường;
`%` và `_` được tìm như ký tự thường, không phải wildcard do client điều khiển.
Không hứa tìm không dấu. Lọc role hỗ trợ tài khoản có nhiều vai trò mà không
lặp dòng hoặc đếm lặp. Không trả password hash, token hay phiên bản xác thực.

## Khóa và mở khóa

Trạng thái khóa độc lập với xác minh email. Lock lưu thời điểm/lý do, tăng
`authentication_version`, thu hồi tất cả refresh sessions và ghi audit trong
cùng transaction. Mở khóa xóa cờ/lý do khóa hiện tại nhưng giữ lịch sử và trạng
thái xác minh. Người dùng phải đăng nhập lại; token trước lúc khóa không hồi sinh.
Thao tác lặp cùng trạng thái trả trạng thái hiện tại, không ghi audit lặp hoặc
thay lý do khóa ban đầu. Không cho khóa/mở khóa ADMIN, kể cả chính người thao tác.
Khóa chưa có thời hạn tự động; admin chủ động mở khóa.

JWT mới có claim auth_version. JWT cũ thiếu claim được coi là version 0 và còn
hợp lệ với tài khoản chưa bị khóa lần nào. Decoder vẫn kiểm tra chữ ký/thời hạn,
sau đó kiểm tra user tồn tại, khóa và phiên bản bằng database. JWT không còn hợp
lệ trả UNAUTHORIZED (401), không lộ chi tiết user qua lỗi xác thực. Đăng nhập đúng
mật khẩu vào tài khoản bị khóa trả ACCOUNT_LOCKED (403); refresh bị khóa trả
INVALID_REFRESH_TOKEN. Google và Apple cũng bị chặn. Khi gọi signin trong Swagger,
xóa bearer token cũ khỏi Authorize nếu token đó đã bị khóa.

Mỗi request có bearer token thêm một truy vấn theo primary key users. Không cache
trạng thái khóa trong bộ nhớ từng instance, nên các instance cùng database nhìn
thấy khóa khi đã commit. Không thu hồi request đang thực thi một cách hồi tố.
Đăng nhập tạo session, refresh, logout và admin khóa dùng user lock trước session
lock để tránh đảo thứ tự. Transaction có timeout; token/session cấp đồng thời với
khóa không được tiếp tục sử dụng sau khi khóa commit.

Khóa buyer chặn đặt đơn; khóa merchant ẩn deal/store khỏi public discovery và
chặn đơn mới ở luồng reserve. Account lock phối hợp với merchant-profile lock
hiện có để tuần tự hóa với đặt đơn. Không thay trạng thái đơn cũ, hủy đơn, tự xác
nhận thanh toán hoặc hoàn tiền. Merchant bị khóa cũng không gọi được API giao
đơn cũ. Nếu mục tiêu chỉ hạn chế kinh doanh mà vẫn cần truy cập để xử lý đơn, dùng
nghiệp vụ đình chỉ cửa hàng riêng và lên kế hoạch vận hành cho các đơn đang chờ.
Không gộp trạng thái đình chỉ cửa hàng vào khóa tài khoản.

Audit chỉ thêm khi trạng thái đổi, có UUID của target/actor, action, reason và
thời điểm; không có API sửa/xóa audit. UUID là snapshot, không FK tới users để
không buộc xóa lịch sử nếu sau này ẩn danh/xóa tài khoản. Chính sách giữ/xóa audit
và PII trong reason cần được xác định khi triển khai task 078.
User dùng DynamicUpdate để cập nhật mật khẩu/xác minh đồng thời không ghi đè
các trường khóa từ một entity đã đọc trước đó.

## Flow Swagger

1. Đăng nhập ADMIN hiện có, Authorize; gọi danh sách để lấy userId public UUID.
2. Xem chi tiết tài khoản buyer thử nghiệm, đăng nhập buyer và lưu token/refresh.
3. Authorize lại ADMIN, POST lock với lý do; response locked=true.
4. Dùng token buyer trước khóa gọi GET /api/v1/orders: 401. Signin đúng mật khẩu
   bị 403 ACCOUNT_LOCKED; refresh cũ bị từ chối.
5. ADMIN đọc actions: một dòng LOCK. Lock lặp không tạo thêm dòng.
6. ADMIN POST unlock có lý do; response locked=false. Token/refresh cũ vẫn bị
   từ chối. Buyer signin lại lấy token mới và gọi API bình thường.
7. Xem history gồm LOCK/UNLOCK; thử khóa ADMIN trả ACCOUNT_ACTION_FORBIDDEN (409).
8. Với merchant thử nghiệm, xác nhận deal không còn xuất hiện và buyer không đặt
   được bằng dealId đã biết sau khi account bị khóa. Đơn trước đó còn nguyên dữ liệu.

## Validation và môi trường

V33 bổ sung account fields, audit và index; V32 thuộc task 059 vẫn đang được giữ
trong working tree chưa commit. Không sửa migration đã áp dụng trước đó.
PostgreSQL integration tests kiểm tra API/roles/search, JWT cũ và legacy version,
refresh/signin/social providers, idempotency, verification preservation,
merchant visibility/reservation, concurrent lock/refresh và cập nhật password
không làm mất lock, cùng logout idempotent cho tài khoản khóa/session không có.

Java 21.0.12, Boot 3.5.16, Maven Wrapper 3.9.16, Flyway 11.20.3,
JDBC 42.7.11, Testcontainers 1.21.4, Redis 7; test PostgreSQL 17.11 và 18.6.
Không thêm dependency hoặc env key. Local .env vẫn ignored. Backend giữ tắt theo
chỉ đạo người dùng; database local reports_dev đang V32 chưa được nâng lên V33.
Khi người dùng yêu cầu chạy, Flyway sẽ áp dụng V33 trên database tương thích này.
Không dùng database gốc có V28/V29 Outbox để chạy nhánh này.

Chưa chứng minh tải production. Search chứa keyword có thể quét nhiều dòng;
phân trang/query timeout và chỉ ADMIN giúp giới hạn phạm vi nhưng không thay thế
load test. Cần đo độ trễ DB và connection pool của JWT check khi chạy nhiều user.
Code 059 chưa commit vẫn được giữ nguyên; không tự commit/push trong task này.
