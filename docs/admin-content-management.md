# Task 073 — Danh mục, banner, điều khoản và chính sách riêng tư

Nhánh `feature/admin-content-management` từ `origin/dev` sau khi merge task 059/072.
Không thêm Maven dependency hoặc biến môi trường. Migration mới V34; không sửa
migration cũ. Runtime vẫn PostgreSQL, ảnh dùng Cloudinary, giới hạn upload dùng Redis.

## Danh mục

- Public `GET /api/v1/categories?page=0&size=20`: danh mục đang bật.
- ADMIN `GET/POST /api/v1/admin/categories`; `PUT /api/v1/admin/categories/{code}`.
- `code` cố định, chữ in hoa/số/underscore; mã mới được chuẩn hóa. Sửa tên, enabled,
  sortOrder bằng expectedRevision (lấy từ revision response mới nhất).
- Migration tạo BAKERY, MEAL, VEGETARIAN, DRINK, OTHER, đồng thời bổ sung mọi mã cũ
  trong deals, deal_templates và user_profile_favorite_categories. Tên mã cũ tạm là
  chính mã; admin đổi tên hiển thị sau. Không sửa deal/template/sở thích lịch sử.
- Deal mới, sửa draft, publish draft và chọn sở thích phải dùng danh mục đang bật.
  Tái sử dụng template đi qua create nên bị chặn nếu danh mục đã tắt; chọn danh mục
  khác bằng tạo draft mới với nội dung mẫu. Deal cũ vẫn xem và đặt theo quy tắc cũ.
- Không xóa danh mục. Tắt danh mục chỉ ngăn sử dụng mới; không tự hủy đơn/ẩn deal.
- businessCategory của merchant không thay đổi.

## Banner

- Public `GET /api/v1/banners`: chỉ ảnh đang enabled, có ảnh và trong lịch hiển thị.
- ADMIN `GET/POST /api/v1/admin/banners`; `PUT /api/v1/admin/banners/{id}`.
- `POST /api/v1/admin/banners/{id}/image?expectedRevision=...`: multipart file.
- Tạo banner disabled -> upload ảnh -> cập nhật enabled=true bằng revision mới nhất.
- title, altText, targetPath, sortOrder, enabled, startsAt, endsAt. Có lịch thì cần
  đủ hai mốc, startsAt < endsAt. Thời điểm bắt đầu được tính vào; kết thúc không.
- Target chỉ đường dẫn nội bộ `/`, `/deals`, `/deals/{UUID}`, `/stores/{UUID}`.
  Frontend ánh xạ đường dẫn này với màn hình tương ứng; không tự mở URL ngoài.
- JPEG/PNG <=5 MB, <=20 triệu pixels, xác minh byte thực tế. Tối đa 10 upload/admin/
  giờ, Redis Lua atomic, áp dụng chung giữa các instance. Redis lỗi thì từ chối upload.
- Cloudinary upload ngoài transaction database; recheck quyền và revision khi lưu.
  Lưu thất bại thì dọn ảnh mới; sau lưu thành công dọn ảnh cũ. Nếu cleanup provider
  thất bại, log cảnh báo; cần reconciliation storage để xử lý ảnh mồ côi. Không có
  durable cleanup queue trong task này. Có thể tắt banner để gỡ khỏi giao diện.

## Tài liệu

- Loại tài liệu `TERMS_OF_USE`, `PRIVACY_POLICY`.
- ADMIN `GET/POST /api/v1/admin/legal-documents`, `GET/PUT /{id}`, `POST /{id}/publish`.
- Public `GET /api/v1/legal-documents/{document}/current` và
  `GET /api/v1/legal-documents/{document}/versions/{version}`.
- documentVersion duy nhất theo loại, 1–50 ký tự chữ/số/dấu chấm/gạch dưới/gạch ngang.
  Bản nháp sửa được; bản publish không sửa/phát hành lại. Mỗi loại có một current.
  Khi publish bản mới, bản cũ vẫn đọc được. API list admin trả summary, không tải body.
- Nội dung văn bản thuần, body <=50.000 ký tự, format=PLAIN_TEXT. Frontend render
  bằng text, không diễn giải body thành HTML. Không có nội dung chính sách tự sinh.
- Chưa có bản đầu tiên: current trả CONTENT_NOT_FOUND; đăng ký vẫn ghi nhận mã
  consent legacy như trước để tránh chặn frontend đang dùng. Đây là chế độ chuyển
  tiếp, chưa kiểm chứng nội dung gắn với mã legacy; không coi mã đó là nội dung đã duyệt.
