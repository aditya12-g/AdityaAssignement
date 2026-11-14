# API reference

All endpoints are served from the application origin. Protected endpoints require:

```http
Authorization: Bearer <JWT>
```

Interactive API documentation: `/swagger-ui.html`. The machine-readable OpenAPI document is `/v3/api-docs`.

## Authentication

### `POST /api/auth/register`

Creates a pending account and sends a verification code. Country is derived from the request IP.

```json
{
  "username": "northstar1",
  "email": "north_star@example.com",
  "password": "Strong!123",
  "userType": "Standard User"
}
```

Returns `202 Accepted`. The dev profile includes `developmentOtp`; non-dev profiles send the code by SMTP and omit that field. In the dev profile, the `Administrator` choice is enabled for local machine testing; it is disabled outside dev.

### `POST /api/auth/verify-otp`

```json
{ "email": "north_star@example.com", "otp": "123456" }
```

Returns a confirmation message. Invalid, expired, or unknown challenges return `400`.
Five consecutive invalid codes invalidate that pending challenge.

### `POST /api/auth/login`

```json
{ "email": "north_star@example.com", "password": "Strong!123" }
```

On success returns the JWT, token type, username, role label, and `Login successful`. Invalid credentials return `401` with `{"message":"Login Failed"}`.

### `GET /api/auth/me`

Returns the current authenticated user's username and role.

### `POST /api/auth/logout`

Revokes the presented JWT and returns `"<Username> Logged Out"`. That token cannot be used for `/api/dashboard`, `/api/products`, or other protected endpoints afterward.

### `GET /api/auth/error`

Returns HTTP 401 and the task's `Login Failed` message.

## User and role routes

### `GET /api/dashboard`

Requires authentication. Returns `{"message":"Welcome <Username>"}`.

### `GET /api/settings`

Requires Administrator role. Standard users receive HTTP 403.

## Products

All product routes require authentication. Product fields are `name` (required, up to 120 chars), `description` (up to 1000 chars), `price` (at least 0.01), and `stock` (zero or greater).

| Method | Route | Description |
|---|---|---|
| `GET` | `/api/products?page=0&size=10&sort=name,asc` | List products with pagination and sorting (`content` plus a `page` metadata object) |
| `GET` | `/api/products/{id}` | Read one product |
| `POST` | `/api/products` | Create a product; returns 201 |
| `PUT` | `/api/products/{id}` | Replace editable fields |
| `DELETE` | `/api/products/{id}` | Delete; returns 204 |

Create/update example:

```json
{
  "name": "Field Notebook",
  "description": "Recycled paper, 160 pages",
  "price": 12.5,
  "stock": 35
}
```

## Common errors

Errors use a JSON message, for example `{"message":"Product not found"}`. Validation errors return `400`, blocked signup returns `403`, duplicate registration returns `409`, geolocation/provider failures return `503`, missing/invalid authentication returns `401`, and insufficient role returns `403`.
