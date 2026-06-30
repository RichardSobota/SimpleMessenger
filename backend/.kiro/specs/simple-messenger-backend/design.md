# Design Document — Simple Messenger Backend

## Overview

The Simple Messenger Backend is a RESTful API service built around the OpenAPI 3.0.3 contract located at `openapi.yaml`. It exposes two primary resource domains — **Users** and **Messages** — under the versioned base path `/v1`. The service handles:

- User registration (public), profile retrieval, partial update, and deletion
- Directed follower relationships stored on the followed user's record
- Text message creation and retrieval, with paginated listings both globally and per user
- JWT-based bearer authentication enforced on every endpoint except `POST /users`
- Uniform, structured error responses for all 4xx conditions

The implementation uses **Java 21** with **Spring Boot 3**, **Spring Data JPA** (Hibernate), **PostgreSQL**, and **jqwik** for property-based testing.

---

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build tool | Maven (pom.xml) |
| ORM / persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15+ |
| Schema migrations | Flyway |
| Authentication | Spring Security + JJWT (io.jsonwebtoken) |
| Password hashing | BCrypt (Spring Security) |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Property-based tests | jqwik |
| Integration tests | JUnit 5 + Spring Boot Test + MockMvc / Testcontainers (PostgreSQL) |
| JSON | Jackson (bundled with Spring Boot) |

---

## Architecture

The service follows a classic layered architecture:

```
┌──────────────────────────────────────────────┐
│              HTTP / Controller Layer         │
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
│   (Spring Data JPA repositories + queries)   │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│                  Database                    │
│              PostgreSQL (via JDBC)           │
└──────────────────────────────────────────────┘
```

### Key architectural decisions

| Decision | Rationale |
|---|---|
| Spring Boot 3 / Java 21 | Mature, production-ready stack; excellent ecosystem for REST APIs |
| Spring Data JPA | Reduces boilerplate for CRUD; native query support for complex joins |
| Flyway migrations | Version-controlled schema changes; easy test-database reset |
| JWT in a Spring Security filter | Auth is orthogonal to business logic; one filter covers all protected routes |
| BCrypt at service layer | Passwords never persisted or returned in plain text |
| Server-assigned UUIDs and timestamps | `id`, `createdAt`, and `sentAt` are always set server-side |

### Request lifecycle

```
Client Request
    │
    ▼
JwtAuthFilter (verify JWT — skip for POST /v1/users)
    │  └─ 401 UnauthorizedError if invalid/missing
    ▼
@RestController (parse path params, query params, body; @Valid)
    │  └─ 400 ValidationError if UUID format invalid or body fails @Valid
    ▼
Service Layer (domain validation, business rules)
    │  └─ 400/403/404/409 domain exceptions as appropriate
    ▼
Repository Layer (JPA read/write)
    ▼
Response Serialisation (Jackson → shape per OpenAPI schema)
    ▼
GlobalExceptionHandler (@ControllerAdvice maps exceptions → error bodies)
    ▼
Client Response
```

---

## Project Structure