- Sau publish đầu tiên của từng loại: signup và tạo tài khoản social kiểm tra đúng
  current version; mã sai/cũ trả CONSENT_VERSION_OUTDATED. Frontend cần GET nội dung,
  hiển thị cho người dùng đọc và đồng ý rồi gửi termsVersion/privacyPolicyVersion.
- Chuẩn bị frontend và nội dung thật trước khi publish trên production. Nên tạo và
  kiểm tra cả hai draft trước khi publish. Không ép tài khoản cũ đồng ý lại; lịch sử
  consent hiện có không bị sửa hay xóa. Không sửa giá/đơn/thanh toán.

## Swagger: luồng kiểm tra

Đăng nhập ADMIN và Authorize. Mọi bước PUT/publish/upload lấy revision mới nhất.

1. POST `/api/v1/admin/categories`:
   ```json
   {"code":"NOODLES","name":"Món mì","enabled":true,"sortOrder":60}
   ```
   Public GET categories thấy mã này. Merchant chọn `categoryCode=NOODLES` để tạo deal.
   PUT category với name/enabled=false/sortOrder/expectedRevision: mã biến mất khỏi
   picker; deal cũ còn nguyên; create/reuse mới trả CATEGORY_UNAVAILABLE. Bật lại
   với revision mới để dùng tiếp.

2. POST `/api/v1/admin/banners`:
   ```json
   {"title":"Ưu đãi cuối ngày","altText":"Các món đang giảm giá","targetPath":"/deals","enabled":false,"sortOrder":0,"startsAt":null,"endsAt":null,"expectedRevision":0}
   ```
   Lấy id và revision -> upload file qua endpoint image. Lấy revision sau upload ->
   PUT cùng payload, đổi enabled=true và expectedRevision. Public GET banners thấy
   banner. Thử lịch tương lai/hết hạn hoặc tắt enabled: public không trả banner.
   PUT dùng revision cũ: CONTENT_CONFLICT. URL javascript/external: VALIDATION_FAILED.

3. POST `/api/v1/admin/legal-documents`:
   ```json
   {"document":"TERMS_OF_USE","documentVersion":"2026-10-v1","title":"Điều khoản sử dụng LootFast","body":"Nội dung thật đã được kiểm tra trước khi phát hành."}
   ```
   Đây chỉ là mẫu request; thay body bằng nội dung hoàn chỉnh. Public chưa đọc được
   draft. POST `/{id}/publish` body `{"expectedRevision":0}` (hoặc revision hiện tại).
   Public GET TERMS_OF_USE/current thấy phiên bản. Lặp lại với PRIVACY_POLICY.
   Signup gửi hai version này; mã giả/cũ bị từ chối. Tạo v2 rồi publish: current là v2,
   GET versions/2026-10-v1 vẫn trả nội dung v1. PUT v1 bị CONTENT_CONFLICT.

4. Bỏ token hoặc dùng BUYER/MERCHANT gọi admin API: 401/403. Public không cần token.
   size >50, body/title trống, image giả: bị từ chối. Không đặt quota test trên
   production hoặc publish tài liệu thử trên production.

## Kiểm tra và giới hạn

Khóa danh mục ở database xuyên suốt transaction create/publish/preferences, sắp
thứ tự mã khi chọn nhiều danh mục để tránh deadlock. Admin sửa khóa write và so
revision; request đồng thời không ghi đè. Publication khóa head theo loại trước
version, consent khóa read cùng head; không dựa vào state trong một instance.
Database có unique/check constraints, indexes phục vụ danh sách, page <=50 và
transaction timeout. Tài liệu đã publish có metadata actor/time và giữ các bản cũ;
không phải nhật ký toàn bộ thay đổi bản nháp/banner/danh mục.

Test H2 và PostgreSQL 17/18: phân quyền/public API/OpenAPI, category disable và
preserve references/template, stale edits và concurrent edits, publication/history,
consent version enforcement, banner scheduling/targets, validation/upload failure/
cleanup/quota; migration upgrade database có dữ liệu. Full Maven verification.
Không chứng minh tải production; cần load test theo lưu lượng thực tế. Local BE
không khởi động theo yêu cầu trước đó, V34 chưa áp dụng vào DB local/Railway.
