# 📧 Báo cáo gửi Backend — Vấn đề CORS khi Frontend gọi API

> **Người gửi**: Team FE (flash-sale-b2c-fe)
> **Người nhận**: Team Backend (flash-sale-b2c-UTC2)
> **Ngày**: 2026-10-10
> **Mức độ**: 🔴 **Blocker** — không test được flow login, không thể dev tích hợp FE↔BE
> **Liên quan**: Endpoint `POST /api/v1/auth/login` (và mọi endpoint `/api/v1/**` chưa verify)

---

## 1. Tóm tắt sự cố

Khi Frontend chạy ở **`http://localhost:3000`** (Next.js dev server) gọi sang Backend ở **`http://180.93.137.28`**, browser **block toàn bộ request** vì **CORS preflight (OPTIONS) thất bại** — Backend không trả header `Access-Control-Allow-Origin` cho origin `http://localhost:3000`.

CORS là cơ chế bảo mật của **browser** — Frontend **không thể bypass** được. Chỉ Backend mới có thể cấp quyền bằng cách trả header `Access-Control-Allow-Origin` cho `http://localhost:3000`.

---

## 2. Repro steps (100% tái hiện)

```text
1. Start FE:  cd flash-sale-b2c-fe && npm run dev   # http://localhost:3000
2. Mở browser:  http://localhost:3000/dang-nhap
3. Nhập email/password bất kỳ → bấm "Đăng nhập"
4. Mở DevTools → Network → thấy:
   - Request:  OPTIONS http://180.93.137.28/api/v1/auth/login  (preflight)
   - Status:   (no response / failed)
   - Console:  "blocked by CORS policy: ... No 'Access-Control-Allow-Origin' header"
   - Request:  POST http://180.93.137.28/api/v1/auth/login     (actual)
   - Status:   net::ERR_FAILED
```

---

## 3. Log thật từ browser console

```text
Access to fetch at 'http://180.93.137.28/api/v1/auth/login' from origin 
'http://localhost:3000' has been blocked by CORS policy: 
Response to preflight request doesn't pass access control check: 
No 'Access-Control-Allow-Origin' header is present on the requested resource.

POST http://180.93.137.28/api/v1/auth/login  net::ERR_FAILED
```

---

## 4. Phân tích nguyên nhân

| Thành phần | Trạng thái |
|---|---|
| Frontend gửi request đúng format | ✅ Đúng (Content-Type: application/json, body JSON) |
| Frontend đọc `NEXT_PUBLIC_API_URL` đúng | ✅ `http://180.93.137.28` (đã set trong `.env`) |
| Backend có CORS config | ✅ Có file `CorsConfig` riêng (xem `docs/api-document.md` mục 27.5) |
| Backend whitelist `http://localhost:3000` | ❌ **KHÔNG có trong allowlist hiện tại** |

**Kết luận**: `CorsConfig` của Backend đã tồn tại nhưng allowlist hiện tại **chưa bao gồm origin của FE dev** (`http://localhost:3000`). Cần thêm vào.

---

## 5. Yêu cầu fix (đề xuất)

Backend cần cập nhật `CorsConfig` (hoặc `application-*.yaml` tương ứng) để allow origin `http://localhost:3000`.

