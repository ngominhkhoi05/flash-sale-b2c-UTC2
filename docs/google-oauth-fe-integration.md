# Hướng dẫn FE tích hợp đăng nhập bằng Google OAuth2

> **Audience**: Frontend dev (Next.js 16 + React 19).
> **Backend**: Spring Boot `flash-sale-b2c-UTC2` — đã sẵn sàng endpoint, không cần sửa BE.
> **Flow chuẩn**: OAuth2 Authorization Code Flow (server-side exchange, an toàn nhất).
> **Cập nhật lần cuối**: 2026-10-17.

---

## 1. Tổng quan flow

> **LƯU Ý code FE hiện tại (2026-10-17)**: có **2 implementation** song song
> trong `flash-sale-b2c-fe`:
>
> 1. **Same-window redirect** (docs này mô tả) — `GoogleLoginButton` +
>    `useGoogleRedirect` + `/auth/google/callback`. Đây là flow chính thức.
> 2. **Popup flow cũ** (KHÔNG tài liệu nào mô tả ngoài JSDoc) —
>    `useGoogleLogin` + `/oauth2/redirect` page. URI callback là
>    `/oauth2/redirect` (KHÁC `/auth/google/callback`). Nếu dùng nhầm
>    flow này sẽ bị Google trả `redirect_uri_mismatch` vì URI popup
>    không nằm trong danh sách đã đăng ký ở Google Console (mục 2.3).
>
> Khi dev FE mới integrate, **chỉ dùng flow #1**. Flow #2 sẽ bị xóa
> trong commit refactor sắp tới.

```text
┌──────────┐                  ┌──────────┐               ┌──────────┐
│   FE     │  1. Lấy URL      │    BE    │               │  Google  │
│ (Browser)│ ────────────────▶│  (8080)  │               │          │
│          │                  │          │               │          │
│          │  2. Trả về URL   │          │               │          │
│          │ ◀────────────────│          │               │          │
│          │                  │          │               │          │
│          │  3. window.location = authorizationUrl         │
│          │ ─────────────────────────────────────────────▶│          │
│          │                                                │          │
│          │  4. User chọn tài khoản, đồng ý scope         │          │
│          │                                                │          │
│          │  5. Redirect về callback kèm ?code=...         │
│          │ ◀─────────────────────────────────────────────│          │
│          │                                                │          │
│          │  6. POST /api/v1/auth/google (gửi code)        │
│          │ ────────────────▶│                            │          │
│          │                  │  7. Đổi code → id_token     │          │
│          │                  │ ──────────────────────────▶│          │
│          │                  │                            │          │
│          │                  │  8. Trả về id_token         │          │
│          │                  │ ◀──────────────────────────│          │
│          │                  │                            │          │
│          │                  │  9. Verify signature, lấy email/name/picture
│          │                  │  10. Upsert User + AuthAccount
│          │                  │  11. Cấp JWT               │
│          │  12. Trả về JWT  │                            │          │
│          │ ◀────────────────│                            │          │
└──────────┘                  └──────────┘               └──────────┘
```

**Điểm quan trọng**:

- FE **KHÔNG** tự gọi `oauth2.googleapis.com/token` — phải để BE làm (vì cần `client_secret`).
- FE **KHÔNG** nhận `id_token` từ Google — chỉ nhận `code`, gửi sang BE, BE trả về `accessToken` + `refreshToken` của hệ thống mình.
- BE đã verify email + signature + audience → FE không cần verify gì thêm.

---

## 2. Cấu hình domain & redirect URI

### 2.1. Domain chuẩn cho production

```text
FE callback URL:    https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback
BE API base URL:    https://vibe-mart-utc2.canluaz.io.vn/api/v1
```

### 2.2. Cấu hình FE

Tạo file `flash-sale-b2c-fe/.env.local` (nếu chưa có — hiện tại repo chỉ có
`.env.example` + `.env.local.example` + `.env.production.example`, dev phải tự tạo):

```bash
# Public — accessible in browser
# LƯU Ý: code FE đọc `NEXT_PUBLIC_API_URL` (không phải `..._API_BASE_URL`)
# src/lib/api/client.ts dòng 156 đã hardcode tên biến này.
# apiFetch tự động prepend thêm segment "/v1" khi gọi endpoint, nên URL
# chỉ cần base origin là đủ (vd `/api/v1` qua Nginx, hoặc
# `http://localhost:8080/api` cho local dev — xem .env.example gốc của FE).
NEXT_PUBLIC_API_URL=https://vibe-mart-utc2.canluaz.io.vn/api/v1

