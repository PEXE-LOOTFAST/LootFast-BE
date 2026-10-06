# Phạm vi đề xuất cho task 059, 072, 073 và 078

Trạng thái: bản kế hoạch để thảo luận, chưa triển khai backend.
Đối chiếu working tree ngày 06/10/2026, nhánh `feature/notification-email-outbox`,
HEAD `90ae75edd2b98f50c1f04613a2f8ec1f99ced4cc`.
Không thay đổi Google Sheets hoặc trạng thái task trong bước này.

## Nội dung task thay thế

| Task | Tên và phạm vi đề xuất |
| --- | --- |
| 059 | [Người bán] Báo cáo đơn hàng, doanh thu đã giao và phần ăn giải cứu theo khoảng ngày của cửa hàng |
| 072 | [Quản trị] Tìm kiếm/xem tài khoản, khóa/mở khóa có lý do, thu hồi phiên và lưu lịch sử thao tác |
| 073 | [Quản trị] Quản lý danh mục deal, banner có lịch hiển thị và điều khoản có phiên bản; cung cấp API đọc công khai |
| 078 | [Quản trị] Tiếp nhận yêu cầu xuất/xóa dữ liệu cá nhân, xuất an toàn và ẩn danh theo chính sách lưu giữ |

## 059 — Báo cáo hiệu quả cửa hàng

Hiện trạng: đơn có merchant, quantity, giá gốc/giá bán chụp tại lúc đặt,
totalAmount, trạng thái đơn/thanh toán và finalizedAt. Có thống kê tác động cho
buyer; chưa thấy API báo cáo hiệu quả cho merchant.

Phạm vi phiên bản đầu:
- Merchant chỉ xem dữ liệu của chính cửa hàng, lấy danh tính từ token.
- Lọc ngày theo Asia/Ho_Chi_Minh, giới hạn mỗi lần tối đa 366 ngày.
- Tổng đơn đặt và phân bổ trạng thái theo createdAt trong khoảng chọn.
- Doanh thu đã giao: tổng totalAmount của đơn COMPLETED và CONFIRMED,
  theo finalizedAt trong khoảng chọn. Đây không phải báo cáo lợi nhuận/kế toán.
- Phần ăn giải cứu: tổng quantity của đơn COMPLETED, theo finalizedAt.
- Tiền tiết kiệm: tổng (originalUnitPrice - unitPrice) * quantity của đơn COMPLETED.
- Chuỗi doanh thu/phần ăn theo ngày và top deal theo doanh thu đã giao (tối đa 10).
- Nêu rõ cơ sở thời gian cho từng chỉ số: đơn đặt và đơn hoàn tất có thể thuộc
  hai khoảng ngày khác nhau, không yêu cầu các nhóm tổng cộng bằng nhau.
- Không cộng đơn hủy/no-show vào doanh thu đã giao. Không tự tạo chỉ số hoàn tiền
  khi hệ thống hiện chưa có trạng thái hoặc sổ hoàn tiền.

API dự kiến: `GET /api/v1/merchants/me/reports/performance?from=...&to=...`.
from/to là ngày, bao gồm cả hai ngày; chuyển thành khoảng Instant [start, nextDay).
Truy vấn SUM/COUNT/GROUP BY tại database, không tải toàn bộ đơn vào Java.
Files dự kiến: controller/service/DTO báo cáo merchant, OrderRepository, migration
index nếu cần dựa trên truy vấn, tests dữ liệu và phân quyền.

Nghiệm thu: nhiều merchant, ngày trống, ranh giới ngày VN, giá deal thay đổi sau
đặt, các trạng thái đơn/thanh toán và khoảng ngày vượt giới hạn.

## 072 — Quản trị tài khoản

Hiện trạng: UserStatus chỉ có UNVERIFIED/VERIFIED; refresh session có cơ chế
thu hồi. JWT decoder hiện kiểm tra chữ ký/thời hạn, chưa đối chiếu trạng thái khóa.

Phạm vi:
- ADMIN tìm kiếm theo thông tin tài khoản, lọc vai trò/trạng thái, phân trang tối đa 50.
- Xem chi tiết có chọn lọc; không trả password hash, token hoặc bí mật thanh toán.
- Khóa/mở khóa với lý do, người thao tác, thời điểm và audit log.
- Tách trạng thái khóa khỏi trạng thái xác minh email để mở khóa không biến
  một tài khoản chưa xác minh thành tài khoản đã xác minh.
