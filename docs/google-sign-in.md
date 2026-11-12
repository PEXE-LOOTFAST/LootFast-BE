# Đăng nhập Google — Android Credential Manager và backend

Backend đã có POST `/api/v1/auth/social/nonce` và POST `/api/v1/auth/social/login`.
Không tạo endpoint trùng, không thay luồng đăng nhập email/mật khẩu. Backend dùng
Spring Security/Nimbus hiện có để xác minh ID token, rồi cấp access/refresh token
LootFast. Không cấp quyền bằng email hoặc role do frontend tự khai.

## Thông tin đã có và cần xác nhận

- Web OAuth Client ID người phát triển đã cung cấp (public identifier):
  `1017144698952-4usnqapcaid6kjk8mka4dd5qevpkg51p.apps.googleusercontent.com`.
  Cần xác nhận nó nằm trong Google Cloud project của LootFast và có loại Web application.
- Package đọc từ app.json và native Gradle hiện tại: `vn.lootfast.app`.
- Khóa debug hiện tại: SHA-1
  `5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`.
  Đây không phải production signing key. Dùng APK ký bằng khóa khác thì lấy SHA-1
  của khóa đó; bản Google Play phải dùng fingerprint App signing của Google Play.
- Cần xác nhận Android OAuth client (package + SHA-1) đã được tạo trong cùng project,
  audience/publishing status và tài khoản kiểm thử được phép.
- Không cần mật khẩu Google, client secret, service-account JSON hoặc private key.

## Các bước lấy trên Google Cloud

1. Mở https://console.cloud.google.com/ và chọn đúng project LootFast.
2. Google Auth Platform → Branding: điền tên ứng dụng, email hỗ trợ và thông tin
   liên hệ. Dùng đúng thông tin sản phẩm, điều khoản/chính sách và domain khi yêu cầu.
3. Audience: chọn External nếu phục vụ người dùng bên ngoài tổ chức. Khi đang Testing,
   thêm tài khoản thử nếu Google yêu cầu; không coi chỉ test được với nhóm nội bộ
   là đã sẵn sàng phát hành. Khi ra thị trường, kiểm tra publishing/verification
   theo yêu cầu Console, không yêu cầu quyền Gmail/Drive chỉ để đăng nhập.
4. Clients: mở Web application client, đối chiếu Client ID đã cung cấp. Nếu chưa có,
   Create client → Web application. ID này là `serverClientId` của Credential Manager
   và audience backend chấp nhận.
5. Create client → Android: package `vn.lootfast.app`, fingerprint SHA-1 theo khóa ký
   APK thực tế. Tạo client tương ứng cho mỗi package/khóa dùng phát hành.
6. Không điền `/api/v1/auth/social/login` làm Google redirect URI trong luồng Android
   Credential Manager: app nhận ID token từ Google rồi tự POST JSON lên API này.
   Đây không phải callback OAuth authorization-code flow trên trình duyệt.

Để tự đọc SHA-1 debug trên máy hiện tại:

```bash
cd "/home/kien/FPTU/Semester-8/EXE201/LootFast-mobile-FE"
keytool -list -v -keystore android/app/debug.keystore -alias androiddebugkey -storepass android
```

Lệnh chỉ đọc certificate của khóa debug. Nếu checkout nằm nơi khác, dùng đường dẫn
thực tế. Không dùng mật khẩu debug này cho khóa production. Không gửi keystore.

## Cấu hình local và Render

Trong `.env` ignored của backend và Render → Environment:

```properties
GOOGLE_CLIENT_IDS=1017144698952-4usnqapcaid6kjk8mka4dd5qevpkg51p.apps.googleusercontent.com
```

Local đã thêm ID vào danh sách, giữ ID cũ nếu có. Chưa sửa biến môi trường Render.
Nhiều audience được phép thì ngăn cách bằng dấu phẩy; chỉ whitelist client do mình
kiểm soát. Backend đối chiếu `aud`, không phải chỉ nhận dạng đuôi client ID.

Frontend Android đang dùng `react-native-credentials-manager`. Trong `.env` frontend:

```properties
EXPO_PUBLIC_GOOGLE_CLIENT_ID_WEB=1017144698952-4usnqapcaid6kjk8mka4dd5qevpkg51p.apps.googleusercontent.com
EXPO_PUBLIC_API_URL=https://lootfast-be.onrender.com/api/v1
```

