# Hướng Dẫn FE Tích Hợp Thanh Toán ZaloPay - Flash Sale B2C UTC2

> **Phiên bản**: 1.0
> **Ngày**: 2026-10-15
> **Backend**: `flash-sale-b2c-UTC2` (Spring Boot, ZaloPay Sandbox Open API v2)
> **Frontend**: `flash-sale-b2c-fe` (Next.js 16, React 19)
> **Đường dẫn file này**: `docs/fe-zalopay-integration-guide.md`

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Flow thanh toán](#2-flow-thanh-toán)
3. [API endpoints](#3-api-endpoints)
4. [Request/Response schema](#4-requestresponse-schema)
5. [Callback webhook (server-to-server)](#5-callback-webhook-server-to-server)
6. [Mã nguồn mẫu](#6-mã-nguồn-mẫu)
7. [Xử lý UI cho QR + Web/App deeplink](#7-xử-lý-ui-cho-qr--webapp-deeplink)
8. [Query fallback khi miss callback](#8-query-fallback-khi-miss-callback)
9. [Error codes](#9-error-codes)
10. [Quy ước và lưu ý](#10-quy-ước-và-lưu-ý)

---

## 1. Tổng quan

Backend tích hợp **ZaloPay Sandbox** theo Open API v2. Toàn bộ flow tuân thủ:

| Thông tin | Giá trị |
|---|---|
| ZaloPay endpoint (create) | `https://sb-openapi.zalopay.vn/v2/create` |
| ZaloPay endpoint (query) | `https://sb-openapi.zalopay.vn/v2/query` |
| HTTP method (create/query) | `POST` (form-urlencoded) |
| Signature | HMAC-SHA256, key1 cho create/query, key2 cho callback |
| Currency | VND (long, không có phần thập phân) |
| App ID (sandbox) | `2554` |
| Callback URL | `https://panther-flatness-unzip.ngrok-free.dev/api/v1/payments/zalopay/callback` |

### 1.1 Đặc điểm quan trọng

- **QR code trả về dạng `data:image/png;base64,...`** (không phải URL ảnh) — FE render thẳng bằng `<img src={qrCodeUrl}>`.
- **Payment URL là deeplink ZaloPay** — `order_url` mở app ZaloPay (mobile) hoặc redirect đến ZaloPay web (desktop).
- **Server callback xử lý tự động** — FE không cần (và **không nên**) gọi lại callback từ phía client.
- **Idempotency** — Backend verify `payment.status` trước khi update; gọi callback 2 lần chỉ update 1 lần.
- **Auth** — Tất cả API thanh toán yêu cầu JWT Bearer; callback là public (không cần auth, có MAC thay thế).

### 1.2 Order status flow

```text
PENDING_PAYMENT (sau checkout)
   |
   |-- POST /api/v1/orders/{id}/payment/zalopay --> PENDING (payment)
   |
   |<-- ZaloPay callback --> CONFIRMED (order, payment.status = SUCCESS)
   |
   |-- Seller ship --> SHIPPED
   |-- Buyer complete --> COMPLETED
   |-- Timeout 5 phút --> CANCELLED_TIMEOUT
```

---

## 2. Flow thanh toán

### 2.1 Happy path (user thanh toán thành công)

```text
1. Buyer checkout --> Order tạo với status PENDING_PAYMENT
2. FE gọi POST /api/v1/orders/{orderId}/payment/zalopay
3. Backend gọi ZaloPay /v2/create --> nhận order_url + qr_code
4. Backend lưu Payment(status=PENDING) trong DB
5. Backend trả PaymentResponse cho FE (có order_url, qr_code)
6. FE hiển thị QR + nút "Mở ZaloPay"
7. User quét QR / click deeplink --> thanh toán trong app ZaloPay
8. ZaloPay gọi webhook callback --> Backend update Payment(SUCCESS) + Order(CONFIRMED)
9. Backend broadcast WS PAYMENT_STATUS_CHANGED --> FE update UI real-time
10. FE polling GET /api/v1/orders/{id}/payment hoặc nhận WS event --> hiển thị "Đã thanh toán"
```

### 2.2 Cancel / Timeout path

```text
1. Buyer click "Huỷ" trên trang thanh toán --> gọi API huỷ order
2. Hoặc: 5 phút timeout --> backend scheduler cancel order + restore stock Flash Sale
3. Payment giữ nguyên status PENDING; nếu ZaloPay callback đến SAU khi order CANCELLED_TIMEOUT
   --> backend check order.status, bỏ qua, KHÔNG transition ngược
```

---

## 3. API endpoints

### 3.1 Tạo yêu cầu thanh toán ZaloPay

```http
POST /api/v1/orders/{id}/payment/zalopay
Authorization: Bearer <access_token>
```

| Thuộc tính | Giá trị |
|---|---|
| Method | `POST` |
| URL | `/api/v1/orders/{orderId}/payment/zalopay` |
| Access | Authenticated (Buyer sở hữu Order) |
| Request body | Không cần |
| Response | `ApiResponse<PaymentResponse>` |

**Status codes:**

| Code | Ý nghĩa |
|---|---|
| 200 | Tạo yêu cầu thanh toán thành công |
| 401 | Token invalid/expired |
| 403 | Order không thuộc về user hiện tại |
| 404 | Order không tồn tại |
| 409 | Order không ở trạng thái PENDING_PAYMENT (đã confirm/cancel) |
| 500 | ZaloPay gateway lỗi (return_code != 1) |

### 3.2 Xem trạng thái thanh toán

```http
GET /api/v1/orders/{id}/payment
Authorization: Bearer <access_token>
```

**Response:** `ApiResponse<PaymentResponse>` — luôn trả về 200, kể cả khi chưa có payment (status = `PENDING`, paymentMethod = `UNPAID`).

### 3.3 Query fallback ZaloPay (sync thủ công)

```http
POST /api/v1/orders/{id}/payment/zalopay/query
Authorization: Bearer <access_token>
```

Dùng khi client bị miss callback (network, WS lỗi, v.v.). Backend gọi ZaloPay `/v2/query` để đồng bộ.

### 3.4 Callback webhook (public, server-to-server)

```http
POST /api/v1/payments/zalopay/callback
Content-Type: application/x-www-form-urlencoded
```

| Thuộc tính | Giá trị |
|---|---|
| Method | `POST` |
| URL | `/api/v1/payments/zalopay/callback` |
| Access | **Public** (không cần JWT, verify bằng MAC) |
| Request body | Form-urlencoded: `data=<json>`, `mac=<hmac>`, `type=<int>` |
| Response | JSON `{ "return_code": 1, "return_message": "Success" }` |

> **Lưu ý**: Endpoint này **chỉ dành cho ZaloPay server gọi**, FE không bao giờ gọi trực tiếp. Xem mục 5 để hiểu cách backend xử lý.

---

## 4. Request/Response schema

### 4.1 `PaymentResponse` (response duy nhất cho cả 3 API)

```typescript
interface PaymentResponse {
  id: number | null;              // ID payment trong DB (null nếu chưa tạo)
  orderId: number;
  orderCode: string;              // vd: "ORD_2026_001234"
  paymentMethod: 'ZALOPAY' | 'COD' | 'UNPAID';
  transactionCode: string | null; // ZaloPay app_trans_id, vd: "ZP1697356800000_42"
  amount: number;                 // VND, BigDecimal → number (vd: 150000)
  qrCodeUrl: string | null;       // data:image/png;base64,iVBORw0KGgo... (chỉ có khi ZALOPAY)
  paymentUrl: string | null;      // ZaloPay deeplink (chỉ có khi ZALOPAY)
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  paidAt: string | null;          // ISO 8601 (Instant), null nếu chưa thanh toán
  createdAt: string;              // ISO 8601 (Instant)
}
```

**Wrapper chuẩn** (mọi API trong project):

```typescript
interface ApiResponse<T> {
  success: boolean;
  code: number;       // HTTP status
  message: string;    // Tiếng Việt, hiển thị cho user
  data: T;
  timestamp: string;  // ISO 8601
}
```

### 4.2 Ví dụ response thành công (tạo ZaloPay)

```json
{
  "success": true,
  "code": 200,
  "message": "Tạo yêu cầu thanh toán ZaloPay thành công",
  "data": {
    "id": 123,
    "orderId": 42,
    "orderCode": "ORD_2026_001234",
    "paymentMethod": "ZALOPAY",
    "transactionCode": "ZP1697356800000_42",
    "amount": 150000,
    "qrCodeUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...==",
    "paymentUrl": "https://sb-openapi.zalopay.vn/pay?token=abc123...",
    "status": "PENDING",
    "paidAt": null,
    "createdAt": "2026-10-15T10:30:00.000Z"
  },
  "timestamp": "2026-10-15T10:30:00.000Z"
}
```

### 4.3 Ví dụ response khi chưa có payment

```json
{
  "success": true,
  "code": 200,
  "message": "Lấy thông tin thanh toán thành công",
  "data": {
    "id": null,
    "orderId": 42,
    "orderCode": "ORD_2026_001234",
    "paymentMethod": "UNPAID",
    "transactionCode": null,
    "amount": 150000,
    "qrCodeUrl": null,
    "paymentUrl": null,
    "status": "PENDING",
    "paidAt": null,
    "createdAt": "2026-10-15T10:25:00.000Z"
  },
  "timestamp": "2026-10-15T10:30:00.000Z"
}
```

---

## 5. Callback webhook (server-to-server)

### 5.1 ZaloPay gọi callback khi nào?

Sau khi user thanh toán trong app ZaloPay, ZaloPay server sẽ **POST** đến `callback-url` đã đăng ký. Backend xử lý hoàn toàn tự động, **FE không cần làm gì**.

### 5.2 Backend xử lý callback (FE không cần biết chi tiết, nhưng nên hiểu)

```text
1. Nhận POST từ ZaloPay
2. Verify MAC = HMAC-SHA256(data, key2)
3. Parse JSON data --> lấy app_trans_id, zp_trans_id, amount
4. Tìm Payment theo transaction_code (= app_trans_id)
   - Không tìm thấy --> return_code=0 (log error)
5. Nếu Payment.status = SUCCESS (idempotent)
   --> return_code=1, return_message="Success" (KHÔNG update lại)
6. Nếu PENDING --> set status=SUCCESS, paidAt=now
7. Nếu Order.status = PENDING_PAYMENT --> set CONFIRMED
8. Broadcast WS event PAYMENT_STATUS_CHANGED đến user
9. Trả về ZaloPay: { return_code: 1, return_message: "Success" }
```

### 5.3 Vì sao FE không nên gọi callback?

- **MAC verification** — FE không biết `key2` (secret), không thể tạo valid MAC.
- **Public endpoint, signed** — ZaloPay cần trust rằng chỉ server backend mới có secret để verify.
- **Backend đã idempotent** — Gọi callback 2 lần không gây lỗi, nhưng FE không có lý do gọi.

### 5.4 Khi nào FE nên "đợi" callback?

- FE **không cần poll liên tục**. Cách tốt nhất là **lắng nghe WS event** `PAYMENT_STATUS_CHANGED`.
- Nếu WS chưa connect hoặc miss, FE dùng **polling GET `/payment`** mỗi 3-5 giây trong tối đa 5 phút (timeout của order).
- Sau 5 phút không nhận được event → hiển thị "Đang chờ thanh toán, vui lòng kiểm tra lại sau".

---

## 6. Mã nguồn mẫu

### 6.1 Tạo thanh toán ZaloPay

```typescript
// src/features/checkout/api/payment.api.ts
import { apiFetch } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/types';
import type { PaymentResponse } from './payment.types';

const BASE = (id: number) => `/api/v1/orders/${id}/payment`;

export const createZaloPayPayment = async (orderId: number): Promise<PaymentResponse> => {
  const resp = await apiFetch<ApiResponse<PaymentResponse>>(`${BASE(orderId)}/zalopay`, {
    method: 'POST',
  });
  return resp.data;
};

export const getOrderPayment = async (orderId: number): Promise<PaymentResponse> => {
  const resp = await apiFetch<ApiResponse<PaymentResponse>>(`${BASE(orderId)}`, {
    method: 'GET',
  });
  return resp.data;
};

export const queryZaloPayPayment = async (orderId: number): Promise<PaymentResponse> => {
  const resp = await apiFetch<ApiResponse<PaymentResponse>>(`${BASE(orderId)}/zalopay/query`, {
    method: 'POST',
  });
  return resp.data;
};
```

### 6.2 TanStack Query hooks

```typescript
// src/features/checkout/hooks/useZaloPayPayment.ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createZaloPayPayment,
  getOrderPayment,
  queryZaloPayPayment,
} from '../api/payment.api';
import { useEffect } from 'react';
import { usePaymentStatusWs } from './usePaymentStatusWs';

export const useCreateZaloPayPayment = (orderId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => createZaloPayPayment(orderId),
    onSuccess: (data) => {
      qc.setQueryData(['order-payment', orderId], data);
    },
  });
};

export const useOrderPayment = (orderId: number, options?: { polling?: boolean }) => {
  const qc = useQueryClient();
  const wsEvent = usePaymentStatusWs(orderId);

  // WS event arrived → invalidate query to refetch
  useEffect(() => {
    if (wsEvent) qc.invalidateQueries({ queryKey: ['order-payment', orderId] });
  }, [wsEvent, orderId, qc]);

  return useQuery({
    queryKey: ['order-payment', orderId],
    queryFn: () => getOrderPayment(orderId),
    enabled: !!orderId,
    // Polling fallback nếu WS miss (mỗi 3s, tối đa 5 phút)
    refetchInterval: options?.polling ? 3000 : false,
    refetchIntervalInBackground: false,
    staleTime: 0,
  });
};

export const useQueryZaloPayPayment = (orderId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => queryZaloPayPayment(orderId),
    onSuccess: (data) => {
      qc.setQueryData(['order-payment', orderId], data);
    },
  });
};
```

### 6.3 WebSocket hook (PAYMENT_STATUS_CHANGED)

```typescript
// src/features/checkout/hooks/usePaymentStatusWs.ts
import { useEffect, useState } from 'react';
import { useStompClient } from '@/lib/ws/StompContext';

export interface PaymentStatusEvent {
  orderId: number;
  orderCode: string;
  paymentMethod: 'ZALOPAY' | 'COD';
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  occurredAt: string;
}

export const usePaymentStatusWs = (orderId: number): PaymentStatusEvent | null => {
  const client = useStompClient();
  const [event, setEvent] = useState<PaymentStatusEvent | null>(null);

  useEffect(() => {
    if (!client?.connected || !orderId) return;

    // Subscribe queue riêng cho order này
    const sub = client.subscribe(
      `/user/queue/orders/${orderId}/payment-status`,
      (message) => {
        try {
          const body = JSON.parse(message.body) as PaymentStatusEvent;
          setEvent(body);
        } catch (e) {
          console.error('[WS] Failed to parse payment event', e);
        }
      }
    );

    return () => sub.unsubscribe();
  }, [client?.connected, orderId]);

  return event;
};
```

### 6.4 UI component cho trang thanh toán

```tsx
// src/features/checkout/PaymentZaloPayPanel.tsx
'use client';

import { useState, useEffect } from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useCreateZaloPayPayment, useOrderPayment } from '../hooks/useZaloPayPayment';
import { formatVND } from '@/lib/utils/format';

interface Props {
  orderId: number;
  orderAmount: number;
}

export function PaymentZaloPayPanel({ orderId, orderAmount }: Props) {
  const t = useTranslations('checkout.payment');
  const router = useRouter();

  // Polling bật khi đang ở trang thanh toán, fallback khi WS miss
  const { data: payment, isLoading } = useOrderPayment(orderId, { polling: true });
  const { mutate: createPayment, isPending: isCreating } = useCreateZaloPayPayment(orderId);

  // Auto-create payment khi user vào trang (nếu chưa có)
  useEffect(() => {
    if (!payment || payment.paymentMethod === 'UNPAID') {
      createPayment();
    }
  }, [payment?.paymentMethod]);

  // Khi thanh toán thành công → redirect sang trang order detail
  useEffect(() => {
    if (payment?.status === 'SUCCESS') {
      const timer = setTimeout(() => router.push(`/orders/${orderId}`), 1500);
      return () => clearTimeout(timer);
    }
  }, [payment?.status, orderId, router]);

  if (isLoading || isCreating) {
    return (
      <Card className="p-6 flex flex-col items-center gap-4">
        <Skeleton className="h-64 w-64" />
        <Skeleton className="h-4 w-32" />
      </Card>
    );
  }

  // Đã thanh toán
  if (payment?.status === 'SUCCESS') {
    return (
      <Card className="p-6 flex flex-col items-center gap-4 text-success">
        <CheckCircle2 className="h-16 w-16" />
        <h2 className="text-xl font-semibold">{t('success.title')}</h2>
        <p className="text-muted-foreground">
          {t('success.amount', { amount: formatVND(payment.amount) })}
        </p>
        <Button onClick={() => router.push(`/orders/${orderId}`)}>
          {t('success.viewOrder')}
        </Button>
      </Card>
    );
  }

  // Đang chờ thanh toán
  if (payment?.status === 'PENDING' && payment.qrCodeUrl) {
    return (
      <Card className="p-6 flex flex-col items-center gap-4">
        <h2 className="text-xl font-semibold">{t('zalopay.scanQr')}</h2>
        <p className="text-muted-foreground">
          {t('zalopay.amount', { amount: formatVND(payment.amount) })}
        </p>

        {/* QR Code: dùng next/image với unoptimized vì là data URL */}
        <Image
          src={payment.qrCodeUrl}
          alt={t('zalopay.qrAlt')}
          width={256}
          height={256}
          unoptimized
          className="rounded-lg border"
        />

        {/* Deeplink button mở ZaloPay app */}
        {payment.paymentUrl && (
          <Button asChild className="w-full max-w-sm">
            <a href={payment.paymentUrl} target="_blank" rel="noopener noreferrer">
              {t('zalopay.openApp')}
            </a>
          </Button>
        )}

        <p className="text-sm text-muted-foreground text-center">
          {t('zalopay.autoConfirmHint')}
        </p>
      </Card>
    );
  }

  return null;
}
```

---

## 7. Xử lý UI cho QR + Web/App deeplink

### 7.1 QR Code rendering

`qrCodeUrl` là **data URL** (`data:image/png;base64,...`), không phải HTTP URL. Khi dùng `next/image`:

```tsx
<Image
  src={payment.qrCodeUrl}
  alt="QR thanh toán ZaloPay"
  width={256}
  height={256}
  unoptimized   // BẮT BUỘC vì next/image không tối ưu được data URL
  priority      // Load ngay vì là element chính của trang
/>
```

> **Lưu ý**: Không cần (và không nên) upload QR lên Cloudinary/CDN. Data URL render trực tiếp trong `<img>` mà không cần network request thêm.

### 7.2 Deeplink ZaloPay

`paymentUrl` (ZaloPay `order_url`) có 2 dạng:

| Platform | Behavior |
|---|---|
| Mobile có ZaloPay | Mở app ZaloPay, tự động nhảy vào flow thanh toán |
| Mobile không có ZaloPay | Fallback sang web (ZaloPay web payment) |
| Desktop | Redirect sang trang thanh toán ZaloPay trên browser |

**Khuyến nghị UX**:
- Hiển thị QR code là **primary action** (user dùng mobile banking/ ZaloPay mobile khác để quét).
- Deeplink button là **secondary action** cho user đang dùng ZaloPay trên cùng device.

### 7.3 Polling vs WebSocket

| Cơ chế | Khi nào dùng |
|---|---|
| **WebSocket** (`PAYMENT_STATUS_CHANGED`) | Default khi user đang ở trang thanh toán. Realtime, tiết kiệm request. |
| **Polling** (GET `/payment` mỗi 3s) | Fallback khi WS chưa connect / bị disconnect. Auto-stop khi nhận WS event. |
| **Manual query** (POST `/zalopay/query`) | User click "Tôi đã thanh toán rồi" → force sync với ZaloPay gateway. |

### 7.4 Timeout handling

Order PENDING_PAYMENT có TTL 5 phút (tính từ lúc tạo). Sau 5 phút:
- Backend scheduler (`processExpiredReservations`) cancel order + restore stock Flash Sale
- FE nên hiển thị **countdown timer** 5 phút cho user biết
- Khi hết giờ → polling sẽ trả về order status `CANCELLED_TIMEOUT` → redirect về trang checkout

```tsx
// Countdown component (tham khảo common/Countdown.tsx trong repo FE)
import { useCountdown } from '@/hooks/useCountdown';

const { minutes, seconds } = useCountdown(order.expiresAt);
return (
  <p className="text-warning">
    {t('expiresIn', { minutes, seconds })}
  </p>
);
```

---

## 8. Query fallback khi miss callback

### 8.1 Khi nào cần query?

- WS không connect được (network issue, corporate proxy block WS).
- User tắt tab rồi mở lại trước khi callback đến.
- User click nút "Tôi đã thanh toán rồi, kiểm tra lại".

### 8.2 Cách dùng

```tsx
// Trên trang payment, thêm button "Tôi đã thanh toán"
const { mutate: queryPayment, isPending } = useQueryZaloPayPayment(orderId);

<Button variant="outline" onClick={() => queryPayment()} disabled={isPending}>
  {isPending ? <Spinner /> : t('manualSync')}
</Button>
```

Backend sẽ gọi ZaloPay `/v2/query`, nếu `return_code=1` và amount khớp → update `Payment(SUCCESS)` + `Order(CONFIRMED)`.

### 8.3 Rate limiting

Không nên gọi query liên tục (mỗi giây). Khuyến nghị:
- **Auto polling** mỗi 3-5 giây, tối đa 100 lần (5 phút / 3s = 100 requests).
- **Manual query** chỉ khi user click button (1 lần / 5 giây).

---

## 9. Error codes

| Code HTTP | Error code (backend) | Ý nghĩa | FE xử lý |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Request sai format | Hiển thị message validation |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_TOKEN_EXPIRED` | Token invalid/expired | Redirect login (xem `fe-auth-routing-guide.md`) |
| 403 | `ORDER_NOT_OWNER` | Order không thuộc user hiện tại | Ẩn nút thanh toán |
| 404 | `ORDER_NOT_FOUND` | Order không tồn tại | 404 page |
| 409 | `ORDER_INVALID_STATUS_TRANSITION` | Order không ở PENDING_PAYMENT (đã confirm/cancel) | Hiển thị "Đơn hàng đã được xử lý", refresh data |
| 500 | `INTERNAL_SERVER_ERROR` (từ ZaloPay return_code != 1) | ZaloPay gateway lỗi | Retry hoặc chuyển sang COD |

**Lưu ý**: Khi backend gọi ZaloPay `/v2/create` mà ZaloPay trả `return_code != 1`, backend throw `INTERNAL_SERVER_ERROR` (HTTP 500). FE hiển thị generic "Có lỗi xảy ra, vui lòng thử lại hoặc chọn phương thức thanh toán khác".

---

## 10. Quy ước và lưu ý

### 10.1 BẮT BUỘC

- **Luôn dùng `apiFetch` wrapper** ở `src/lib/api/client.ts` — KHÔNG fetch trực tiếp `fetch('/api/v1/...')`.
- **JWT token** lấy từ `localStorage.getItem('fs_access_token')` — wrapper tự động thêm `Authorization: Bearer ...`.
- **Wrapper response** — Mọi API trả về `ApiResponse<T>`, cần unwrap `.data` trước khi dùng.
- **Error handling** — `apiFetch` throw `ApiError` với `code` từ backend. Map code → i18n key ở `error.<CODE>`.
- **`unoptimized` cho QR** — Data URL không tối ưu được qua `next/image` optimizer.
- **`priority` cho QR** — QR là element chính của trang payment, cần load ngay.

### 10.2 KHÔNG

- **KHÔNG gọi callback URL từ FE** — Endpoint `/api/v1/payments/zalopay/callback` chỉ dành cho ZaloPay server, có MAC verification.
- **KHÔNG poll liên tục** — Dùng WS làm primary, polling làm fallback.
- **KHÔNG upload QR lên Cloudinary** — QR đã là data URL, render trực tiếp.
- **KHÔNG hard-code `key1` / `key2`** — Sandbox keys nằm trong `application-local.yaml` (backend), FE không cần biết.
- **KHÔNG tự tạo `orderUrl` / `qrCode`** — Luôn lấy từ `PaymentResponse` trả về từ backend.
- **KHÔNG dùng `<img>` cho QR** — Dùng `next/image` với `unoptimized` (xem `component-pattern.mdc` mục 10).

### 10.3 Edge cases cần handle

| Tình huống | Xử lý |
|---|---|
| User đóng tab rồi mở lại trước khi callback | Auto-create lại payment (idempotent ở backend, trả về existing payment) |
| User thanh toán 2 lần cùng order | Backend check `Order.status` chỉ transition PENDING_PAYMENT → CONFIRMED 1 lần |
| Network chậm khi ZaloPay callback | Backend retry tự động (ZaloPay retry 3 lần nếu không nhận 200 OK) |
| Backend restart giữa lúc ZaloPay callback | Mất callback, nhưng user có thể dùng "Tôi đã thanh toán" → query sync |
| Sandbox ZaloPay downtime | Backend trả 500, FE hiển thị "Hệ thống thanh toán đang bảo trì, vui lòng thử lại sau" |
| User huỷ trong app ZaloPay | ZaloPay KHÔNG gọi callback cho case cancel; order sẽ timeout ở backend scheduler |

### 10.4 Liên quan

- [`docs/fe-auth-routing-guide.md`](./fe-auth-routing-guide.md) — Auth flow, JWT storage, route guards
- [`docs/fe-ws-integration-guide.md`](./fe-ws-integration-guide.md) — WebSocket kết nối, STOMP destinations
- Backend `AGENTS.md` mục #20 (Payment) + #46.14 (Payment API) — Source of truth cho API contract
- Backend `ZaloPayClient.java` — Logic MAC verification, có thể tham khảo để hiểu cách backend verify callback
- `src/lib/api/client.ts` — `apiFetch` wrapper (xem `api-data.mdc` mục 2-3)

---

## Phụ lục: Sequence Diagram

```text
┌──────┐          ┌──────────┐         ┌──────────┐         ┌──────────┐
│  FE  │          │ Backend  │         │ Database │         │ ZaloPay  │
└──┬───┘          └────┬─────┘         └────┬─────┘         └────┬─────┘
   │  POST /payment/zalopay   │                   │                   │
   ├─────────────────────────>│                   │                   │
   │                          │  Create Payment   │                   │
   │                          │   (status=PENDING)│                   │
   │                          ├──────────────────>│                   │
   │                          │                   │                   │
   │                          │ POST /v2/create                       │
   │                          ├──────────────────────────────────────>│
   │                          │<──────────────────────────────────────┤
   │                          │   order_url + qr_code                 │
   │                          │                   │                   │
   │  PaymentResponse         │                   │                   │
   │<─────────────────────────┤                   │                   │
   │                          │                   │                   │
   │  Hiển thị QR + Button    │                   │                   │
   │                          │                   │                   │
   │  User quét QR / click deeplink                │                   │
   │  (trong ZaloPay app)   │                   │                   │
   │                          │                   │                   │
   │                          │  POST /callback (data, mac)           │
   │                          │<──────────────────────────────────────┤
   │                          │  Verify MAC (HMAC-SHA256)             │
   │                          │  Update Payment(SUCCESS)              │
   │                          ├──────────────────>│                   │
   │                          │  Update Order(CONFIRMED)              │
   │                          ├──────────────────>│                   │
   │                          │  WS broadcast PAYMENT_STATUS_CHANGED  │
   │                          │<─────────────────┤                   │
   │  WS event                │                   │                   │
   │<─────────────────────────┤                   │                   │
   │                          │  Trả {return_code:1, return_message:│
   │                          │     "Success"}                       │
   │                          ├──────────────────────────────────────>│
   │  Hiển thị "Đã thanh toán"                   │                   │
```

---

> **Cập nhật lần cuối**: 2026-10-15
> **Maintainer**: Backend team (`flash-sale-b2c-UTC2`)
> **Liên hệ**: Nếu API thay đổi, cập nhật file này + báo FE team trước ít nhất 1 sprint.
