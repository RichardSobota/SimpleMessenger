# Design Document — Simple Messenger Backend

## Overview

The Simple Messenger Backend is a RESTful API service built around the OpenAPI 3.0.3 contract located at `openapi.yaml`. It exposes two primary resource domains — **Users** and **Messages** — under the versioned base path `/v1`. The service handles:

- User registration (public), profile retrieval, partial update, and deletion
- Directed follower relationships stored on the followed user's record
- Text message creation and retrieval, with paginated listings both globally and per user
- JWT-based bearer authentication enforced on every endpoint except `POST /users`
- Uniform, structured error responses for all 4xx conditions

The design prioritises contract fidelity (every response shape and HTTP status code must match the OpenAPI spec exactly), clear separation between the HTTP layer and the business/persistence layer, and straightforward testability of core logic.

---

## Architecture

The service follows a classic layered architecture:

```
┌──────────────────────────────────────────────┐
│              HTTP / Router Layer             │
│  (route matching, auth filter, req parsing)  │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│              Service Layer                   │
│  (business logic, validation, domain rules)  │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│            Repository / Data Layer           │
│       (CRUD operations against the DB)       │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│                  Database                    │
│    (relational store — e.g. PostgreSQL)      │
└──────────────────────────────────────────────┘
```

### Key architectural decisions

| Decision | Rationale |
|---|---|
| Layered (not hexagonal) | Keeps the implementation straightforward for a spec of this size; all logic is within a single deployable unit |
| Relational database | Follower relationships and paginated queries map naturally to SQL; UUID primary keys align with the spec |
| JWT validation in a middleware/filter | Auth is orthogonal to business logic; a single middleware applied to all routes except `POST /users` avoids repetition |
| Passwords hashed at the service layer | Passwords must never be persisted or returned in plain text |
| Server-assigned UUIDs and timestamps | `id`, `createdAt`, and `sentAt` are always generated server-side; clients cannot supply them |

### Request lifecycle

```
Client Request
    │
    ▼
Auth Middleware (verify JWT — skip for POST /users)
    │  └─ 401 if invalid/missing
    ▼
Route Handler (parse path params, query params, body)
    │  └─ 400 if UUID format invalid or body malformed
    ▼
Service Layer (domain validation, business rules)
    │  └─ 400/403/404/409 as appropriate
    ▼
Repository Layer (DB read/write)
    ▼
Response Serialisation (shape response per OpenAPI schema)
    ▼
Client Response
```

---

## Components and Interfaces

### 1. Auth Middleware

Responsibility: Validate the `Authorization: Bearer <token>` header on every request except `POST /users`.

- Extracts the JWT from the `Authorization` header.
- Verifies the token signature and expiry using a shared secret / public key.
- On failure (missing, malformed, expired, invalid signature) → returns `UnauthorizedError` (401).
- On success → attaches the decoded principal to the request context.

### 2. User Controller / Route Handlers

Handles HTTP concerns for `/users`, `/users/{userId}`, `/users/{userId}/followers`, `/users/{userId}/followers/{followerId}`, and `/users/{userId}/messages`.

| Operation | HTTP | Path | Auth |
|---|---|---|---|
| createUser | POST | /users | Public |
| getUserById | GET | /users/{userId} | Required |
| updateUserById | PATCH | /users/{userId} | Required |
| deleteUserById | DELETE | /users/{userId} | Required |
| addFollower | POST | /users/{userId}/followers | Required |
| removeFollower | DELETE | /users/{userId}/followers/{followerId} | Required |
| getMessagesForUser | GET | /users/{userId}/messages | Required |

Each handler:
1. Validates path parameters are valid UUIDs → 400 if not.
2. Parses and validates the request body where applicable.
3. Delegates to the User Service or Message Service.
4. Serialises the response to the appropriate shape.

### 3. Message Controller / Route Handlers

Handles HTTP concerns for `/messages` and `/messages/{messageId}`.