- Khóa chặn đăng nhập thường/social, refresh và request bảo vệ bằng JWT cũ.
  Thu hồi refresh session là chưa đủ để vô hiệu access token.
- Kiểm tra trạng thái bằng dữ liệu dùng chung (database cho phiên bản đầu),
  không dùng bộ nhớ riêng của từng instance để đảm bảo khóa có hiệu lực.
- Không cho tự khóa hoặc khóa ADMIN trong phạm vi đầu để tránh mất quyền quản trị.
- Merchant bị khóa không được nhận đơn mới; feed công khai và luồng đặt đơn
  phải kiểm tra trạng thái merchant. Không đổi trạng thái hoặc tự hủy đơn cũ.
- Chốt quy trình xử lý đơn RESERVED của merchant trước khi đưa tính năng khóa
  lên production: quản trị vận hành xử lý giao/hủy/đối soát thế nào khi merchant
  không còn truy cập API. Chưa có luồng đó thì không tự cho rằng đơn đã được xử lý.
- Khóa phải an toàn khi đồng thời có refresh/đặt đơn; kiểm tra trong transaction
  và chọn khóa database theo cùng thứ tự với các luồng hiện có.

API dự kiến: GET `/api/v1/admin/users`, GET `/{userId}`,
POST `/{userId}/lock`, POST `/{userId}/unlock`.
Files dự kiến: user entity/repository, admin user controller/service/DTO,
auth/security, deal feed/order access, audit entity/repository, migrations và tests.

Nghiệm thu: BUYER/MERCHANT bị từ chối API admin; JWT đã cấp bị chặn sau khóa;
refresh bị chặn; mở khóa vẫn giữ trạng thái xác minh; audit và hành động lặp
không trùng side effect; xử lý merchant và đơn đang tồn tại được kiểm tra riêng.

## 073 — Danh mục, banner, điều khoản

Hiện trạng: categoryCode hiện là chuỗi trên deal; chưa có bảng danh mục chuẩn,
banner hay nội dung điều khoản có phiên bản. Không đồng nhất danh mục deal với
businessCategory của merchant nếu chưa có yêu cầu sản phẩm.

Triển khai thành ba phần trong cùng task để review/test độc lập:
1. Danh mục deal: code ổn định, tên, thứ tự và active; code duy nhất. Tắt danh mục
   ngăn tạo/cập nhật deal sang danh mục đó; giữ dữ liệu và hiển thị lịch sử.
   Backfill các code đang tồn tại trước khi bắt buộc kiểm tra danh mục.
   Không hard-delete danh mục đang được deal/template tham chiếu.
2. Banner: tiêu đề, ảnh, link, thứ tự, active, startsAt/endsAt; API public chỉ trả
   banner trong lịch. Giới hạn dữ liệu và định dạng link; áp dụng cơ chế upload ảnh
   hiện có sau khi kiểm tra quyền và quy tắc lưu trữ, tránh tạo hệ upload thứ hai.
3. Điều khoản: type, version, nội dung plain text hoặc Markdown giới hạn,
   draft/published, publishedAt; phiên bản đã publish bất biến, sửa bằng bản mới.
   Public đọc bản hiện hành và bản theo version. Lưu việc người dùng chấp nhận
   phiên bản điều khoản là phạm vi bổ sung cần chốt, không mặc nhiên đã bao gồm.

API dự kiến: admin/public cho `deal-categories`, `banners`, `terms`.
Files dự kiến: các feature controller/service/entity/repository/DTO, migration,
validation danh mục trong deal/template, cấu hình public routes và tests.
Không thêm CMS hoặc thư viện mới chỉ để lưu nội dung.

Nghiệm thu: quyền admin, lịch banner, danh mục tắt và dữ liệu lịch sử,
code/version duy nhất, publish đồng thời, nội dung có giới hạn, API public
không lộ bản nháp và phiên bản điều khoản cũ còn đọc được.

## 078 — Xuất và xử lý yêu cầu xóa dữ liệu

Hiện trạng: dữ liệu liên kết qua user/profile/đơn/thanh toán/notification/session;
xóa user trực tiếp có thể phá quan hệ và thông tin giao dịch.

Phạm vi đề xuất:
- Tạo yêu cầu có loại EXPORT/ERASURE, tài khoản, người yêu cầu/xử lý, trạng thái
  và audit. Xác minh chủ thể/quyền xử lý trước khi xuất hoặc xóa.
