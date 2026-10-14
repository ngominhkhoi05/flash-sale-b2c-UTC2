# Hướng dẫn cấu hình phân quyền & Auth cho Frontend (FE)

> **Đối tượng**: Đội frontend (lash-sale-b2c-fe) consume API từ backend này.
> **Ngày tạo**: 2026-10-14
> **Stack FE**: Next.js 16.3.8 (App Router) · React 19.2 · TS 5.x · Zustand 5 · TanStack Query 5 · next-intl 4.x
> **Source rule FE** (tham chiếu, không sửa): lash-sale-b2c-fe/.cursor/rules/{auth-jwt.mdc, routing-guards.mdc, state-management.mdc, api-data.mdc, layer-convention.mdc}

---
## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Token storage — 3 nơi lưu, 2 mục đích](#2-token-storage--3-nơi-lưu-2-mục-đích)
3. [Auth state — Zustand persist s_auth](#3-auth-state--zustand-persist-fs_auth)
4. [Client API layer — piFetch + auto refresh](#4-client-api-layer--apifetch--auto-refresh)
5. [Edge guard — middleware.ts](#5-edge-guard--middlewarets)
6. [Server Component guard — lib/auth-guard.ts](#6-server-component-guard--libauth-guardts)
7. [Route groups & URL convention](#7-route-groups--url-convention)
8. [RBAC — Role & Permission](#8-rbac--role--permission)
9. [Login / Logout / Refresh flow](#9-login--logout--refresh-flow)
10. [Mapping ErrorCode từ backend → i18n key](#10-mapping-errorcode-từ-backend--i18n-key)
11. [SessionCookieBridge — đồng bộ token](#11-sessioncookiebridge--đồng-bộ-token)
12. [Redirect sau login + callbackUrl](#12-redirect-sau-login--callbackurl)
13. [Env variables cần có](#13-env-variables-cần-có)
14. [Test matrix cho FE auth](#14-test-matrix-cho-fe-auth)
15. [Lỗi thường gặp & xử lý](#15-lỗi-thường-gặp--xử-lý)
16. [Tài liệu liên quan](#16-tài-liệu-liên-quan)

---

## 1. Tổng quan kiến trúc

FE dùng **3 lớp bảo vệ chồng lên nhau**, theo uth-jwt.mdc + outing-guards.mdc:

`
Browser
  │
  ├── middleware.ts (Edge runtime)
  │     └─ chỉ check cookie có tồn tại → redirect /login nhanh
  │        (KHÔNG verify JWT — edge runtime giới hạn crypto)
  │
  └── Server Component
        └── src/lib/auth-guard.ts
              ├─ đọc cookie httpOnly access_token
              ├─ verify JWT signature (cùng secret với backend)
              └─ check role → redirect nếu sai

Client state ── src/stores/auth.store.ts (Zustand persist "fs_auth")
                  └─ chỉ giữ { user, role, isAuthenticated }
                     KHÔNG lưu token trong store

Token        ── localStorage (client)  +  cookie httpOnly (server)
                  ↕  đồng bộ qua SessionCookieBridge
`

### Tại sao cần 3 lớp

| Lớp | Bảo vệ khỏi | Tại sao |
|---|---|---|
| middleware.ts | User chưa login truy cập /seller, /admin | Edge chạy trước khi render → redirect nhanh, không lộ nội dung |
| uth-guard.ts | User có cookie nhưng hết hạn / role sai | Verify đầy đủ signature, check role chính xác |
| Client state (Zustand) | UI cần biết user hiện tại để ẩn/hiện action | Reactive — header, sidebar, button thay đổi theo role |

### Quy tắc cứng

- **Edge runtime KHÔNG verify JWT** (giới hạn crypto) → middleware.ts chỉ check cookies().get('access_token') có/không.
- **KHÔNG bypass Security** chỉ để test feature.
- **KHÔNG roll JWT verify riêng** ngoài uth-guard.ts (xem 
o-overengineering.mdc).

---

## 2. Token storage — 3 nơi lưu, 2 mục đích

| Token | Nơi lưu | Key | Mục đích | Ai đọc |
|---|---|---|---|---|
| ccess_token | localStorage | s_access_token | Gắn vào Authorization: Bearer ... cho mọi request từ client | piFetch |
| efresh_token | localStorage | s_refresh_token | Dùng để gọi /auth/refresh-token khi access hết hạn | Refresh flow |
| ccess_token (httpOnly cookie) | Cookie | ccess_token | Server Component đọc được qua wait cookies() | uth-guard.ts |

### Tại sao lưu cả localStorage + cookie

- **localStorage**: client JS cần đọc để gắn header. Không thể chỉ dùng cookie vì cookie httpOnly không đọc được từ JS.
- **Cookie httpOnly**: Server Component (RSC) cần đọc để verify trước khi render. Không thể dùng localStorage vì RSC không có localStorage.

### Vòng đời token

`
1. User login → backend trả { accessToken, refreshToken, user }
2. FE lưu localStorage: fs_access_token, fs_refresh_token
3. SessionCookieBridge gọi API set cookie httpOnly access_token
4. auth.store.login() → set { user, role, isAuthenticated }
5. Mỗi request:
   - client → Authorization header từ localStorage
   - server → cookie access_token qua await cookies()
6. Access hết hạn (401) → apiFetch tự refresh 1 lần, retry
7. Refresh fail → auth.store.logout() + clear storage + redirect /login
`

### Quy tắc cứng

- **KHÔNG** lưu token trong Zustand state (chỉ user + role).
- **KHÔNG** log token ra console (xem error-handling.mdc).
- **KHÔNG** để token vào URL (query string).

---

## 3. Auth state — Zustand persist s_auth

File: src/stores/auth.store.ts

`	s
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Role = 'BUYER' | 'SELLER' | 'ADMIN';

interface AuthState {
  user: User | null;
  role: Role | null;
  isAuthenticated: boolean;
  login: (token: string, refresh: string, user: User) => void;
  logout: () => void;
  refresh: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      role: null,
      isAuthenticated: false,

      login: (token, refresh, user) => {
        localStorage.setItem('fs_access_token', token);
        localStorage.setItem('fs_refresh_token', refresh);
        set({ user, role: user.role, isAuthenticated: true });
      },

      logout: () => {
        localStorage.removeItem('fs_access_token');
        localStorage.removeItem('fs_refresh_token');
        set({ user: null, role: null, isAuthenticated: false });
      },

      refresh: async () => { /* gọi apiFetch('/auth/refresh-token') */ },
    }),
    {
      name: 'fs_auth',
      // CHỈ persist { user, role, isAuthenticated } — KHÔNG token
      partialize: (state) => ({
        user: state.user,
        role: state.role,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
`

### Hook wrapper: src/hooks/useAuth.ts

`	s
export function useAuth() {
  return useAuthStore();
}
`

- Hook wrapper mỏng — KHÔNG thêm business logic.
- Dùng useAuth() trong component (không gọi trực tiếp useAuthStore).

### Khi nào reset state

| Trigger | Hành động |
|---|---|
| User click logout | uth.store.logout() → clear storage + cookie + redirect /login |
| Refresh token fail | uth.store.logout() (xem mục 4) |
| User đổi role (admin gán role mới) | Re-login hoặc refresh user info |
| Token expired & không refresh được | uth.store.logout() |

---

## 4. Client API layer — `apiFetch` + auto refresh

File: `src/lib/api/client.ts`

```ts
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T>;
```

### Trách nhiệm

1. **Base URL** từ `process.env.NEXT_PUBLIC_API_BASE_URL`.
2. **Auto attach** `Authorization: Bearer <access_token>` từ `localStorage`.
3. **Unwrap** response envelope `{ data: T, success, code, message, timestamp }` → trả `T`.
4. **Throw `ApiError`** nếu `success === false` hoặc HTTP status ≥ 400.
5. **401 → auto refresh 1 lần** → retry request gốc.
6. **Retry idempotent 1 lần** cho network error.
7. **Idempotency-Key**: tự attach `Idempotency-Key: <uuid>` cho mọi mutation.

### Flow xử lý 401

```
apiFetch('/api/v1/orders/me')
  |
  |-- request với Authorization: Bearer <access_token>
  |
  |-- response 401 (token expired)
  |
  |-- mutex.refresh() ----- 1 promise dùng chung cho mọi request đang chờ
  |   |
  |   |-- gọi POST /api/v1/auth/refresh-token { refreshToken }
  |   |
  |   |-- success -> lưu accessToken mới + retry request gốc
  |   |
  |   |-- fail -> auth.store.logout() + clear storage + redirect /login
  |
  |-- return response (hoặc throw ApiError nếu vẫn fail)
```

### Mutex pattern cho refresh

```ts
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(API_BASE + "/api/v1/auth/refresh-token", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: localStorage.getItem("fs_refresh_token") }),
      });
      if (!res.ok) throw new Error("refresh failed");
      const data = await res.json();
      localStorage.setItem("fs_access_token", data.data.accessToken);
      return data.data.accessToken;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}
```

### Quy tắc cứng

- **CHỈ retry 1 lần** — không retry 2 lần.
- Refresh chạy **tuần tự**, không parallel (mutex).
- Nếu request gốc fail ngay cả sau refresh → throw `ApiError` cho component xử lý.
- **KHÔNG retry** 4xx errors khác 401.
- **KHÔNG retry** 409, 422, 429 (xem `error-handling.mdc` mục 8).

### ApiError class — `src/lib/api/errors.ts`

```ts
export class ApiError extends Error {
  constructor(
    public code: string,        // AUTH_INVALID_TOKEN, ORDER_NOT_FOUND, ...
    message: string,            // message tiếng Việt từ backend
    public status: number,      // HTTP status
    public details?: unknown,   // field-level errors (cho form validation)
  ) { super(message); }
}
```

---

## 5. Edge guard — `middleware.ts`

File: `src/middleware.ts` (root)

```ts
import { NextResponse, type NextRequest } from "next/server";
import createIntlMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

const intlMiddleware = createIntlMiddleware(routing);

const SELLER_PREFIX = "/seller";
const ADMIN_PREFIX = "/admin";
const AUTH_ONLY = ["/login", "/register"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get("access_token")?.value;

  // 1. Seller/Admin — chỉ cần có cookie
  if (pathname.startsWith(SELLER_PREFIX) || pathname.startsWith(ADMIN_PREFIX)) {
    if (!token) {
      const url = new URL("/login", request.url);
      url.searchParams.set("callbackUrl", pathname);
      return NextResponse.redirect(url);
    }
  }

  // 2. Login/Register — nếu đã login → redirect về home (hoặc callbackUrl)
  if (AUTH_ONLY.includes(pathname)) {
    if (token) {
      const callbackUrl = request.nextUrl.searchParams.get("callbackUrl") || "/";
      return NextResponse.redirect(new URL(callbackUrl, request.url));
    }
  }

  return intlMiddleware(request);
}

export const config = {
  matcher: ["/((?!api|_next|.*\\..*).*)"],
};
```

### Quy tắc cứng

- **CHỈ check sự tồn tại cookie** — KHÔNG verify JWT signature ở middleware.
- **Verify đầy đủ ở Server Component** qua `auth-guard.ts`.
- Middleware chỉ chạy cho route KHÔNG phải `api/`, `_next/`, file tĩnh.
- `localePrefix: "never"` (xem `i18n.mdc`) — URL không có `/vi/` hay `/en/`.

### Lý do không verify JWT ở middleware

| Lý do | Giải thích |
|---|---|
| Edge runtime | Next 16 edge runtime giới hạn — không có sẵn `crypto.subtle` đầy đủ |
| Bundle size | Verify JWT cần `jose` lib (~30KB), làm middleware bundle to |
| Latency | Verify trùng lặp giữa middleware + RSC → tốn thời gian |
| Phân vai rõ | Middleware = fast redirect; RSC = full check |

---

## 6. Server Component guard — `lib/auth-guard.ts`

File: `src/lib/auth-guard.ts`

```ts
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { jwtVerify } from "jose";

const JWT_SECRET = new TextEncoder().encode(process.env.JWT_SECRET!);

export async function requireAuth() {
  const store = await cookies();  // Next 16: async
  const token = store.get("access_token")?.value;

  if (!token) {
    redirect("/login?callbackUrl=" + encodeURIComponent("/current-path"));
  }

  try {
    const { payload } = await jwtVerify(token, JWT_SECRET, { algorithms: ["HS256"] });
    return {
      user: {
        id: Number(payload.sub),
        username: payload.username as string,
        role: payload.role as "BUYER" | "SELLER" | "ADMIN",
      },
    };
  } catch {
    redirect("/login");
  }
}

export async function requireRole(role: "SELLER" | "ADMIN") {
  const { user } = await requireAuth();
  if (user.role !== role) redirect("/");
  return { user };
}
```

### Dùng trong Server Component

```tsx
// src/app/(seller)/seller/dashboard/page.tsx
import { requireRole } from "@/lib/auth-guard";

export default async function SellerDashboardPage() {
  const { user } = await requireRole("SELLER");
  return <Dashboard userId={user.id} />;
}
```

```tsx
// src/app/(buyer)/orders/page.tsx
import { requireAuth } from "@/lib/auth-guard";

export default async function OrdersPage() {
  const { user } = await requireAuth();
  return <OrderList userId={user.id} />;
}
```

### Quy tắc cứng

- **PHẢI `await cookies()`** (Next 16: cookies API async).
- Verify với cùng `JWT_SECRET` như backend (env `JWT_SECRET` ≥ 32 ký tự, HS256).
- Token hết hạn → redirect `/login` (KHÔNG hiện lỗi).
- Role sai → redirect `/` (KHÔNG hiện 403 — tránh leak thông tin).
- **KHÔNG** để page render trước khi check auth.

### Verify JWT — options

| Cách | Ưu | Nhược |
|---|---|---|
| `jose` lib (recommended) | Edge-compatible, async API | Bundle ~30KB |
| Gọi backend API `/auth/verify` | Không cần JWT secret ở FE | Thêm network round-trip |
| Decode base64 (KHÔNG dùng) | Không cần secret | Không verify được signature → unsafe |

**Khuyến nghị**: dùng `jose` vì cùng secret với backend, không cần network call.

---

## 7. Route groups & URL convention

### URL convention (xem `routing-guards.mdc` mục 1)

```
OK  /login
OK  /register
OK  /products
OK  /products/abc-123
OK  /cart
OK  /checkout
OK  /orders
OK  /orders/abc-123
OK  /flash-sales
OK  /flash-sales/slot-1
OK  /seller/dashboard
OK  /seller/products
OK  /admin/dashboard
OK  /admin/stores

NO  /dang-nhap            (tiếng Việt có dấu - đã bỏ dấu rồi cũng không dùng)
NO  /vi/login              (locale prefix)
NO  /en/login
NO  /order_detail          (snake_case)
```

- **Tiếng Anh không dấu** cho path segment.
- **Kebab-case** cho multi-word.
- **Locale KHÔNG xuất hiện** trong URL (`localePrefix: "never"`).

### Route groups (xem `routing-guards.mdc` mục 2)

```
src/app/
|-- layout.tsx              # root layout (Providers, font, i18n)
|-- page.tsx                # homepage (/)
|-- (auth)/                 # group: auth pages, no header
|   |-- layout.tsx          # center card, no header/footer
|   |-- login/page.tsx
|   |-- register/page.tsx
|-- (buyer)/                # group: buyer-facing, share layout
|   |-- layout.tsx          # header + footer
|   |-- page.tsx            # (/) - vì (buyer) là group không ảnh hưởng URL
|   |-- products/
|   |   |-- page.tsx        # /products
|   |   |-- [id]/page.tsx   # /products/abc-123
|   |-- cart/page.tsx
|   |-- checkout/page.tsx
|   |-- orders/
|       |-- page.tsx        # /orders
|       |-- [id]/page.tsx   # /orders/abc-123
|-- (seller)/seller/        # /seller/**
|   |-- dashboard/page.tsx
|   |-- products/page.tsx
|   |-- flash-sales/page.tsx
|   |-- orders/page.tsx
|   |-- vouchers/page.tsx
|-- (admin)/admin/          # /admin/**
|   |-- dashboard/page.tsx
|   |-- stores/page.tsx
|   |-- categories/page.tsx
|   |-- vouchers/page.tsx
|   |-- flash-sales/page.tsx
|   |-- users/page.tsx
|-- api/                    # KHONG tao (xem no-overengineering.mdc)
```

- Route group `(name)` KHÔNG ảnh hưởng URL — chỉ tổ chức layout.
- `(auth)` group: layout center card, không header/footer.
- `(buyer)` / `(seller)` / `(admin)` groups: layout có header + footer + role-based menu.

### Layout theo role

```tsx
// src/app/(seller)/seller/layout.tsx
import { requireRole } from "@/lib/auth-guard";
import { SellerSidebar } from "@/components/layout/SellerSidebar";
import { SellerHeader } from "@/components/layout/SellerHeader";

export default async function SellerLayout({ children }: { children: React.ReactNode }) {
  const { user } = await requireRole("SELLER");
  return (
    <div>
      <SellerHeader user={user} />
      <SellerSidebar />
      <main>{children}</main>
    </div>
  );
}
```

---

## 8. RBAC — Role & Permission

### Role authority (xem backend `AGENTS.md` mục #46.2)

| Role | Authority | Dùng ở FE |
|---|---|---|
| BUYER | `ROLE_BUYER` | `requireAuth()` cho `/cart`, `/orders`, `/checkout` |
| SELLER | `ROLE_SELLER` | `requireRole("SELLER")` cho `/seller/**` |
| ADMIN | `ROLE_ADMIN` | `requireRole("ADMIN")` cho `/admin/**` |

### Permission code (atomic, từ backend)

Backend trả permission code qua `/auth/login` response (nếu user có nhiều role), vd:

```json
{
  "data": {
    "user": { "id": 123, "username": "buyer1", "role": "BUYER" },
    "accessToken": "...",
    "refreshToken": "..."
  }
}
```

Trong tương lai nếu backend trả thêm permissions:

```json
{
  "data": {
    "user": { ... },
    "permissions": ["order:create", "product:read", "voucher:claim"]
  }
}
```

→ FE lưu vào store, dùng để conditional UI.

### Conditional UI pattern

```tsx
"use client";
import { useAuth } from "@/hooks/useAuth";

export function BuyButton() {
  const { role, isAuthenticated } = useAuth();
  if (!isAuthenticated) return <LoginPrompt />;
  if (role !== "BUYER") return null;  // chỉ BUYER mới thấy
  return <Button>Mua ngay</Button>;
}
```

### Khi nào check role

| Vị trí | Check gì | Tại sao |
|---|---|---|
| `middleware.ts` | Cookie có tồn tại | Fast redirect, edge-friendly |
| `auth-guard.ts` (RSC) | JWT signature + role | Full verify, chính xác |
| Component (client) | `useAuth().role` | Reactive UI (ẩn/hiện button) |
| API call | Server enforce lại (403) | Backend là source of truth cuối cùng |

> **Backend là source of truth** — FE check chỉ để UX tốt hơn. Nếu FE check sai nhưng backend reject vẫn là an toàn.

---

## 9. Login / Logout / Refresh flow

### Login flow

```
1. User submit form /login
2. POST /api/v1/auth/login { usernameOrEmail, password }
3. Backend trả { accessToken, refreshToken, user }
4. Lưu localStorage: fs_access_token, fs_refresh_token
5. SessionCookieBridge gọi API set cookie access_token (httpOnly, sameSite=Lax)
6. auth.store.login() -> set { user, role, isAuthenticated: true }
7. Redirect:
   - ADMIN  -> /admin
   - SELLER -> /seller
   - BUYER  -> callbackUrl hoặc /
```

### Logout flow

```
1. User click "Đăng xuất"
2. POST /api/v1/auth/logout (invalidate refresh_token phía server)
3. Clear localStorage: fs_access_token, fs_refresh_token
4. Clear cookie: access_token (qua API hoặc document.cookie)
5. auth.store.logout() -> reset state
6. Redirect /login
```

### Refresh flow (khi access hết hạn)

```
1. apiFetch gọi request bất kỳ
2. Response 401 (token expired)
3. Mutex: chỉ 1 request refresh tại 1 thời điểm
4. POST /api/v1/auth/refresh-token { refreshToken }
5. Backend trả { accessToken: mới }
6. Lưu localStorage: fs_access_token (mới)
7. SessionCookieBridge update cookie
8. Retry request gốc 1 lần
9. Nếu response 200 -> return data
10. Nếu response vẫn 401 -> logout + redirect /login
```

### Idempotency-Key cho mutation

Mọi `POST` / `PUT` / `PATCH` / `DELETE` → tự attach `Idempotency-Key: <uuid>` (xem `api-data.mdc` mục 9 + backend `AGENTS.md` mục #34).

```ts
import { v4 as uuidv4 } from "uuid";

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() || "GET";
  const isMutation = ["POST", "PUT", "PATCH", "DELETE"].includes(method);

  const headers = new Headers(init?.headers);
  if (isMutation && !headers.has("Idempotency-Key")) {
    headers.set("Idempotency-Key", uuidv4());
  }
  // ...
}
```

---

## 10. Mapping ErrorCode từ backend → i18n key

Backend trả error qua `ApiResponse.error.code` (xem backend `AGENTS.md` mục #38):

```json
{
  "success": false,
  "code": 401,
  "message": "Token không hợp lệ hoặc đã hết hạn",
  "data": null,
  "timestamp": "2026-10-14T10:00:00.000Z"
}
```

Nhưng trong header `X-Error-Code` hoặc trong `data.code` (tuỳ implementation), vd:

```json
{
  "data": {
    "code": "AUTH_INVALID_TOKEN",
    "message": "Token không hợp lệ hoặc đã hết hạn"
  }
}
```

### Mapping ở FE

File: `messages/vi.json` + `messages/en.json`

```json
// messages/vi.json
{
  "error": {
    "AUTH_INVALID_CREDENTIALS": "Tên đăng nhập hoặc mật khẩu không đúng",
    "AUTH_INVALID_TOKEN": "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
    "AUTH_TOKEN_EXPIRED": "Phiên đăng nhập đã hết hạn",
    "AUTH_FORBIDDEN": "Bạn không có quyền truy cập",
    "USER_NOT_FOUND": "Không tìm thấy người dùng",
    "PRODUCT_OUT_OF_STOCK": "Sản phẩm đã hết hàng",
    "FLASH_SALE_OUT_OF_STOCK": "Đã hết lượt mua Flash Sale",
    "CART_EMPTY": "Giỏ hàng trống",
    "ORDER_NOT_OWNER": "Đơn hàng không thuộc về bạn",
    "PAYMENT_DUPLICATE": "Giao dịch đã được xử lý",
    "INTERNAL_ERROR": "Lỗi hệ thống, vui lòng thử lại sau"
  }
}
```

### Dùng trong component

```tsx
import { ApiError } from "@/lib/api/errors";
import { useTranslations } from "next-intl";

const t = useTranslations("error");

if (err instanceof ApiError) {
  toast.error(t(err.code)); // key trùng với code
}
```

### Lỗi 401 đặc biệt

| Backend code | Xử lý ở FE |
|---|---|
| `AUTH_INVALID_TOKEN` | Tự refresh, không toast |
| `AUTH_TOKEN_EXPIRED` | Tự refresh, không toast |
| `AUTH_FORBIDDEN` (403) | Toast + redirect về home |
| `AUTH_INVALID_CREDENTIALS` (login) | Toast inline trong form |

### Khi backend thêm ErrorCode mới

1. Backend thêm vào enum `*ErrorCode` (xem backend `AGENTS.md` mục #7).
2. FE thêm key tương ứng vào `messages/vi.json` + `messages/en.json`.
3. Nếu chưa có key → fallback về `err.message` từ backend (tiếng Việt).

---

## 11. SessionCookieBridge — đồng bộ token

File: `src/components/layout/SessionCookieBridge.tsx`

### Trách nhiệm

- Component ẩn, mount 1 lần ở root layout.
- Đồng bộ 2 chiều `localStorage.access_token` <-> cookie `access_token`:
  - Khi `localStorage` đổi → update cookie (gọi API `/session/cookie`).
  - Khi cookie bị xoá (vd expired) → clear `localStorage` + logout.

### Pseudo-code

```tsx
"use client";
import { useEffect } from "react";
import { useAuthStore } from "@/stores/auth.store";

export function SessionCookieBridge() {
  useEffect(() => {
    const syncToCookie = () => {
      const token = localStorage.getItem("fs_access_token");
      if (token) {
        // Set cookie httpOnly qua API (server sẽ set httpOnly header)
        // Xem mục "Cách set cookie" bên dưới
      }
    };

    const handleStorage = (e: StorageEvent) => {
      if (e.key === "fs_access_token" && !e.newValue) {
        useAuthStore.getState().logout();
      }
    };

    window.addEventListener("storage", handleStorage);
    syncToCookie();

    return () => window.removeEventListener("storage", handleStorage);
  }, []);

  return null;
}
```

### Cách set cookie httpOnly

Cookie httpOnly **KHÔNG thể set từ JavaScript** (`document.cookie`). Cần 1 trong 2 cách:

| Cách | Setup | Ưu | Nhược |
|---|---|---|---|
| Backend endpoint set cookie | `POST /api/v1/auth/login` set `Set-Cookie: access_token=...; HttpOnly; SameSite=Lax` | Đơn giản, native | Cookie cùng domain với API |
| RSC route handler | RSC set cookie qua `cookies().set()` | FE control | Cần thêm route |

**Khuyến nghị**: backend set cookie trong response `/auth/login` + `/auth/refresh-token`. FE không cần làm gì thêm.

> **KHÔNG tạo `app/api/**` route** ở FE (xem `no-overengineering.mdc`). Cookie phải set từ backend.

---

## 12. Redirect sau login + callbackUrl

### Pattern

```
1. User chưa login truy cập /seller/products
2. middleware.ts check cookie -> không có
3. Redirect: /login?callbackUrl=/seller/products
4. User login thành công
5. Login page đọc callbackUrl -> redirect về /seller/products
```

### Implement ở login page

```tsx
// src/app/(auth)/login/page.tsx
import { redirect } from "next/navigation";
import { LoginForm } from "@/features/auth/components/LoginForm";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ callbackUrl?: string }>;
}) {
  const { callbackUrl } = await searchParams;  // Next 16: async
  return <LoginForm callbackUrl={callbackUrl || "/"} />;
}
```

```tsx
// features/auth/components/LoginForm.tsx
"use client";
import { useAuthStore } from "@/stores/auth.store";

export function LoginForm({ callbackUrl }: { callbackUrl: string }) {
  const login = useAuthStore((s) => s.login);
  const router = useRouter();

  const onSubmit = async (data: LoginInput) => {
    const res = await authApi.login(data);
    login(res.accessToken, res.refreshToken, res.user);
    router.push(callbackUrl);  // hoặc default theo role
  };

  return <form onSubmit={handleSubmit(onSubmit)}>...</form>;
}
```

### Default redirect theo role

```ts
const DEFAULT_REDIRECT: Record<Role, string> = {
  BUYER: "/",
  SELLER: "/seller",
  ADMIN: "/admin",
};

const redirectTo = callbackUrl || DEFAULT_REDIRECT[user.role];
```

### Quy tắc cứng

- **KHÔNG hard-code redirect URL** — lấy từ `callbackUrl` hoặc default theo role.
- **Validate `callbackUrl`** là relative path (chống open redirect attack):
  ```ts
  const safeCallback = callbackUrl.startsWith("/") ? callbackUrl : "/";
  ```
- **Encode/decode** callbackUrl đúng cách khi truyền qua URL params.

---

## 13. Env variables cần có

File: `flash-sale-b2c-fe/.env.local` (KHÔNG commit)

```bash
# Backend API
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# JWT secret (chỉ dùng cho verify trong RSC — phải GIỐNG backend)
JWT_SECRET=your-shared-secret-min-32-chars

# Optional: bật mock khi dev
NEXT_PUBLIC_USE_MOCK=false
```

### Lưu ý

- `NEXT_PUBLIC_*` → expose cho client (chỉ dùng cho non-secret).
- `JWT_SECRET` (không có prefix) → chỉ server đọc được (dùng trong `auth-guard.ts`).
- `JWT_SECRET` PHẢI giống backend (cùng thuật toán HS256).
- KHÔNG commit `.env.local` — đã có trong `.gitignore` (xem `no-overengineering.mdc` mục cuối).

---

## 14. Test matrix cho FE auth

| Test case | Setup | Expect |
|---|---|---|
| Truy cập `/` chưa login | Cookie rỗng | Render homepage (public) |
| Truy cập `/login` chưa login | Cookie rỗng | Render form login |
| Truy cập `/login` đã login | Cookie có | Redirect `/` (hoặc callbackUrl) |
| Truy cập `/seller` không cookie | Cookie rỗng | Redirect `/login?callbackUrl=/seller` |
| Truy cập `/seller` cookie BUYER | Cookie có nhưng role sai | RSC: redirect `/` |
| Truy cập `/seller` cookie SELLER | Cookie SELLER hợp lệ | Render dashboard |
| Truy cập `/admin` không cookie | Cookie rỗng | Redirect `/login?callbackUrl=/admin` |
| Login thành công | Form hợp lệ | Set token + cookie + redirect theo role |
| Login sai password | Form sai | Inline error (không toast) |
| Token expired trong request | Đang dùng -> 401 | Tự refresh -> retry -> nếu fail -> logout |
| Refresh fail | refreshToken invalid | Logout + redirect `/login` |
| Logout | Click button | Clear storage + cookie + redirect `/login` |
| Open 2 tab, logout 1 tab | storage event | Tab kia tự logout |

### Unit test mẫu

```ts
// src/stores/auth.store.test.ts
import { useAuthStore } from "./auth.store";

beforeEach(() => {
  localStorage.clear();
  useAuthStore.setState({ user: null, role: null, isAuthenticated: false });
});

test("login sets state and localStorage", () => {
  const user = { id: 1, username: "buyer1", role: "BUYER" as const };
  useAuthStore.getState().login("access-token", "refresh-token", user);

  expect(useAuthStore.getState().user).toEqual(user);
  expect(useAuthStore.getState().role).toBe("BUYER");
  expect(localStorage.getItem("fs_access_token")).toBe("access-token");
  expect(localStorage.getItem("fs_refresh_token")).toBe("refresh-token");
});

test("logout clears state and localStorage", () => {
  useAuthStore.getState().login("a", "b", { id: 1, role: "BUYER" });
  useAuthStore.getState().logout();

  expect(useAuthStore.getState().user).toBeNull();
  expect(localStorage.getItem("fs_access_token")).toBeNull();
});
```

### Integration test mẫu

```ts
// src/middleware.test.ts (nếu setup)
import { middleware } from "./middleware";
import { NextRequest } from "next/server";

test("redirect /seller to /login when no cookie", () => {
  const req = new NextRequest(new URL("http://localhost/seller/dashboard"));
  const res = middleware(req);
  expect(res.status).toBe(307);
  expect(res.headers.get("Location")).toContain("/login?callbackUrl=/seller/dashboard");
});
```

---

## 15. Lỗi thường gặp & xử lý

### 1. Middleware chạy nhưng vẫn vào được `/seller` không có cookie

**Nguyên nhân**: Cookie không được set vì backend không trả `Set-Cookie` header.

**Fix**:
- Backend `/auth/login` PHẢI trả `Set-Cookie: access_token=...; HttpOnly; SameSite=Lax; Path=/`.
- Check CORS: backend phải cho phép credentials (`Access-Control-Allow-Credentials: true`).
- Domain phải match (vd FE ở `localhost:3000`, API ở `localhost:8080` → cookie cần `Domain=localhost` hoặc proxy qua cùng domain).

### 2. Sau khi refresh, lần request sau lại 401

**Nguyên nhân**: Refresh thành công nhưng `localStorage` không được update.

**Fix**:
```ts
// Trong apiFetch, sau khi refresh:
localStorage.setItem("fs_access_token", newToken);
// KHONG quên gọi SessionCookieBridge sync cookie
```

### 3. RSC đọc cookie ra `undefined`

**Nguyên nhân**: Quên `await cookies()` (Next 16 cookies API async).

**Fix**:
```ts
// SAI
const store = cookies();
const token = store.get("access_token")?.value;

// DUNG
const store = await cookies();
const token = store.get("access_token")?.value;
```

### 4. Login thành công nhưng header vẫn hiện "Đăng nhập"

**Nguyên nhân**: `useAuth()` không reactive — header đọc `localStorage` trực tiếp thay vì `useAuthStore`.

**Fix**:
```tsx
// SAI
const isLoggedIn = !!localStorage.getItem("fs_access_token");

// DUNG
const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
```

### 5. Infinite redirect loop `/login` -> `/` -> `/login`

**Nguyên nhân**: Cookie hết hạn nhưng `localStorage` vẫn có token cũ. `auth.store` nghĩ user vẫn login.

**Fix**:
- Khi 401 → `auth.store.logout()` ngay lập tức (xem mục 4).
- Hoặc verify JWT signature ở `auth.store.refresh()` → nếu fail → logout.

### 6. `Cannot read properties of null (reading "get")` ở `cookies()`

**Nguyên nhân**: Gọi `cookies()` trong Client Component hoặc trong Server Action không hợp lệ.

**Fix**:
- `cookies()` chỉ dùng trong Server Component / Route Handler / Server Action.
- Trong Client Component, đọc qua `useAuthStore` thay vì cookie.

### 7. CORS error khi gọi `/auth/refresh-token`

**Nguyên nhân**: Backend CORS config không cho phép credential.

**Fix**:
- Backend `CorsConfig` cần `setAllowCredentials(true)`.
- FE cần `fetch(url, { credentials: "include" })` (mặc định `apiFetch` đã làm).
- Backend không được set `Access-Control-Allow-Origin: *` nếu dùng credentials — phải whitelist origin cụ thể.

### 8. Refresh loop vô tận

**Nguyên nhân**: Refresh response 200 nhưng request gốc vẫn 401 → tiếp tục refresh.

**Fix**:
- Sau refresh, set header `Authorization` mới cho request retry.
- Nếu refresh response không có `accessToken` → logout ngay.
- Mutex đảm bảo chỉ 1 refresh chạy tại 1 thời điểm.

---

## 16. Tài liệu liên quan

### FE rules (xem trong `flash-sale-b2c-fe/.cursor/rules/`)

| File | Áp dụng cho |
|---|---|
| `auth-jwt.mdc` | Token storage, refresh, SessionCookieBridge, login/logout flow |
| `routing-guards.mdc` | URL convention, route group, middleware, auth-guard |
| `state-management.mdc` | Zustand pattern, auth.store, query key factory |
| `api-data.mdc` | apiFetch, ApiError, Endpoints, Idempotency-Key |
| `layer-convention.mdc` | Cấu trúc src/, dependency direction |
| `i18n.mdc` | Locale, next-intl, error code → message mapping |
| `error-handling.mdc` | Throw vs catch vs toast, console log, Error Boundary |
| `no-overengineering.mdc` | KHÔNG tạo `app/api/**`, không roll JWT verify riêng |
| `docs-sync.mdc` | Source of truth #1 là `docs/CLAUDE.md` (read-only) |

### Backend contracts (xem trong `flash-sale-b2c-UTC2/`)

| File | Nội dung |
|---|---|
| `AGENTS.md` mục #30 | Security — JWT, RBAC, env |
| `AGENTS.md` mục #46.2 | RBAC role/permission |
| `AGENTS.md` mục #46.3 | Auth endpoint contract |
| `AGENTS.md` mục #34 | Idempotency convention |
| `AGENTS.md` mục #36 | WebSocket (realtime) |
| `AGENTS.md` mục #38 | Error code convention |
| `docs/flashsale_order_contract.md` | Flash Sale ↔ Order contract |
| `docs/api/api-document.md` | API chi tiết từng module |

### Cross-repo

| Repo | Vai trò |
|---|---|
| `flash-sale-b2c-UTC2` | Backend Spring Boot (repo này) |
| `flash-sale-b2c-fe` | Next.js frontend |
| `deploy-b2c-utc2` | Docker compose, nginx, scripts |

### Khi cần sync giữa FE ↔ BE

- Thêm/sửa endpoint → cập nhật `AGENTS.md` mục #46 + `docs/api/api-document.md` + file rule FE liên quan.
- Thêm/sửa ErrorCode → cập nhật `AGENTS.md` mục #7 + `messages/vi.json` + `messages/en.json`.
- Thêm/sửa role/permission → cập nhật `AGENTS.md` mục #30 + #46.2 + rule FE `routing-guards.mdc` + `auth-jwt.mdc`.
- Thêm/sửa WS event → cập nhật `AGENTS.md` mục #36 + rule FE `websocket-realtime.mdc`.

---

> **Ghi chú cuối**: File này là **living document** cho team FE. Cập nhật khi:
> - Backend thêm/sửa endpoint auth.
> - Backend thêm role/permission mới.
> - FE thêm/sửa rule auth.
> - Phát hiện edge case mới.
>
> **Liên hệ sync**: Ping team backend khi sửa rule FE, hoặc ngược lại.