| Operation | HTTP | Path | Auth |
|---|---|---|---|
| createMessage | POST | /messages | Required |
| getMessages | GET | /messages | Required |
| getMessageById | GET | /messages/{messageId} | Required |
| deleteMessageById | DELETE | /messages/{messageId} | Required |

### 4. User Service

Core business logic for user operations.

```
createUser(firstName, lastName, email, password) → User
  - validate fields
  - check email uniqueness → ConflictError (409) if taken
  - hash password
  - persist; return User (no password field)

getUserById(userId) → User
  - fetch; throw NotFoundError (404) if absent

updateUserById(callerId, userId, patch) → User
  - throw ForbiddenError (403) if callerId ≠ userId
  - validate patch fields; throw ValidationError (400) if empty or invalid
  - check email uniqueness if email supplied → ConflictError (409)
  - apply only supplied fields; return updated User

deleteUserById(userId) → void
  - verify user exists → NotFoundError (404)
  - delete user and all their messages (cascade or explicit delete)

addFollower(userId, followerId) → User
  - verify both exist → NotFoundError (404)
  - prevent self-follow (userId == followerId) → ValidationError (400)
  - idempotent: if already a follower, return current user unchanged
  - add follower; return updated User

removeFollower(userId, followerId) → void
  - verify both users exist → NotFoundError (404)
  - verify follower relationship exists → NotFoundError (404)
  - remove follower
```

### 5. Message Service

Core business logic for message operations.

```
createMessage(userId, messageText) → Message
  - validate fields non-empty → ValidationError (400)
  - verify userId exists → NotFoundError (404)
  - assign UUID id, server-generated sentAt (UTC ISO 8601)
  - persist; return Message with Location header

getMessageById(messageId) → Message
  - fetch; throw NotFoundError (404) if absent

deleteMessageById(messageId) → void
  - verify exists → NotFoundError (404)
  - delete

getMessages(page, pageSize) → PagedMessages
  - clamp/default page=0, pageSize=20 (max 100)
  - return PagedMessages with pagination metadata

getMessagesForUser(userId, page, pageSize) → PagedMessages
  - verify user exists → NotFoundError (404)
  - return only messages by that user, paginated
```

### 6. Validation Utilities

A shared module that:
- Validates UUID format (path params and body fields)
- Validates email format
- Validates string length constraints (firstName, lastName 1–100; password 8–24; messageText ≥ 1)
- Produces `ValidationError` bodies with a `details` array identifying each failing field

### 7. Error Handler

A global error handler that maps domain exceptions to HTTP responses:

| Exception | HTTP Status | Body schema |
|---|---|---|
| ValidationException | 400 | ValidationError |
| UnauthorizedException | 401 | UnauthorizedError |
| ForbiddenException | 403 | ForbiddenError |
| NotFoundException | 404 | NotFoundError |
| ConflictException | 409 | ConflictError |

All responses include `Content-Type: application/json`.

---

## Data Models

### Relational schema

#### `users` table

| Column | Type | Constraints |
|---|---|---|
| id | UUID | PK |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| password_hash | VARCHAR | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL, server default NOW() |

#### `followers` table (join table)

| Column | Type | Constraints |
|---|---|---|
| user_id | UUID | FK → users.id, NOT NULL |
| follower_id | UUID | FK → users.id, NOT NULL |
| PRIMARY KEY | (user_id, follower_id) | — |

A row `(user_id=A, follower_id=B)` means "B follows A" — i.e., B appears in A's `followers` list.

Constraints enforced at DB level:
- `user_id ≠ follower_id` (check constraint, backs up the service-layer self-follow guard)

#### `messages` table

| Column | Type | Constraints |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | FK → users.id, NOT NULL |
| message_text | TEXT | NOT NULL |
| sent_at | TIMESTAMPTZ | NOT NULL, server default NOW() |

Index on `messages(user_id)` to support efficient per-user message retrieval.

### Domain model → API schema mapping

#### User (API response)

```
users row
  + followers (from followers join + users join)
  → {
      id, firstName, lastName, email, createdAt,
      followers: [ UserSummary ]   // each is {id, firstName, lastName, email, createdAt}
    }
```