# BẮT BUỘC: phải đăng ký URI này ở Google Cloud Console (mục 2.3)
# và cũng phải khớp `OAUTH2_REDIRECT_URI` trên server BE.
# Code FE hiện chỉ đọc env này ở 1 chỗ duy nhất
# (src/app/(auth)/auth/google/callback/page-client.tsx dòng ~96) — nếu
# thiếu, callback gửi `redirectUri=""` và BE trả AUTH_502_GOOGLE_EXCHANGE.
NEXT_PUBLIC_GOOGLE_REDIRECT_URI=https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback
```

> **Lịch sử đổi tên**: bản trước của docs nói `NEXT_PUBLIC_API_BASE_URL`
> nhưng code FE đã dùng `NEXT_PUBLIC_API_URL` từ đầu. Bản này đã sửa
> cho khớp code — KHÔNG copy tên cũ vào `.env.local`.

### 2.3. Cấu hình Google Cloud Console (OAuth Client)

Vào [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → chọn OAuth 2.0 Client ID `858260586907-...` → **Authorized redirect URIs** phải có:

| # | URI | Môi trường |
|---|---|---|
| 1 | `https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback` | **Production** (bắt buộc) |
| 2 | `http://localhost:3000/auth/google/callback` | Local dev (nếu dev cần test) |

**Không cần** thêm `/api/v1/auth/google/callback` — Google không gọi BE trực tiếp, chỉ gọi FE callback.

### 2.4. Cấu hình BE

Đã có sẵn ở `flash-sale-b2c-UTC2/src/main/resources/application-prod.yaml` (hoặc `application.yaml` trên server). BE tự đọc `OAUTH2_REDIRECT_URI` từ env. Không cần sửa code.

---

## 3. Các API BE cung cấp

### 3.1. `GET /api/v1/auth/google/url`

Lấy URL Google consent screen để redirect user.

**Request**: không cần body, không cần header.

**Response 200 OK**:

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy Google consent URL thành công",
  "data": {
    "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=858260586907-...&redirect_uri=https%3A%2F%2Fvibe-mart-utc2.canluaz.io.vn%2Fauth%2Fgoogle%2Fcallback&response_type=code&scope=openid+email+profile&access_type=offline&prompt=consent",
    "state": "550e8400-e29b-41d4-a716-446655440000"
  },
  "timestamp": "2026-10-17T05:25:00Z"
}
```

| Field | Kiểu | Mô tả |
|---|---|---|
| `data.authorizationUrl` | string | URL Google consent screen. **FE dùng `window.location.href = ...` để redirect.** |
| `data.state` | string | Random UUID, optional. FE có thể dùng cho CSRF protection (xem mục 6). |

**Lưu ý**: `state` BE trả về hiện tại chỉ là random UUID, **BE không verify state khi callback**. Nếu muốn CSRF protection thật, FE tự generate state riêng, lưu vào sessionStorage, rồi verify khi callback (xem mục 6).

### 3.2. `POST /api/v1/auth/google`

Đổi authorization code lấy JWT.

**Request**:

```http
POST /api/v1/auth/google
Content-Type: application/json
```

```json
{
  "code": "4/0AXlqoi7o0GA47owI_EEL0xETXs67Op0DPNZwEWtl8G-_8fb9IAlKHbW5HfBt6iRxmk5KEw",
  "redirectUri": "https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback"
}
```

| Field | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `code` | string | ✓ | Authorization code từ Google. **One-time use, hết hạn ~10 phút.** |
| `redirectUri` | string | ✓ | Redirect URI đã dùng ở consent URL. **Phải khớp y hệt** URI đã đăng ký ở Google Console. |

**Response 200 OK**:

```json
{
  "success": true,
  "code": 200,
  "message": "Đăng nhập bằng Google thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJr...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJr...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 12,
      "email": "khoi22092021@gmail.com",
      "fullName": "Nguyễn Văn Khôi",
      "phone": null,
      "avatarUrl": "https://lh3.googleusercontent.com/a/AGN...",
      "status": "ACTIVE",
      "roles": ["ROLE_BUYER"],
      "createdAt": "2026-10-17T05:25:00Z"
    },
    "roles": ["ROLE_BUYER"]
  },
  "timestamp": "2026-10-17T05:25:00Z"
}
```

| Field | Kiểu | Mô tả |
|---|---|---|
| `data.accessToken` | string | JWT dùng cho mọi request sau (Bearer token). |
| `data.refreshToken` | string | Dùng cho `POST /api/v1/auth/refresh-token` khi access token hết hạn. |
| `data.tokenType` | string | Luôn là `"Bearer"`. |
| `data.expiresIn` | number | Số giây access token còn hiệu lực (thường 3600 = 1 giờ). |
| `data.user` | object | Thông tin user. `avatarUrl` lấy từ Google profile. |
| `data.roles` | array | Danh sách role. User Google mới sẽ tự động có `ROLE_BUYER`. |

**Lưu ý quan trọng**:

- Nếu email Google **đã tồn tại trong DB** (vd user đã đăng ký bằng email/password trước đó) → BE tự động **link account**, không tạo user mới. User vẫn login được bằng cả 2 cách.
- Nếu email Google **chưa verify** (Google trả `email_verified=false`) → BE trả lỗi `AUTH_401_GOOGLE_EMAIL_UNVERIFIED`. User phải verify email Google trước.

---

## 4. Flow tích hợp FE

### Bước 1 — Tạo nút "Đăng nhập bằng Google"

Trên trang login, khi user click nút:

```ts
// Gọi BE lấy authorizationUrl
const { data } = await apiFetch<{
  authorizationUrl: string;
  state: string;
}>('/auth/google/url');