**Cấu hình đề xuất** (theo Spring Boot 3.x + Spring Security 6):

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Dev origins (cần thêm http://localhost:3000)
        cfg.setAllowedOrigins(List.of(
            "http://localhost:3000",      // FE dev (Next.js)
            "http://localhost:3001",
            "http://127.0.0.1:3000"
        ));

        // Nếu cần support nhiều FE domain qua biến môi trường:
        // cfg.setAllowedOriginPatterns(List.of(
        //     "http://localhost:*",
        //     "https://*.vibemart.vn"
        // ));

        cfg.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        cfg.setAllowedHeaders(List.of("*"));

        cfg.setExposedHeaders(List.of(
            "Authorization",
            "X-Request-Id",
            "Idempotency-Key"
        ));

        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
```

**Hoặc** nếu dùng `application.yaml`:

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:3001
      - http://127.0.0.1:3000
    allowed-methods: "*"
    allowed-headers: "*"
    exposed-headers: "Authorization,X-Request-Id,Idempotency-Key"
    allow-credentials: true
    max-age: 3600
```

---

## 6. Endpoint bị ảnh hưởng (chưa verify hết, cần test sau khi fix)

| Method | Path | Trạng thái |
|---|---|---|
| `POST` | `/api/v1/auth/login` | ❌ Block (đã verify) |
| `POST` | `/api/v1/auth/register` | ❌ Có thể block (cùng pattern) |
| `POST` | `/api/v1/auth/refresh` | ❌ Có thể block (cùng pattern) |
| `GET`  | `/api/v1/products/**` | ❌ Có thể block (cùng pattern) |
| Mọi endpoint `/api/v1/**` | — | ⚠️ Cần verify sau khi fix |

> **Khuyến nghị**: Apply CORS config global cho `/api/**` thay vì per-endpoint để tránh miss.

---

## 7. Acceptance criteria (Definition of Done)

- [ ] `OPTIONS http://180.93.137.28/api/v1/auth/login` (từ origin `http://localhost:3000`) trả status 200
- [ ] Response có headers:
  - `Access-Control-Allow-Origin: http://localhost:3000`
  - `Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS`
  - `Access-Control-Allow-Headers: *` (hoặc list cụ thể: `Authorization, Content-Type, Idempotency-Key, X-Request-Id`)
  - `Access-Control-Allow-Credentials: true` (nếu FE dùng cookie)
- [ ] `POST /api/v1/auth/login` thực sự thực thi và trả JSON (không còn bị block ở preflight)
- [ ] Test lại với `POST /api/v1/auth/register` và `POST /api/v1/auth/refresh` cũng pass
- [ ] (Optional) Test với browser profile khác (Chrome + Firefox) để chắc chắn không phải browser-specific

---

## 8. Test nhanh từ phía Frontend (sau khi Backend fix)

Mở browser console tại `http://localhost:3000/dang-nhap`, chạy:

```js
fetch('http://180.93.137.28/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'test@example.com', password: 'test123' })
})
.then(r => r.json())
.then(console.log)
.catch(console.error);
```

- ✅ **Pass**: thấy JSON response (status 200 hoặc 401 — đều OK vì CORS đã pass)
- ❌ **Fail vẫn còn block ở CORS**: báo lại Backend

---

## 9. Liên hệ & thông tin môi trường

| Mục | Giá trị |
|---|---|
| FE version | Next.js 16.3.8, React 19.2 (branch `feature/auth-login-page`) |
| FE URL dev | `http://localhost:3000` |
| BE URL | `http://180.93.137.28` (đang public) |
| Browser test | Chrome (đã tắt extension katalon) |
| Thời điểm phát hiện | 2026-10-10 ~00:37 |
| Người báo cáo | Team FE (qua chat handoff) |
| File tham chiếu Backend | `docs/api-document.md` mục 27.5 (Security Filter Chain) |

---

## 10. Ghi chú thêm (không liên quan CORS nhưng thấy trong quá trình debug)

Hai vấn đề dưới đây **KHÔNG liên quan Backend**, chỉ note để Backend biết context:

1. **Hydration mismatch warning** trên `<html>` (attribute `katalonextensionid=...`) — do Chrome extension "Katalon Recorder" inject. FE đã xác nhận không phải bug React.
2. **Next/Image aspect-ratio warning** cho logo — FE đã fix ở commit `4c2da68` (sử dụng `width=0 height=0 + sizes + style`).

---

Cảm ơn Backend team đã hỗ trợ. Khi fix xong vui lòng báo lại để FE verify và tiếp tục tích hợp. 🙏