Password is never included in any outbound User or UserSummary shape.

#### Message (API response)

```
messages row → {
  id, user (= user_id), messageText, sentAt
}
```

#### PagedMessages

```
{
  data: Message[],
  page: int,          // zero-based, as requested
  pageSize: int,
  totalElements: int,
  totalPages: int     // ceil(totalElements / pageSize)
}
```

`totalPages` is always ≥ 1 (even when `totalElements` is 0, return `totalPages: 0`).

### Pagination logic

```
offset = page * pageSize
SELECT ... LIMIT pageSize OFFSET offset

totalPages = ceil(totalElements / pageSize)
           = 0 when totalElements = 0
```

Query parameters default and range:
- `page`: default 0, minimum 0
- `pageSize`: default 20, minimum 1, maximum 100

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property 1: User creation response completeness

*For any* valid `CreateUserRequest` (firstName 1–100 chars, lastName 1–100 chars, valid email, password 8–24 chars), the API SHALL return HTTP 201 with a User body that: contains a well-formed UUID `id`, a valid ISO 8601 `createdAt` timestamp, an empty `followers` array, and does NOT include a `password` field.

**Validates: Requirements 1.1, 1.6, 1.7, 1.9**

---

### Property 2: User creation input validation rejects invalid fields

*For any* `CreateUserRequest` where at least one field violates its constraint (email format invalid, password length < 8 or > 24, firstName or lastName empty or > 100 chars, any required field missing), the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies every offending field.

**Validates: Requirements 1.2, 1.3, 1.4, 1.10**

---

### Property 3: User retrieval returns correct shape with UserSummary followers

*For any* existing User, an authenticated GET to `/users/{userId}` SHALL return HTTP 200 with a User object containing `id`, `firstName`, `lastName`, `email`, `createdAt`, and a `followers` array where each entry is a `UserSummary` — containing only `id`, `firstName`, `lastName`, `email`, `createdAt` — with no nested `followers` field.

**Validates: Requirements 2.1, 2.4**

---

### Property 4: PATCH applies only supplied fields and leaves others unchanged

*For any* existing User and any non-empty subset of valid PATCH fields (`firstName`, `lastName`, `email`), an authenticated PATCH to `/users/{userId}` SHALL update only the fields present in the request body and return HTTP 200; all fields absent from the request body SHALL retain their pre-patch values.

**Validates: Requirements 3.1, 3.6**

---

### Property 5: PATCH input validation rejects invalid field values

*For any* `UpdateUserRequest` where a supplied field violates its constraint (e.g. `firstName` is empty, `email` is not a valid email format), or where the body is entirely empty, the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies the offending field(s).

**Validates: Requirements 3.2, 3.8**

---

### Property 6: Add-follower idempotency

*For any* valid pair of distinct users A and B, calling `POST /users/{A}/followers` with `followerId=B` multiple times SHALL be idempotent: every call returns HTTP 200 with a User body, and after the second (or subsequent) call the `followers` list is identical in content to what it was after the first successful call.

**Validates: Requirements 5.1, 5.5**

---

### Property 7: Remove-follower result and status

*For any* existing follower relationship between users A and B, an authenticated DELETE to `/users/{A}/followers/{B}` SHALL return HTTP 204 with no response body, and a subsequent GET to `/users/{A}` SHALL not include B in the `followers` list.

**Validates: Requirements 6.1, 6.2**

---

### Property 8: Message creation response completeness

*For any* valid `CreateMessageRequest` (non-empty `messageText`, valid UUID `user` referencing an existing User), an authenticated POST to `/messages` SHALL return HTTP 201 with a `Message` body containing a well-formed UUID `id`, the correct `user` UUID, the original `messageText`, and a valid ISO 8601 UTC `sentAt` timestamp, plus a `Location` response header pointing to the new resource.

**Validates: Requirements 7.1, 7.5**

---

### Property 9: Message creation input validation rejects invalid fields

