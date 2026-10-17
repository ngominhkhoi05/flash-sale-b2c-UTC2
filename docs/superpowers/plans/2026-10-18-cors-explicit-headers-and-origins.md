# Plan: CORS preflight — explicit headers & origins

> Created: 2026-10-18 | Author: Cursor Agent | Branch: `fix/cors-explicit-headers-and-origins`
> Related: FE team report 2026-10-18 about Google OAuth2 POST không xuất hiện trong Network

## 1. Context

- **Vấn đề**: Google OAuth2 login từ FE bị fail — POST `/api/v1/auth/google` không xuất hiện trong Network (browser nuốt request do preflight fail).
- **Root cause** (`CorsConfig.java`): dùng `setAllowedOriginPatterns(origins)` + `setAllowedHeaders(List.of("*"))` + `setAllowCredentials(true)`. Theo W3C CORS spec, wildcard (`*`) cho headers KHÔNG hợp lệ khi credentials=true → browser reject preflight ngay tại client.
- **FE confirm 2026-10-18**: 4 origin cần allow (`https://vibe-mart-utc2.canluaz.io.vn`, `http://localhost:3000`, `http://localhost:3001`, `http://127.0.0.1:3000`). Production env hiện có thêm `http://180.93.137.28` (IP VPS).
- **Workflow mode**: full 6-step (branch + plan + test + sửa + commit + báo cáo). User duyệt có viết unit test.

## 2. Scope

### Files thay đổi (3 files)

| File | Loại | Mô tả |
|---|---|---|
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/config/CorsConfig.java` | Sửa | 3 chỗ wildcard → explicit; wildcard `*` env → sentinel `"null"` |
| `src/test/java/com/b2c/flash_sale_b2c_UTC2/config/CorsConfigTest.java` | Tạo mới | 5 test case verify config tuân thủ spec |
| `docs/superpowers/plans/2026-10-18-cors-explicit-headers-and-origins.md` | Tạo mới | Plan file này |

### Files KHÔNG đổi

| File | Lý do |
|---|---|
| `src/main/java/com/b2c/flash_sale_b2c_UTC2/config/SecurityConfig.java` | Đã permitAll OPTIONS + `/api/v1/auth/**` đúng |
| `src/main/resources/application.yaml` | Đã đọc `${CORS_ALLOWED_ORIGINS:...}` đúng |
| `src/main/resources/application-prod.yaml` | Đã đọc `${CORS_ALLOWED_ORIGINS:...}` đúng |
| `src/test/resources/application-test.yaml` | Set `"*"` chỉ test, không ảnh hưởng prod |
| `src/test/java/com/b2c/flash_sale_b2c_UTC2/common/test/TestCorsConfig.java` | `@Primary` test config riêng, dùng wildcard OK cho test |

## 3. Tham khảo

- `.cursor/rules/27-testing.mdc` (BẮT BUỘC test khi đổi code logic)
- `.cursor/rules/30-git-workflow.mdc` (9-step workflow + branch naming EN)
- `.cursor/rules/04-java-25.mdc` (Java 25 syntax)
- `.cursor/rules/33-task-checklist.mdc` (Bước 0.0 → Bước 6)
- W3C CORS spec: https://fetch.spec.whatwg.org/#cors-protocol

## 4. Subplan (8 subplan tuần tự)

| # | Subplan | Loại | Thời gian |
|---|---|---|---|
| 1 | Tạo branch + plan file | Setup | 2 phút |
| 2 | Audit file (Bước 5.0): logic vs copy | Audit | 2 phút |
| 3 | Viết test `CorsConfigTest.java` (RED) | Test | 5 phút |
| 4 | Sửa `CorsConfig.java` (GREEN) | Code | 5 phút |
| 5 | Chạy `gradlew test --tests CorsConfigTest` | Verify | 2 phút |
| 6 | Chạy `gradlew test` toàn bộ | Verify | 5 phút |
| 7 | Chạy `gradlew build -x test` | Verify | 3 phút |
| 8 | Auto-commit (Conventional Commit EN) | Commit | 2 phút |

**Mỗi subplan độc lập**, chạy tuần tự (không parallel — subplan 3 phải FAIL trước khi subplan 4 sửa).

## 5. Test strategy

### Unit test (Bước 5.1 — RED trước)

File: `src/test/java/com/b2c/flash_sale_b2c_UTC2/config/CorsConfigTest.java`

5 test case:

1. `headersShouldNotContainWildcard` — Headers KHÔNG chứa `"*"` (W3C spec)
2. `headersShouldContainRequiredForFE` — Headers chứa `content-type`, `x-user-id`, `authorization`
3. `originsShouldBeExactNotPattern` — Origins trả về exact match, không phải pattern
4. `wildcardEnvShouldBecomeNullSentinel` — Env `CORS_ALLOWED_ORIGINS=*` → `"null"` (không phải `"*"`)
5. `credentialsShouldBeTrue` — `getAllowCredentials()` = true

### Manual verify (Bước 6, sau khi deploy)

```bash
# 1. Verify preflight (browser sẽ gửi trước POST)
curl -i -X OPTIONS \
  "https://vibe-mart-utc2.canluaz.io.vn/api/v1/auth/google" \
  -H "Origin: https://vibe-mart-utc2.canluaz.io.vn" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type,x-user-id,accept" \
  --max-time 10
# Expected: 200/204 + 4 header CORS (không wildcard)

# 2. Verify POST từ host (giống browser)
curl -i -X POST "https://vibe-mart-utc2.canluaz.io.vn/api/v1/auth/google" \
  -H "Origin: https://vibe-mart-utc2.canluaz.io.vn" \
  -H "Content-Type: application/json" \
  -d '{"code":"4/0A_DUMMY","redirectUri":"https://vibe-mart-utc2.canluaz.io.vn/auth/google/callback"}'
# Expected: 400 + JSON (vì code dummy)

# 3. Verify container (nếu FE build lại)
docker exec flash-sale-b2c-frontend sh -c 'grep -r "auth/google" /app/.next/static/chunks/ 2>/dev/null | head -3'
```

## 6. Risk

| Risk | Mức | Mitigation |
|---|---|---|
| `setAllowedOrigins` thay vì `setAllowedOriginPatterns` có thể block origin typo trong env | Thấp | Env hiện có 4 origin rõ ràng |
| Test `TestCorsConfig` xung đột | Rất thấp | Test profile KHÔNG load `CorsConfig` (`@Profile("!test & !test-local")`) |
| Một domain FE tương lai không có trong env | Trung bình | Báo user bổ sung, không hard-code |

## 7. Rollback

`git revert <commit-hash>` + rebuild image + redeploy (~5 phút).

## 8. Ước lượng thời gian

~28 phút (chưa tính rebuild + redeploy ~10 phút).

## 9. Lịch sử thay đổi

- v1 (2026-10-18): Plan ban đầu.