// Lưu state vào sessionStorage (optional, cho CSRF — xem mục 6)
sessionStorage.setItem('google_oauth_state', data.state);

// Redirect user sang Google
window.location.href = data.authorizationUrl;
```

### Bước 2 — Tạo trang callback

Tạo route `src/app/auth/google/callback/page.tsx`. Khi Google redirect về, URL có dạng:

```text
https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback?code=4%2F0A...&scope=email+profile+...&authuser=0&prompt=consent
```

FE cần:

1. Parse `code` từ query string.
2. Verify state (nếu dùng CSRF — xem mục 6).
3. Gọi `POST /api/v1/auth/google` với `code` + `redirectUri`.
4. Lưu token vào storage.
5. Redirect về trang chủ / trang user yêu cầu.

```ts
// Pseudo-code trong page.tsx
const code = searchParams.get('code');
const error = searchParams.get('error');

if (error) {
  // User cancel hoặc Google trả error
  // Redirect về /login?error=google_cancelled
  redirect('/login?error=google_cancelled');
}

if (!code) {
  redirect('/login?error=google_no_code');
}

// Verify state (nếu dùng CSRF)
const expectedState = sessionStorage.getItem('google_oauth_state');
const actualState = searchParams.get('state');
if (expectedState && expectedState !== actualState) {
  redirect('/login?error=google_state_mismatch');
}
sessionStorage.removeItem('google_oauth_state');

// QUAN TRỌNG: `redirectUri` ở đây PHẢI khớp 100% với `redirect_uri`
// đã dùng ở consent URL. Tốt nhất lưu vào sessionStorage lúc lấy URL rồi
// đọc lại ở đây, tránh hard-code mismatch giữa 2 chỗ.
// Ví dụ: lúc gọi GET /auth/google/url, FE có thể đọc lại `state` và
// `redirect_uri` từ authorizationUrl (parse bằng URL constructor) rồi lưu
// vào sessionStorage. Hoặc đơn giản hơn: dùng cùng env var
// NEXT_PUBLIC_GOOGLE_REDIRECT_URI ở cả 2 nơi. Nếu sai, Google sẽ trả
// `invalid_grant` → backend trả 400 + AUTH_502_GOOGLE_EXCHANGE.
//
// LƯU Ý CODE FE HIỆN TẠI (2026-10-17): page-client.tsx đọc
// `process.env.NEXT_PUBLIC_GOOGLE_REDIRECT_URI` trực tiếp tại callback
// mà KHÔNG snapshot từ consent URL. Nếu env đổi giữa 2 thời điểm
// (deploy, hot-reload) → mismatch. Hiện tại hoạt động vì cả 2 chỗ
// cùng dùng 1 env var — sửa sau nếu cần.
const redirectUri = process.env.NEXT_PUBLIC_GOOGLE_REDIRECT_URI!;