```
src/
├── main/
│   ├── java/com/simplemessenger/
│   │   ├── SimplemessengerApplication.java
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── UserController.java
│   │   │   └── MessageController.java
│   │   ├── dto/
│   │   │   ├── CreateUserRequest.java
│   │   │   ├── UpdateUserRequest.java
│   │   │   ├── UserResponse.java
│   │   │   ├── UserSummaryResponse.java
│   │   │   ├── CreateMessageRequest.java
│   │   │   ├── MessageResponse.java
│   │   │   └── PagedMessagesResponse.java
│   │   ├── entity/
│   │   │   ├── UserEntity.java
│   │   │   └── MessageEntity.java
│   │   ├── exception/
│   │   │   ├── ValidationException.java
│   │   │   ├── UnauthorizedException.java
│   │   │   ├── ForbiddenException.java
│   │   │   ├── NotFoundException.java
│   │   │   └── ConflictException.java
│   │   ├── handler/
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   └── MessageRepository.java
│   │   ├── security/
│   │   │   ├── JwtAuthFilter.java
│   │   │   └── JwtUtil.java
│   │   └── service/
│   │       ├── UserService.java
│   │       └── MessageService.java
│   └── resources/
│       ├── application.properties
│       ├── application-test.properties
│       └── db/migration/
│           ├── V1__create_users_table.sql
│           ├── V2__create_followers_table.sql
│           └── V3__create_messages_table.sql
└── test/
    └── java/com/simplemessenger/
        ├── unit/
        │   ├── ValidationUtilsTest.java
        │   ├── UserServiceTest.java
        │   └── MessageServiceTest.java
        ├── integration/
        │   ├── UserControllerIntegrationTest.java
        │   └── MessageControllerIntegrationTest.java
        └── pbt/
            ├── UserCreationPropertyTest.java
            ├── UserRetrievalUpdatePropertyTest.java
            ├── FollowerManagementPropertyTest.java
            ├── MessageCreationPropertyTest.java
            ├── MessageRetrievalDeletionPropertyTest.java
            ├── PaginationPropertyTest.java
            └── AuthErrorShapePropertyTest.java
```

---

## Components and Interfaces

### 1. JwtAuthFilter

`src/main/java/com/simplemessenger/security/JwtAuthFilter.java`

Extends `OncePerRequestFilter`. Skips `POST /v1/users`. For all other requests:
- Extracts `Authorization: Bearer <token>` header.
- Verifies signature and expiry via `JwtUtil`.
- On failure → sets response to 401 `UnauthorizedError` JSON and halts the filter chain.
- On success → sets `SecurityContextHolder` with the authenticated principal.

### 2. UserController

`src/main/java/com/simplemessenger/controller/UserController.java`

| Operation | HTTP | Path | Auth |
|---|---|---|---|
| createUser | POST | /v1/users | Public |
| getUserById | GET | /v1/users/{userId} | Required |
| updateUserById | PATCH | /v1/users/{userId} | Required |
| deleteUserById | DELETE | /v1/users/{userId} | Required |
| addFollower | POST | /v1/users/{userId}/followers | Required |
| removeFollower | DELETE | /v1/users/{userId}/followers/{followerId} | Required |
| getMessagesForUser | GET | /v1/users/{userId}/messages | Required |

Each handler:
1. Validates UUID path parameters (custom validator or manual check) → 400 if invalid.
2. Uses `@Valid` on request bodies for field-level constraints.
3. Delegates to `UserService` or `MessageService`.
4. Returns the appropriate HTTP status and response DTO.

### 3. MessageController

`src/main/java/com/simplemessenger/controller/MessageController.java`

| Operation | HTTP | Path | Auth |
|---|---|---|---|
| createMessage | POST | /v1/messages | Required |
| getMessages | GET | /v1/messages | Required |
| getMessageById | GET | /v1/messages/{messageId} | Required |
| deleteMessageById | DELETE | /v1/messages/{messageId} | Required |

### 4. UserService

`src/main/java/com/simplemessenger/service/UserService.java`

```
createUser(CreateUserRequest) → UserResponse
  - validate fields (Jakarta Bean Validation + manual email/password check)
  - check email uniqueness → ConflictException (409) if taken
  - hash password with BCrypt
  - persist; return UserResponse (no password)

getUserById(UUID userId) → UserResponse
  - fetch; throw NotFoundException (404) if absent

updateUserById(UUID callerId, UUID userId, UpdateUserRequest) → UserResponse
  - throw ForbiddenException (403) if callerId ≠ userId
  - validate patch fields; throw ValidationException (400) if empty or invalid
  - check email uniqueness if email supplied → ConflictException (409)
  - apply only supplied fields; return updated UserResponse

deleteUserById(UUID userId) → void
  - verify user exists → NotFoundException (404)
  - delete (cascade via FK constraints removes messages and follower rows)

addFollower(UUID userId, UUID followerId) → UserResponse
  - verify both exist → NotFoundException (404)
  - prevent self-follow (userId == followerId) → ValidationException (400)
  - idempotent: if already a follower, return current user unchanged
  - add follower; return updated UserResponse

removeFollower(UUID userId, UUID followerId) → void
  - verify both users exist → NotFoundException (404)
  - verify follower relationship exists → NotFoundException (404)
  - remove follower
```