*For any* `CreateMessageRequest` where `messageText` is missing or empty, or `user` is missing, empty, or not a valid UUID format, the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies the offending field.

**Validates: Requirements 7.2, 7.6**

---

### Property 10: Message retrieval returns correct shape

*For any* existing Message, an authenticated GET to `/messages/{messageId}` SHALL return HTTP 200 with a `Message` object containing `id`, `user`, `messageText`, and `sentAt`.

**Validates: Requirements 8.1**

---

### Property 11: Message deletion makes message unretrievable

*For any* existing Message, an authenticated DELETE to `/messages/{messageId}` SHALL return HTTP 204 with no response body, and a subsequent GET to `/messages/{messageId}` SHALL return HTTP 404.

**Validates: Requirements 9.1**

---

### Property 12: Pagination metadata correctness and completeness

*For any* authenticated GET to `/messages` or `/users/{userId}/messages` with any valid `page` (≥ 0) and `pageSize` (1–100), the response SHALL be HTTP 200 with a `PagedMessages` body where: `data` contains at most `pageSize` items; the items in `data` are the correct slice for the given zero-based `page`; `page` and `pageSize` reflect the requested values (or defaults 0 and 20 when omitted); `totalElements` equals the true count of messages in scope; and `totalPages` equals `ceil(totalElements / pageSize)` (or 0 when `totalElements` is 0).

**Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 11.1, 11.3, 11.4, 11.5, 11.6**

---

### Property 13: Per-user message listing contains only that user's messages

*For any* existing User, an authenticated GET to `/users/{userId}/messages` SHALL return a `PagedMessages` response where every `Message` in `data` has its `user` field equal to `userId`.

**Validates: Requirements 11.1**

---

### Property 14: Missing or malformed JWT returns 401 on every protected endpoint

*For any* protected endpoint (all endpoints except `POST /users`) and any request bearing a missing, malformed, expired, or cryptographically invalid `Authorization` header, the API SHALL return HTTP 401 with an `UnauthorizedError` body containing `status: 401`, `error`, and `message` fields.

**Validates: Requirements 12.1, 12.2, 12.3**

---

### Property 15: Error response bodies always match their declared schema

*For any* API response, if the HTTP status is 400, 401, 403, 404, or 409, the response body SHALL conform to the corresponding error schema (`ValidationError`, `UnauthorizedError`, `ForbiddenError`, `NotFoundError`, `ConflictError`) — each containing at minimum the `status` integer, `error` string, and `message` string; 400 responses additionally contain a non-empty `details` array.

**Validates: Requirements 13.1, 13.2, 13.3, 13.4**

---

### Property 16: All responses carry Content-Type: application/json

*For any* API endpoint and any input (valid or invalid), every HTTP response SHALL include the header `Content-Type: application/json`.

**Validates: Requirements 13.5**

---

## Error Handling

### Error taxonomy

| HTTP Status | Schema | Trigger conditions |
|---|---|---|
| 400 | ValidationError | Invalid/missing body fields, non-UUID path params, self-follow, empty PATCH body |
| 401 | UnauthorizedError | Missing, malformed, expired, or invalid-signature JWT |
| 403 | ForbiddenError | Authenticated user attempting to PATCH another user's account |
| 404 | NotFoundError | User or message not found; follower relationship not found |
| 409 | ConflictError | Duplicate email on create or update |

### Error response construction

All error responses must include:
- `status`: the integer HTTP status code
- `error`: a short label (e.g. `"Bad Request"`, `"Unauthorized"`)
- `message`: a human-readable description

`ValidationError` additionally includes:
- `details`: array of `{ field, issue }` objects, one per failing constraint; must never be empty

The global error handler intercepts domain exceptions and maps them to these shapes before serialisation. All responses set `Content-Type: application/json`.

### Cascaded deletion

When a User is deleted:
1. All messages authored by that user (matching `messages.user_id`) are deleted first (or via `ON DELETE CASCADE` FK constraint).
2. All follower relationships referencing that user (either as `user_id` or `follower_id`) are deleted.
3. The user record itself is then deleted.