// Gọi BE
try {
  const auth = await apiFetch<{
    accessToken: string;
    refreshToken: string;
    user: User;
    roles: string[];
  }>('/auth/google', {
    method: 'POST',
    body: JSON.stringify({
      code,
      redirectUri,
    }),
  });

  // Lưu token (theo convention dự án: localStorage key "fs_access_token")
  localStorage.setItem('fs_access_token', auth.accessToken);
  localStorage.setItem('fs_refresh_token', auth.refreshToken);

  // Cập nhật auth store (Zustand)
  useAuthStore.getState().setUser(auth.user, auth.roles);

  // Redirect về trang user yêu cầu (hoặc trang chủ)
  const returnTo = sessionStorage.getItem('post_login_redirect') || '/';
  sessionStorage.removeItem('post_login_redirect');
  redirect(returnTo);
} catch (err) {
  if (err instanceof ApiError) {
    redirect(`/login?error=google_${err.code.toLowerCase()}`);
  } else {
    redirect('/login?error=google_unknown');
  }
}
```

### Bước 3 — Xử lý lỗi trên trang login

Ở trang login, đọc query `?error=...` và hiển thị message tiếng Việt:

```ts
// vi.json → namespace "error"
"error": {
  "google_cancelled": "Bạn đã hủy đăng nhập bằng Google.",
  "google_no_code": "Google không trả về mã xác thực. Vui lòng thử lại.",
  "google_state_mismatch": "Phiên đăng nhập không hợp lệ. Vui lòng thử lại.",
  "google_auth_401_google_token": "Mã xác thực Google không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.",
  "google_auth_502_google_exchange": "Không thể xác thực với Google. Vui lòng thử lại sau.",
  "google_auth_500_google_config": "Hệ thống chưa cấu hình Google. Vui lòng liên hệ admin.",
  "google_auth_401_google_email_unverified": "Email Google chưa được xác minh. Vui lòng verify email rồi thử lại.",
  "google_unknown": "Đăng nhập bằng Google thất bại. Vui lòng thử lại."
}
```

---

## 5. Checklist khi deploy production

| # | Việc cần làm | Ai làm | Trạng thái |
|---|---|---|---|
| 0 | Tạo file `flash-sale-b2c-fe/.env.local` thật (repo hiện chỉ có `.env.example` + `.env.local.example` + `.env.production.example`). Cần khai báo tối thiểu `NEXT_PUBLIC_API_URL` và `NEXT_PUBLIC_GOOGLE_REDIRECT_URI` theo mục 2.2 | Dev | ☐ |
| 1 | Thêm `https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback` vào **Authorized redirect URIs** ở Google Console | DevOps | ☐ |
| 2 | Set env `OAUTH2_REDIRECT_URI=https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback` trên server BE | DevOps | ☐ |
| 3 | Restart BE sau khi đổi env | DevOps | ☐ |
| 4 | Set env `NEXT_PUBLIC_GOOGLE_REDIRECT_URI=https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback` trên server FE | DevOps | ☐ |
| 5 | Build + deploy FE | DevOps | ☐ |
| 6 | Verify: truy cập `https://vibe-mart-utc2.canluaz.io.vn/login` → click "Đăng nhập bằng Google" → chọn tài khoản → redirect về callback → JWT được lưu | QA | ☐ |
| 7 | Verify DB: row mới trong `users` + `auth_accounts` (provider = `GOOGLE`) | DevOps | ☐ |

---

## 6. CSRF Protection với `state` (optional, khuyến nghị)

OAuth2 spec khuyến nghị FE tự tạo `state` ngẫu nhiên trước khi redirect, lưu vào session, rồi verify khi callback để chống CSRF.

**Lưu ý**: hiện tại BE **không verify state** khi callback — chỉ trả về state random. Nếu muốn verify thật, FE tự quản lý state:

```ts
// Trước khi redirect sang Google:
const state = crypto.randomUUID();
sessionStorage.setItem('google_oauth_state', state);
const url = new URL(authorizationUrl);
url.searchParams.set('state', state);  // override state BE trả về
window.location.href = url.toString();

// Trong callback page:
const expectedState = sessionStorage.getItem('google_oauth_state');
const actualState = searchParams.get('state');
if (!expectedState || expectedState !== actualState) {
  // CSRF attack hoặc state hết hạn
  redirect('/login?error=google_state_mismatch');
}
sessionStorage.removeItem('google_oauth_state');
```