### 5. MessageService

`src/main/java/com/simplemessenger/service/MessageService.java`

```
createMessage(CreateMessageRequest) → MessageResponse
  - validate fields non-empty → ValidationException (400)
  - verify userId exists → NotFoundException (404)
  - assign UUID id, server-generated sentAt (UTC, OffsetDateTime)
  - persist; return MessageResponse

getMessageById(UUID messageId) → MessageResponse
  - fetch; throw NotFoundException (404) if absent

deleteMessageById(UUID messageId) → void
  - verify exists → NotFoundException (404)
  - delete

getMessages(int page, int pageSize) → PagedMessagesResponse
  - default page=0, pageSize=20; clamp pageSize to max 100
  - return PagedMessagesResponse with pagination metadata

getMessagesForUser(UUID userId, int page, int pageSize) → PagedMessagesResponse
  - verify user exists → NotFoundException (404)
  - same pagination logic; filter to that user's messages
```

### 6. GlobalExceptionHandler

`src/main/java/com/simplemessenger/handler/GlobalExceptionHandler.java`

Annotated with `@ControllerAdvice`. Maps domain exceptions to HTTP responses:

| Exception | HTTP Status | Body schema |
|---|---|---|
| ValidationException | 400 | ValidationError |
| UnauthorizedException | 401 | UnauthorizedError |
| ForbiddenException | 403 | ForbiddenError |
| NotFoundException | 404 | NotFoundError |
| ConflictException | 409 | ConflictError |
| MethodArgumentNotValidException | 400 | ValidationError (from @Valid) |

All responses include `Content-Type: application/json`.

---

## Data Models

### JPA Entities

#### `UserEntity`

```java
@Entity @Table(name = "users")
public class UserEntity {
    @Id UUID id;
    @Column(name = "first_name", nullable = false, length = 100) String firstName;
    @Column(name = "last_name",  nullable = false, length = 100) String lastName;
    @Column(unique = true, nullable = false) String email;
    @Column(name = "password_hash", nullable = false) String passwordHash;
    @Column(name = "created_at", nullable = false) OffsetDateTime createdAt;

    @ManyToMany(fetch = LAZY)
    @JoinTable(
        name = "followers",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "follower_id")
    )
    List<UserEntity> followers;
}
```

#### `MessageEntity`

```java
@Entity @Table(name = "messages")
public class MessageEntity {
    @Id UUID id;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "user_id", nullable = false)
    UserEntity user;
    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    String messageText;
    @Column(name = "sent_at", nullable = false) OffsetDateTime sentAt;
}
```

### Flyway Migrations