Đây là hướng dẫn, lượt backend này không chỉnh frontend. Build lại APK sau đổi
EXPO_PUBLIC_* hoặc native module; Expo Go không có native module Credential Manager.
Không cần EXPO_PUBLIC_GOOGLE_CLIENT_ID_ANDROID cho implementation hiện tại.
Android client ID không thay Web serverClientId trong hook hiện tại.
Backend cần restart/redeploy để nạp cấu hình. Thiếu GOOGLE_CLIENT_IDS vẫn boot được,
nhưng social login trả SOCIAL_LOGIN_NOT_CONFIGURED (503).

## Flow frontend / Swagger

1. GET `/api/v1/legal-documents/TERMS_OF_USE/current` và
   `/api/v1/legal-documents/PRIVACY_POLICY/current`: lấy nội dung và phiên bản đã publish.
   Tài khoản mới phải đồng ý thật, gửi phiên bản hiện hành; backend không tự thay consent.
2. POST `/api/v1/auth/social/nonce`, body rỗng: lấy `data.nonce`, `expiresIn=300` giây.
3. App gọi Credential Manager với Web serverClientId và đúng nonce vừa lấy.
   Nhận Google **ID token**, không gửi Google access token thay thế.
4. POST `/api/v1/auth/social/login`, ví dụ:

```json
{
  "provider": "GOOGLE",
  "idToken": "ID_TOKEN_DO_GOOGLE_CAP",
  "nonce": "NONCE_VUA_LAY",
  "termsAccepted": true,
  "termsVersion": "PHIEN_BAN_DIEU_KHOAN_HIEN_HANH",
  "privacyPolicyAccepted": true,
  "privacyPolicyVersion": "PHIEN_BAN_CHINH_SACH_HIEN_HANH"
}
```

Placeholder không phải token/phiên bản hợp lệ. Chỉ gửi true sau hành động đồng ý.
Tài khoản đã liên kết không cần consent đăng ký lại. Không tạo được ID token Google
thật chỉ bằng Swagger: phải lấy qua ứng dụng/Google đã cấu hình; không chia sẻ token
vào chat hoặc log. Khi lỗi đã consume nonce, lấy nonce mới và thực hiện lại đăng nhập.
5. Response cùng cấu trúc đăng nhập thường: accessToken, refreshToken, role và roles.
   Dùng JWT LootFast gọi GET `/api/v1/users/me`, đăng xuất/refresh theo API hiện có.
6. Kiểm tra đăng nhập lần hai: cùng provider + sub phải là cùng tài khoản, không tạo
   user mới hoặc cấp thêm role. Khóa tài khoản thì không cấp phiên. API riêng vẫn
   kiểm tra role; luồng này không thay chính sách ADMIN/BUYER toàn hệ thống.

### Liên kết tài khoản và thay đổi trong lượt này

- Danh tính bền vững là provider + `sub`, không phải email.
- Với Google: chỉ tự liên kết email tài khoản VERIFIED đã có khi Google là bên
  xác nhận đáng tin cho email (Gmail hoặc email_verified kèm Workspace `hd`).
- Đăng ký email/mật khẩu chưa nhập OTP rồi chuyển sang Google: nếu token Google
  hợp lệ và Google là bên xác nhận đáng tin cho đúng email, backend xác thực tài
  khoản UNVERIFIED hiện có và liên kết Google, giữ nguyên ID tài khoản. Tài khoản
  phải chưa bị khóa và request phải xác nhận điều khoản/chính sách hiện hành.
- Khi hoàn tất đăng ký dở bằng Google, xóa mật khẩu chưa xác thực, tăng phiên bản
  xác thực và thu hồi các phiên cũ. Điều này tránh người đăng ký trước bằng email
  của người khác giữ được quyền truy cập qua mật khẩu họ đã đặt. Chủ tài khoản
  có thể dùng luồng quên mật khẩu để đặt mật khẩu mới sau khi đăng nhập Google.
- Consent của cùng phiên bản được ghi một lần; đăng ký dở rồi Google login không
  tạo bản ghi trùng. Nếu liên kết Google thất bại, việc xác thực tài khoản, xóa
  mật khẩu và thu hồi phiên đều rollback trong cùng transaction.
