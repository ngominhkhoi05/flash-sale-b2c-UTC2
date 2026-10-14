# Handoff cho BE: 2026-10-14 — Cần làm rõ shape ProductDetail

## Context

FE đã fix defensive cho 7 component `product-detail/` (5 commit trên branch `fix/product-detail-commitments-shape`).
Mọi trang `/products/[slug]` không còn 500 do `null`/`undefined` runtime.

Tuy nhiên, đây là giải pháp **đối phó**, không phải **giải pháp gốc**.
Mỗi product slug BE trả về shape khác nhau (vd: `tui-xach-mini-3` có `description` nhưng thiếu `descriptionRich`;
`dien-thoai-pro-10` thiếu cả `related` lẫn `bestSellers`). FE không có cách nào phân biệt
"BE quên" vs "BE cố ý trả null".

## Yêu cầu gửi BE

### 1. Confirm contract cho từng field nullable

Các field dưới đây trong `GET /api/v1/products/slug/{slug}` **có thể bị BE trả về `null` hoặc thiếu**:

| Field | Câu hỏi cần BE trả lời |
|---|---|
| `descriptionRich` | Là `string[]` hay `string`? Nếu `string[]`, có thể là `[]` không? Có field `description` riêng không? |
| `features` | Luôn có (mảng ≥ 0) hay có thể thiếu khi chưa nhập? |
| `sizeChart` | Luôn có hay chỉ có khi category có size? |
| `related` | Có khi shop có ≥ 1 sản phẩm khác, ngược lại trả `[]` hay `null`? |
| `bestSellers` | Có khi ngành có sản phẩm bán chạy, ngược lại? |
| `soldCount` / `ratingCount` / `questionCount` | Có khi sản phẩm đã có đơn/đánh giá, ngược lại? |
| `reviews` | Mảng `[]` hay `null` khi chưa có? |
| `ratingAvg` | `0` hay `null` khi chưa có? |
| `storeProfile.ratingCount` | `0` hay `null`? |
| `review.mediaUrls` | Có thể `null` khi review không có ảnh? |
| `variantSelectors.colors` / `sizes` | `[]` hay `null` khi sản phẩm không có variant? |

### 2. Cập nhật OpenAPI / Swagger

Với mỗi field nullable, document rõ trong Swagger:
- Type: `string | null` hay `string | string[]` (union)?
- Mô tả khi nào null (vd: "null khi chưa có đánh giá", "[] khi không có sản phẩm cùng shop").

### 3. Áp dụng quy tắc: trả `[]` cho mảng, `0` cho số, `""` cho chuỗi

Khuyến nghị áp dụng nguyên tắc **"empty over null"**:
- Field kiểu mảng → trả `[]` khi rỗng, **không bao giờ** trả `null`.
- Field kiểu số → trả `0` khi chưa có, **không bao giờ** trả `null` (trừ khi cần phân biệt "đã đo" vs "chưa đo" — lúc đó cần ghi rõ).
- Field kiểu chuỗi → trả `""` khi rỗng.

Lý do: FE TypeScript strict luôn treat field kiểu mảng là `T[]` không phải `T[] | null`. Trả `null` buộc FE phải defensive hoặc cast — tốn effort và dễ bug.

## Lợi ích khi làm theo hướng dẫn này

- **FE**: Bỏ được defensive guard → code gọn, dễ đọc hơn.
- **BE**: Document rõ contract → onboarding dev mới nhanh hơn.
- **Cả 2**: Ít bug "500 cả trang" vì 1 field missing.

## Out of scope (cho handoff này)

- Cấu trúc `BackendProductCommitment` (đã align ở commit `280a142`).
- Cấu trúc `tierVariationConfigs` (đã align ở commit trước đó).
- Endpoint `/api/v1/products/slug/{slug}` (BE confirm trả ProductDetail hay DTO khác).

## Test lại

Sau khi BE update, FE có thể:
1. Giữ defensive guard làm safety net.
2. Hoặc refactor dần về `T[]` thuần khi BE confirm `[]` thay vì `null`.

## Liên hệ

- Branch: `fix/product-detail-commitments-shape` (5 commit, 7 component đã fix)
- PR URL: _(sẽ có sau khi push)_
- FE owner: gửi Slack/email