**V1__create_users_table.sql**
```sql
CREATE TABLE users (
  id           UUID         PRIMARY KEY,
  first_name   VARCHAR(100) NOT NULL,
  last_name    VARCHAR(100) NOT NULL,
  email        VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR      NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

**V2__create_followers_table.sql**
```sql
CREATE TABLE followers (
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, follower_id),
  CHECK (user_id <> follower_id)
);
```

**V3__create_messages_table.sql**
```sql
CREATE TABLE messages (
  id           UUID        PRIMARY KEY,
  user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message_text TEXT        NOT NULL,
  sent_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_messages_user_id ON messages(user_id);
```

### Domain model → API schema mapping

#### UserResponse (API response)

```
UserEntity + followers list
  → UserResponse {
      UUID id, String firstName, String lastName, String email,
      OffsetDateTime createdAt,
      List<UserSummaryResponse> followers   // no nested followers
    }
```

Password is never included in any outbound response.

#### MessageResponse (API response)

```
MessageEntity → MessageResponse {
  UUID id, UUID user, String messageText, OffsetDateTime sentAt
}
```

#### PagedMessagesResponse

```
{
  List<MessageResponse> data,
  int page,           // zero-based, as requested
  int pageSize,
  long totalElements,
  int totalPages      // ceil(totalElements / pageSize), 0 when totalElements=0
}
```

### Pagination logic

```
Pageable pageable = PageRequest.of(page, pageSize);
Page<MessageEntity> result = messageRepository.findAll(pageable);

totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / pageSize);
```

Query parameter defaults and ranges:
- `page`: default 0, minimum 0
- `pageSize`: default 20, minimum 1, maximum 100

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system.*

### Property 1: User creation response completeness

*For any* valid `CreateUserRequest` (firstName 1–100 chars, lastName 1–100 chars, valid email, password 8–24 chars), the API SHALL return HTTP 201 with a User body that: contains a well-formed UUID `id`, a valid ISO 8601 `createdAt` timestamp, an empty `followers` array, and does NOT include a `password` field.

**Validates: Requirements 1.1, 1.6, 1.7, 1.9**

---

### Property 2: User creation input validation rejects invalid fields

*For any* `CreateUserRequest` where at least one field violates its constraint (email format invalid, password length < 8 or > 24, firstName or lastName empty or > 100 chars, any required field missing), the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies every offending field.

**Validates: Requirements 1.2, 1.3, 1.4, 1.10**

---

### Property 3: User retrieval returns correct shape with UserSummary followers

*For any* existing User, an authenticated GET to `/v1/users/{userId}` SHALL return HTTP 200 with a User object containing `id`, `firstName`, `lastName`, `email`, `createdAt`, and a `followers` array where each entry is a `UserSummary` — containing only `id`, `firstName`, `lastName`, `email`, `createdAt` — with no nested `followers` field.

**Validates: Requirements 2.1, 2.4**

---

### Property 4: PATCH applies only supplied fields and leaves others unchanged

*For any* existing User and any non-empty subset of valid PATCH fields (`firstName`, `lastName`, `email`), an authenticated PATCH to `/v1/users/{userId}` SHALL update only the fields present in the request body and return HTTP 200; all fields absent from the request body SHALL retain their pre-patch values.

**Validates: Requirements 3.1, 3.6**

---

### Property 5: PATCH input validation rejects invalid field values

*For any* `UpdateUserRequest` where a supplied field violates its constraint (e.g. `firstName` is empty, `email` is not a valid email format), or where the body is entirely empty, the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies the offending field(s).

**Validates: Requirements 3.2, 3.8**

---

### Property 6: Add-follower idempotency

*For any* valid pair of distinct users A and B, calling `POST /v1/users/{A}/followers` with `followerId=B` multiple times SHALL be idempotent: every call returns HTTP 200 with a User body, and after the second (or subsequent) call the `followers` list is identical in content to what it was after the first successful call.

**Validates: Requirements 5.1, 5.5**

---

### Property 7: Remove-follower result and status

*For any* existing follower relationship between users A and B, an authenticated DELETE to `/v1/users/{A}/followers/{B}` SHALL return HTTP 204 with no response body, and a subsequent GET to `/v1/users/{A}` SHALL not include B in the `followers` list.

**Validates: Requirements 6.1, 6.2**

---

### Property 8: Message creation response completeness

*For any* valid `CreateMessageRequest` (non-empty `messageText`, valid UUID `user` referencing an existing User), an authenticated POST to `/v1/messages` SHALL return HTTP 201 with a `Message` body containing a well-formed UUID `id`, the correct `user` UUID, the original `messageText`, and a valid ISO 8601 UTC `sentAt` timestamp, plus a `Location` response header pointing to the new resource.

**Validates: Requirements 7.1, 7.5**

---

### Property 9: Message creation input validation rejects invalid fields

*For any* `CreateMessageRequest` where `messageText` is missing or empty, or `user` is missing, empty, or not a valid UUID format, the API SHALL return HTTP 400 with a `ValidationError` body whose `details` array identifies the offending field.

**Validates: Requirements 7.2, 7.6**

---

### Property 10: Message retrieval returns correct shape

*For any* existing Message, an authenticated GET to `/v1/messages/{messageId}` SHALL return HTTP 200 with a `Message` object containing `id`, `user`, `messageText`, and `sentAt`.

**Validates: Requirements 8.1**

---

### Property 11: Message deletion makes message unretrievable

*For any* existing Message, an authenticated DELETE to `/v1/messages/{messageId}` SHALL return HTTP 204 with no response body, and a subsequent GET to `/v1/messages/{messageId}` SHALL return HTTP 404.

**Validates: Requirements 9.1**

---

### Property 12: Pagination metadata correctness and completeness

*For any* authenticated GET to `/v1/messages` or `/v1/users/{userId}/messages` with any valid `page` (≥ 0) and `pageSize` (1–100), the response SHALL be HTTP 200 with a `PagedMessages` body where: `data` contains at most `pageSize` items; `page` and `pageSize` reflect the requested values; `totalElements` equals the true count; and `totalPages` equals `ceil(totalElements / pageSize)` (or 0 when `totalElements` is 0).

**Validates: Requirements 10.1–10.6, 11.1, 11.3–11.6**

---

### Property 13: Per-user message listing contains only that user's messages

*For any* existing User, an authenticated GET to `/v1/users/{userId}/messages` SHALL return a `PagedMessages` response where every `Message` in `data` has its `user` field equal to `userId`.

**Validates: Requirements 11.1**

---

### Property 14: Missing or malformed JWT returns 401 on every protected endpoint

*For any* protected endpoint and any request bearing a missing, malformed, expired, or cryptographically invalid `Authorization` header, the API SHALL return HTTP 401 with an `UnauthorizedError` body containing `status: 401`, `error`, and `message` fields.

**Validates: Requirements 12.1, 12.2, 12.3**

---

### Property 15: Error response bodies always match their declared schema

*For any* API response, if the HTTP status is 400, 401, 403, 404, or 409, the response body SHALL conform to the corresponding error schema; 400 responses additionally contain a non-empty `details` array.

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

All error responses include `status`, `error`, `message`. `ValidationError` additionally includes a non-empty `details` array with `{ field, issue }` entries.

---

## Testing Strategy

### Unit tests (service layer)

Tested with JUnit 5 + Mockito, mocking repositories:
- `UserService` — all branches of createUser, updateUserById, deleteUserById, addFollower, removeFollower
- `MessageService` — all branches of createMessage, getMessages, getMessagesForUser, getMessageById, deleteMessageById

### Property-based tests (jqwik)

Each of the 16 properties above is implemented as a jqwik `@Property` test running ≥ 100 tries, tagged:
```java
// Feature: simple-messenger-backend, Property <N>: <property_text>
```

Key jqwik `@Provide` arbitraries:
- `validCreateUserRequest` — firstName (1–100), lastName (1–100), valid email, password (8–24)
- `invalidCreateUserRequest` — at least one field violates its constraint
- `validUUID` — arbitrary UUID string
- `nonUUID` — arbitrary string that is not a UUID
- `validMessageText` — non-empty string
- `pageAndPageSize` — page ≥ 0, pageSize in [1, 100]
- `malformedAuthHeader` — various invalid Authorization header values

PBT tests run as Spring Boot integration tests against a Testcontainers PostgreSQL instance.

### Integration tests

Full HTTP stack exercised via MockMvc or `TestRestTemplate` against a Testcontainers PostgreSQL database. All HTTP status code paths (201, 200, 204, 400, 401, 403, 404, 409) covered per endpoint.

---

## Environment Configuration

`application.properties`:
```
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
app.jwt.secret=${JWT_SECRET}
app.jwt.expiry-ms=${JWT_EXPIRY_MS:3600000}
app.base-url=${BASE_URL:http://localhost:8080/v1}
server.servlet.context-path=/v1
```

`application-test.properties`:
```
spring.datasource.url=jdbc:tc:postgresql:15:///testdb
spring.flyway.enabled=true
app.jwt.secret=test-secret-key-for-tests-only
bcrypt.rounds=4
```