**Lưu ý phân biệt**:
- `state` mà BE trả về trong `GET /api/v1/auth/google/url` chỉ là random UUID vô nghĩa — BE không dùng nó.
- Nếu muốn CSRF thật, **FE tự generate** state, override state trong URL, và tự verify.

---

## 7. Bảng lỗi chi tiết

### 7.1. Lỗi từ phía Google (user nhìn thấy ở consent screen)

| Lỗi | Nguyên nhân | User nên làm |
|---|---|---|
| `redirect_uri_mismatch` | URI callback trong `authorizationUrl` không khớp với Google Console | Báo DevOps, kiểm tra `NEXT_PUBLIC_GOOGLE_REDIRECT_URI` |
| `access_denied` | User bấm "Cancel" ở consent screen | Bỏ qua, redirect về /login |
| `invalid_request` | Thiếu `client_id`, `scope` hoặc param sai | Báo DevOps, kiểm tra `authorizationUrl` |
| `unauthorized_client` | Client ID bị disable hoặc xóa trên Console | Báo DevOps, tạo lại OAuth Client |

### 7.2. Lỗi từ BE (response có `success: false`)

| HTTP | `code` | `message` | Nguyên nhân | Cách xử lý FE |
|---|---|---|---|---|
| 401 | `AUTH_401_GOOGLE_TOKEN` | "Mã xác thực Google không hợp lệ hoặc đã hết hạn" | (1) `code` bị sai/cắt ký tự, (2) `redirectUri` trong body không khớp với URL đã dùng ở consent, (3) `clientSecret` BE sai, (4) `clientId` BE khác với Console | Hiển thị "Phiên đăng nhập hết hạn, vui lòng thử lại", redirect về /login |
| 401 | `AUTH_401_GOOGLE_EMAIL_UNVERIFIED` | "Email Google chưa được xác minh" | User dùng tài khoản Google có email chưa verify | Hiển thị "Vui lòng verify email Google rồi thử lại", link tới https://myaccount.google.com |
| 401 | `AUTH_401_GOOGLE_INVALID` | "Mã id_token từ Google không hợp lệ" | id_token signature sai, audience sai, hoặc hết hạn | Báo DevOps, kiểm tra `clientId` BE có khớp Console không |
| **400** | `AUTH_502_GOOGLE_EXCHANGE` | "Không thể trao đổi authorization code với Google" | BE gọi Google token endpoint bị 400/401/4xx/5xx. Nguyên nhân thường gặp: `clientSecret` BE sai, hoặc `code` hết hạn (>10 phút), `code` đã dùng rồi, hoặc `redirectUri` không khớp 100% với URI lúc generate code | Hiển thị "Không thể kết nối Google, vui lòng thử lại sau", bấm nút "Thử lại" → quay lại consent screen. **QUAN TRỌNG**: trước đây (≤ 2026-10-17) lỗi này trả HTTP 502 khiến Cloudflare/NGINX can thiệp trả về HTML 502, làm FE không đọc được error code. Đã fix ở commit `fix(auth): return 400 instead of 502` — hiện tại Cloudflare pass nguyên JSON response. |
| 500 | `AUTH_500_GOOGLE_CONFIG` | "Hệ thống chưa cấu hình Google OAuth2" | BE thiếu `clientId` / `clientSecret` / `redirectUri` trong config | Báo DevOps ngay, tạm thời ẩn nút Google |
| 400 | `VALIDATION_ERROR` | "Authorization code không được để trống" / "redirectUri không được để trống" | FE gửi body thiếu field | Báo bug, kiểm tra callback page có parse `code` đúng không |

### 7.3. Lỗi từ phía FE (cần handle trước khi gọi BE)

| Tình huống | Cách xử lý |
|---|---|
| Callback URL không có `?code=...` | Có thể user bấm Cancel ở consent, hoặc Google redirect sai. Đọc `?error=...` từ query string, redirect về /login với message phù hợp |
| `code` rỗng / undefined | Redirect về /login, hiển thị "Không nhận được mã từ Google, vui lòng thử lại" |
| Callback page reload nhiều lần | Code Google chỉ dùng được 1 lần, reload sẽ fail. Disable re-mount hoặc dùng `sessionStorage` flag để chỉ gọi BE 1 lần |

### 7.4. Mẹo debug nhanh