- Xuất JSON phiên bản đầu, schema có version: dữ liệu cá nhân, cài đặt,
  đơn và dữ liệu merchant do tài khoản sở hữu; không kèm bí mật xác thực,
  thông tin riêng của đối tác giao dịch hoặc account khác.
- Export lớn dùng job PostgreSQL + worker, phân trang/đọc theo lô, giới hạn
  dung lượng; artifact lưu private, tải có kiểm tra quyền và thời hạn.
  Không dùng email_outbox để làm job export vì trạng thái/payload khác nhau.
- Xóa theo chính sách ẩn danh: chặn sử dụng tài khoản, thu hồi session,
  gỡ thông tin cá nhân khỏi các nơi được phép; giữ liên kết/số liệu giao dịch
  cần lưu. Không DELETE CASCADE toàn bộ đơn/thanh toán.
- Tạm chưa cho hoàn tất ERASURE khi còn đơn RESERVED hoặc thanh toán cần
  xử lý/đối soát. Khóa tài khoản và xóa tài khoản là hai nghiệp vụ khác nhau.
- Kiểm kê cả snapshot JSON creation_response, bank snapshot, giấy phép merchant,
  ảnh/file private, notification, email_outbox, danh tính social, logs và backups;
  sửa mỗi bảng users là chưa đủ. Ngăn worker gửi email sau khi ẩn danh.
- Chốt thời gian lưu giữ, trường dữ liệu giữ lại và xử lý backup trước khi code
  nhánh ERASURE. Không hứa xóa mọi bản sao tức thời khi backup còn chứa dữ liệu.
- Retry/idempotency theo từng bước, không giữ transaction/lock database trong
  suốt lúc xuất file hoặc gọi object storage.

API dự kiến: `/api/v1/admin/privacy-requests`, chi tiết request,
thao tác xử lý và tải kết quả private. API tự yêu cầu cho buyer/merchant là phần
bổ sung cần chốt; phiên bản đầu không mặc nhiên có self-service.
Files dự kiến: privacy request/job/audit, service export/anonymization,
các repository liên quan, private storage/download, worker, migrations và tests.

Nghiệm thu: không tải được file của người khác; file hết hạn; dữ liệu nhiều trang;
không xuất token/password/PII bên thứ ba; đơn cũ còn nguyên số liệu; lỗi giữa các
bước có thể chạy lại; notification/worker không gửi tới tài khoản đã xóa.

## Thứ tự và kiểm tra local

Đề xuất: 059 → 072 → 073 (danh mục → banner → điều khoản) → 078 (export → erasure).
059 ít phụ thuộc và dễ đối chiếu bằng dữ liệu mẫu; 072 xây nền kiểm soát truy cập,
audit; 078 làm sau vì cần chính sách và kiểm kê dữ liệu.

Trước khi đổi nhánh, cần xử lý các thay đổi Outbox/global feed chưa commit hiện
có; không tự commit hoặc ghi đè. Khi bắt đầu mỗi task, dùng working branch riêng
theo AGENTS.md và giữ checkout/database nhất quán.

Không cần thêm BullMQ, Kafka hay framework báo cáo. Spring Data JPA, PostgreSQL,
Spring Security và worker có sẵn đủ cho phạm vi đề xuất; dependency mới nếu có
phải giải thích và kiểm tra tương thích trước.

Sau từng phần code: full Maven verify, PostgreSQL integration tests và Swagger
flow dữ liệu mẫu. Trước startup phải kiểm tra .env (chỉ tên biến), database/schema
history và nguồn process. Việc để backend chạy hay giữ tắt theo chỉ đạo hiện hành
của người dùng. Không sửa cấu hình Railway hoặc dữ liệu production trong các task local.
Chưa có load test nên không tuyên bố đã chứng minh năng lực production.

## Quyết định cần chốt trước triển khai phần tương ứng

- 059: dùng các định nghĩa doanh thu và cơ sở ngày bên trên hay cần thêm chỉ số?
- 072: quy trình xử lý đơn đang nhận khi merchant bị khóa; có cần quyền khóa ADMIN?
- 073: chỉ điều khoản hay cả chính sách riêng tư; có cần lưu chấp nhận phiên bản?
- 078: admin xử lý hay thêm self-service; chính sách giữ dữ liệu và thời gian lưu.