- Third-party email không có hd vẫn có thể đăng ký tài khoản mới với consent;
  nếu trùng tài khoản đã có thì trả SOCIAL_IDENTITY_CONFLICT (409), yêu cầu dùng
  phương thức đăng nhập LootFast hiện có. Chưa thêm API liên kết có xác minh lại
  password/OTP; không hứa rằng lần đăng nhập password sẽ tự liên kết Google.
- Token phải có exp; Nimbus kiểm tra chữ ký, issuer/audience và thời hạn. Nonce
  phải trùng claim và chỉ consume một lần bằng Redis Lua, TTL 5 phút.
- HTTP lấy JWK giới hạn connect 3 giây/read 5 giây; Nimbus cache khóa, không gọi
  tokeninfo online mỗi lần đăng nhập. Không thêm retry không giới hạn.
- SocialLoginRequest.toString che token/nonce để giảm nguy cơ vô tình log credentials.
- Không sửa schema hoặc migrations. Unique provider+sub và user+provider tiếp tục
  chống liên kết trùng; xung đột đồng thời trả lỗi có thể thử lại với login/nonce mới.

## Kiểm chứng và giới hạn trước phát hành

Tests chữ ký RS256 thật, JWK qua HTTP local/cache, token giả/hết hạn/thiếu exp,
sai issuer/audience/nonce, email chưa verify, config rỗng, email bên thứ ba/Workspace,
consent, khóa tài khoản và Redis nonce đồng thời. Không cần secrets Google trong CI.

Chưa kiểm thử end-to-end bằng Google Cloud/client ID thực tế hoặc redeploy Render.
Phải xác nhận Android client và certificate, consent/publishing status, thêm biến
Render và build lại app trước khi nghiệm thu đăng nhập thật.

Frontend hiện tự gửi termsAccepted/privacyPolicyAccepted=true và dùng phiên bản
trong code; phải thay bằng thao tác đồng ý rõ ràng và đọc phiên bản hiện hành trước
khi mở cho người dùng thật. Lượt này không sửa frontend.

Nonce endpoint hiện chưa có quota theo IP/người dùng; cần giới hạn request ở gateway
hoặc bổ sung rate limit và đo tải/quota Redis trước khi mở rộng. Các timeout/cache,
unique constraint và functional tests không chứng minh capacity production.

Nguồn:
- https://developers.google.com/identity/sign-in/android/backend-auth
- https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
- https://developer.android.com/identity/sign-in/credential-manager-siwg

## Kết quả local 24/10/2026

`./mvnw --batch-mode clean verify`: BUILD SUCCESS; 602 tests, 0 failures, 0 errors, 0 skipped. Log: `/tmp/lootfast-google-full-verify.log`. Bộ social riêng có 25 kiểm tra pass.

## Hoàn tất đăng ký dở bằng Google — 12/11/2026

- Đã kiểm tra HTTP social login với tài khoản đăng ký dở: giữ nguyên ID, xác thực
  email, liên kết Google, cấp access/refresh token dùng được; không tạo trùng consent.
- Kiểm tra mật khẩu/phiên cũ không còn dùng được, Google login lần sau vẫn thành
  công, tài khoản đã VERIFIED giữ nguyên mật khẩu; tài khoản khóa, token lỗi,
  thiếu consent và Google không authoritative không được tự kích hoạt.
- Kiểm tra rollback toàn bộ khi unique constraint liên kết Google bị xung đột.
  Các ca chạy trên H2 và PostgreSQL 17/18 với Flyway migrations thật cho PostgreSQL.
  Boundary Google token/nonce và legal version được mock trong test HTTP; xác minh
  chữ ký/token vẫn có bộ kiểm tra riêng. Chưa thử bằng tài khoản Google thật.
- Full `./mvnw --batch-mode clean verify`: 623 tests pass, không lỗi/bỏ qua.
  Log: `/tmp/lootfast-pending-google-full-verify.log`.
- Local PostgreSQL 17.11, schema V46; backend tại `http://localhost:8080`,
  `/actuator/health` trả 200/UP. Không sửa dữ liệu tài khoản trên production,
  chưa commit/push hoặc deploy Render. Không cần đổi payload hay build lại AAB
  cho luồng khôi phục này; cần deploy backend mới trước khi kiểm tra bản CH Play.
Không khởi động persistent backend, không kết nối/migrate Neon hoặc sửa Render.