1. Mở DevTools → tab **Network** → filter `auth/google`.
2. Click nút Google → chọn tài khoản → xem request `POST /api/v1/auth/google`.
3. Click vào request → tab **Payload** → kiểm tra `code` có đầy đủ ký tự (đặc biệt dấu `/` sau số `4`), `redirectUri` có khớp URI đã đăng ký không.
4. Tab **Response** → xem error code → tra bảng 7.2.

#### Mẹo phát hiện Cloudflare "nuốt" response

Nếu response từ Postman/browser là **HTML 502** (không phải JSON), nghĩa là Cloudflare/NGINX đang xử lý upstream 5xx. Cách verify:

```bash
# SSH vào server, gọi trực tiếp backend (bypass Cloudflare + NGINX)
ssh root@<VPS_IP>
curl -sS -i -X POST "http://localhost:8080/api/v1/auth/google" \
  -H "Content-Type: application/json" \
  -d '{"code":"4/0AX_DUMMY","redirectUri":"https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback"}'

# Nếu thấy HTTP 502 + JSON → backend trả 502 (lỗi logic — sửa code)
# Nếu thấy HTTP 400 + JSON → backend OK, Cloudflare bị cache cũ
# Nếu thấy HTML 502 từ chính curl → NGINX có error_page override
```

**Lưu ý lịch sử**: trước 2026-10-17, `AUTH_502_GOOGLE_EXCHANGE` trả HTTP 502 → Cloudflare convert thành HTML 502 → FE không parse được error code. Đã fix bằng cách đổi `HttpStatus.BAD_GATEWAY` → `HttpStatus.BAD_REQUEST` trong `AuthErrorCode.java`. Nếu gặp lại HTML 502 từ Cloudflare, kiểm tra backend đã deploy fix chưa.

---

## 8. Ví dụ e2e flow thành công

```text
1. User mở https://vibe-mart-utc2.canluaz.io.vn/login
2. Bấm nút "Đăng nhập bằng Google"
3. FE gọi GET /api/v1/auth/google/url
4. BE trả authorizationUrl (chứa redirect_uri=https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback)
5. FE redirect window.location.href = authorizationUrl
6. User chọn tài khoản Google "khoi22092021@gmail.com"
7. Google redirect về https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback?code=4/0A...&state=...
8. FE parse code từ URL, gọi POST /api/v1/auth/google với {code, redirectUri}
9. BE đổi code → id_token → verify → upsert user → cấp JWT
10. BE trả {accessToken, refreshToken, user, roles}
11. FE lưu token vào localStorage, cập nhật auth store
12. FE redirect về "/" (trang chủ)
13. User dùng app bình thường với JWT
```

---

## 9. Liên hệ khi gặp lỗi

- **BE lỗi `AUTH_502_GOOGLE_EXCHANGE` kéo dài** → kiểm tra log server (terminal `bootRun`), tìm dòng `Google token exchange HTTP error`. Nếu là `401 Unauthorized` → check `clientSecret` ở env var.
- **FE nhận `redirect_uri_mismatch` ở consent screen** → check `NEXT_PUBLIC_GOOGLE_REDIRECT_URI` và Google Console.
- **User Google đã có account local với cùng email** → BE tự động link, user login được bằng cả 2 cách (Google + email/password). Nếu user muốn tách → xóa row trong `auth_accounts`.

---

## 10. Lịch sử thay đổi quan trọng

| Ngày | Phiên bản | Thay đổi | Tác động |
|---|---|---|---|
| 2026-10-17 | `docs/sync-google-oauth-fe-env-names` | Sửa mục 2.2 (env var), 4 Bước 2 (redirectUri snapshot), thêm mục 1 (note popup flow cũ), cập nhật mục 5 checklist (`#0` tạo `.env.local` thật). | Đồng bộ docs với code FE thật (`NEXT_PUBLIC_API_URL`, snapshot pattern, env file chưa có sẵn). |
| 2026-10-17 | `fix/auth-http-400` | Đổi `AuthErrorCode.GOOGLE_EXCHANGE_FAILED` từ `HttpStatus.BAD_GATEWAY` (502) → `HttpStatus.BAD_REQUEST` (400) | Cloudflare/NGINX không còn convert JSON response thành HTML 502. FE parse được error code. **Code string `AUTH_502_GOOGLE_EXCHANGE` giữ nguyên** (không phải đổi sang `AUTH_400_*`) để tránh breaking change. |
| 2026-10-17 | — | Thêm mục 7.4 về mẹo debug Cloudflare/NGINX | FE dev có thể tự debug khi gặp HTML 502 |
| 2026-10-17 | — | Thêm lưu ý `redirect_uri` khớp 100% ở mục 4 Bước 2 | Tránh lỗi `invalid_grant` phổ biến nhất |