This ensures referential integrity and no orphaned records.

### Password security

- Passwords are hashed using a strong adaptive algorithm (e.g. bcrypt) with a sufficient work factor before storage.
- The plain-text password is never persisted, logged, or included in any response body.

---

## Testing Strategy

### Approach

The testing strategy combines property-based tests for universal correctness properties with example-based unit/integration tests for specific scenarios and edge cases. The property-based testing library should be chosen to match the implementation language (e.g. [fast-check](https://fast-check.dev/) for TypeScript/JavaScript, [hypothesis](https://hypothesis.readthedocs.io/) for Python, [jqwik](https://jqwik.net/) for Java).

### Unit tests (service layer)

Unit tests target the Service layer in isolation, using mocked repositories:

- `UserService.createUser`: valid inputs create correctly shaped domain object (no password); duplicate email throws ConflictException; invalid inputs throw ValidationException.
- `UserService.updateUserById`: only supplied fields are updated; ForbiddenException when callerId ≠ userId; empty body throws ValidationException.
- `UserService.deleteUserById`: verifies cascade deletion triggers (messages deleted); not-found throws NotFoundException.
- `UserService.addFollower`: idempotency (adding same follower twice is safe); self-follow throws ValidationException; non-existent users throw NotFoundException.
- `UserService.removeFollower`: non-existent relationship throws NotFoundException.
- `MessageService.createMessage`: valid input produces correct Message with server-assigned UUID and sentAt; missing/empty fields throw ValidationException.
- Validation utilities: UUID format, email format, string length constraints — each exercised with boundary and invalid inputs.

### Property-based tests

Each property (1–16 above) is implemented as a single property-based test running a minimum of **100 iterations**. Each test is tagged in source:

```
// Feature: simple-messenger-backend, Property <N>: <property_text>
```

Key generators needed:
- `validCreateUserRequest`: random firstName (1–100), lastName (1–100), valid email, password (8–24)
- `invalidEmail`: strings that are not valid email addresses
- `outOfRangePassword`: length < 8 or > 24
- `invalidName`: empty string or string > 100 chars
- `validUUID`: RFC 4122 UUID v4
- `nonUUID`: arbitrary string that is not a valid UUID
- `validMessageText`: non-empty string
- `pageAndPageSize`: page ≥ 0, pageSize in [1, 100]
- `malformedAuthHeader`: various invalid Authorization header values

Property tests that touch HTTP endpoints are run as integration tests against a running test instance of the service with an isolated in-memory or test database.

### Integration tests

Integration tests exercise the full HTTP stack (router → service → repository → DB) with a real test database:

- `POST /users` — 201 with Location; 400 for each invalid field; 409 for duplicate email
- `GET /users/{userId}` — 200; 404 for unknown id; 401 without token
- `PATCH /users/{userId}` — 200 partial update; 403 wrong user; 409 email conflict
- `DELETE /users/{userId}` — 204; messages cascade deleted; 404 for unknown
- `POST /users/{userId}/followers` — 200; idempotent; 400 self-follow; 404 unknown user
- `DELETE /users/{userId}/followers/{followerId}` — 204; 404 relationship absent
- `POST /messages` — 201 with Location; 400 invalid fields; 404 unknown user
- `GET /messages` — 200 paginated; default page/pageSize; correct slices
- `GET /messages/{messageId}` — 200; 404 unknown
- `DELETE /messages/{messageId}` — 204; subsequent GET 404
- `GET /users/{userId}/messages` — 200 only that user's messages; 404 unknown user
- Auth: every protected endpoint returns 401 without token, 401 with expired token

### Smoke tests

- `POST /users` is callable without an `Authorization` header (returns 201 or 400, not 401)
- Each protected endpoint returns 401 when called without a token (verifies auth middleware wiring)

### Coverage targets

- All 13 requirements covered by at least one test
- All 16 correctness properties covered by a property-based test
- Every distinct HTTP status code path (201, 200, 204, 400, 401, 403, 404, 409) exercised by at least one integration test per endpoint that can return it