### Tại sao giữ code string cũ khi HTTP status đổi?

Lý do 1: **Code là business semantics** (`AUTH_502_GOOGLE_EXCHANGE` nghĩa là "trao đổi code với Google thất bại") — KHÔNG liên quan HTTP status. Đổi code string sẽ:
- Phá breaking change ở 6+ file FE đang dựa vào code này
- Phải update docs, i18n keys, test fixtures → tốn công vô ích

Lý do 2: **HTTP status là transport layer** — chỉ ảnh hưởng cách Cloudflare/NGINX handle response, không liên quan logic nghiệp vụ. Đổi 502→400 đủ để giải quyết vấn đề mà KHÔNG cần đổi code string.

Lý do 3: **Số 502 trong code string** giờ trở thành "legacy identifier" — vẫn hoạt động bình thường, không xung đột với HTTP status 400 hiện tại. Sau này nếu muốn refactor → làm trong 1 commit riêng, có test cover, có deprecation plan.

---

## 11. Quick start cho FE dev mới (5 phút)

Đây là phần "làm nhanh" nếu bạn chỉ cần Google login chạy được, không cần đọc hết 10 mục trên.

### Bước 1: Setup env (1 phút)

Tạo `flash-sale-b2c-fe/.env.local`:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_GOOGLE_REDIRECT_URI=http://localhost:3000/auth/google/callback
```

> Lưu ý: `NEXT_PUBLIC_GOOGLE_REDIRECT_URI` PHẢI khớp với `google.oauth2.redirect-uri` trong `application-local.yaml` của BE. Mặc định cả 2 đều là `http://localhost:3000/auth/google/callback`.

### Bước 2: Tạo nút Google (1 phút)

Trong `src/app/login/page.tsx`:

```tsx
<button
  onClick={async () => {
    const { data } = await apiFetch<{ authorizationUrl: string }>('/auth/google/url');
    window.location.href = data.authorizationUrl;
  }}
>
  Đăng nhập bằng Google
</button>
```

### Bước 3: Tạo callback page (2 phút)

Tạo file `src/app/auth/google/callback/page.tsx`:

```tsx
'use client';
import { useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

export default function GoogleCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    if (error || !code) {
      router.replace('/login?error=google_cancelled');
      return;
    }

    fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code,
        redirectUri: process.env.NEXT_PUBLIC_GOOGLE_REDIRECT_URI,
      }),
    })
      .then((r) => r.json())
      .then((json) => {
        if (json.success) {
          localStorage.setItem('fs_access_token', json.data.accessToken);
          localStorage.setItem('fs_refresh_token', json.data.refreshToken);
          router.replace('/');
        } else {
          router.replace(`/login?error=google_${json.code.toLowerCase()}`);
        }
      })
      .catch(() => router.replace('/login?error=google_unknown'));
  }, [searchParams, router]);

  return <div>Đang xử lý đăng nhập...</div>;
}
```

### Bước 4: Test (1 phút)

1. Chạy BE: `cd flash-sale-b2c-UTC2 && ./gradlew bootRun`
2. Chạy FE: `cd flash-sale-b2c-fe && npm run dev`
3. Mở `http://localhost:3000/login`
4. Click "Đăng nhập bằng Google" → chọn tài khoản → redirect về `/`
5. Check localStorage có `fs_access_token`

### Nếu gặp lỗi

| Triệu chứng | Tra mục |
|---|---|
| HTML 502 từ Cloudflare | Mục 7.4 + 10 |
| `redirect_uri_mismatch` ở consent | Mục 2.3 |
| `invalid_grant` (code hết hạn/sai) | Mục 4 Bước 2 |
| `AUTH_502_GOOGLE_EXCHANGE` trong JSON | Bảng 7.2 |
| Token lưu rồi nhưng reload vẫn logout | Mục "Auth state" trong `.cursor/rules/auth-jwt.mdc` của FE repo |

> **Khuyến nghị**: Đừng chỉ đọc mục 11. Sau khi chạy được, đọc lại mục 4 (Flow tích hợp đầy đủ) + mục 7 (Bảng lỗi) để hiểu edge case production (CSRF, popup blocked, refresh token, ...) trước khi merge PR.
