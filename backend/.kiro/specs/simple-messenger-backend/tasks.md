# Implementation Plan: Simple Messenger Backend

## Overview

This plan implements the Simple Messenger Backend as a Java 21 / Spring Boot 3 REST API service conforming to the `openapi.yaml` contract. It covers 25 tasks across six phases: project scaffolding, shared utilities and error handling, repository layer, service layer, HTTP/controller layer, and a comprehensive test suite (unit, integration, and property-based tests for all 16 correctness properties).

## Tasks

Tasks are ordered from foundation upward: project scaffolding → data layer → service layer → HTTP layer → testing. Each task is self-contained and verifiable.

### Phase 1: Project Scaffolding & Infrastructure

- [ ] 1. Initialise the Java/Spring Boot project
  - Create a Maven project with `pom.xml` declaring Java 21 source/target compatibility
  - Add Spring Boot parent (`spring-boot-starter-parent` 3.x)
  - Add dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `postgresql`, `flyway-core`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson`, `spring-boot-starter-test`, `jqwik`, `testcontainers` (postgresql module)
  - Create `src/main/java/com/simplemessenger/SimplemessengerApplication.java` with `@SpringBootApplication` and `main`
  - Create `src/main/resources/application.properties` documenting required env vars: `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRY_MS`, `BASE_URL`
  - Create `src/main/resources/application-test.properties` using Testcontainers JDBC URL (`jdbc:tc:postgresql:15:///testdb`) and a hardcoded test JWT secret
  - Add `package.json` scripts equivalent: Maven `verify` goal runs all tests
  - Verification: `mvn compile` succeeds with an empty application class

- [ ] 2. Configure Flyway migrations and database schema
  - Create Flyway migration files under `src/main/resources/db/migration/`:
    - `V1__create_users_table.sql` — `users` table (id UUID PK, first_name, last_name, email UNIQUE NOT NULL, password_hash, created_at TIMESTAMPTZ DEFAULT NOW())
    - `V2__create_followers_table.sql` — `followers` join table (user_id FK CASCADE, follower_id FK CASCADE, composite PK, CHECK user_id <> follower_id)
    - `V3__create_messages_table.sql` — `messages` table (id UUID PK, user_id FK CASCADE, message_text TEXT NOT NULL, sent_at TIMESTAMPTZ DEFAULT NOW()) + index on `user_id`
  - Set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate validates against Flyway-managed schema
  - Verification: `mvn test -Dtest=FlywayMigrationTest` (or application startup with Testcontainers) applies migrations cleanly; all three tables and constraints are present

- [ ] 3. Implement JPA entities and repositories
  - Create `UserEntity` (`@Entity`, `@Table(name="users")`) with fields: `UUID id`, `String firstName`, `String lastName`, `String email`, `String passwordHash`, `OffsetDateTime createdAt`, `List<UserEntity> followers` (ManyToMany via `followers` join table)
  - Create `MessageEntity` (`@Entity`, `@Table(name="messages")`) with fields: `UUID id`, `UserEntity user` (ManyToOne), `String messageText`, `OffsetDateTime sentAt`
  - Create `UserRepository` extending `JpaRepository<UserEntity, UUID>` with methods: `findByEmail(String email): Optional<UserEntity>`
  - Create `MessageRepository` extending `JpaRepository<MessageEntity, UUID>` with methods: `findByUserId(UUID userId, Pageable pageable): Page<MessageEntity>`, `countByUserId(UUID userId): long`
  - Verification: Application context loads without errors; Hibernate validates schema

### Phase 2: Shared Utilities & Error Handling

- [ ] 4. Implement domain exception classes
  - Create `src/main/java/com/simplemessenger/exception/` with:
    - `ValidationException(List<FieldError> details)` — HTTP 400
    - `UnauthorizedException(String message)` — HTTP 401
    - `ForbiddenException(String message)` — HTTP 403
    - `NotFoundException(String message)` — HTTP 404
    - `ConflictException(String message)` — HTTP 409
  - Each exception extends `RuntimeException` and carries its HTTP status code and a `FieldError` record `{ String field, String issue }`
  - Verification: Each class instantiates correctly and exposes the expected fields

- [ ] 5. Implement the GlobalExceptionHandler
  - Create `src/main/java/com/simplemessenger/handler/GlobalExceptionHandler.java` annotated `@ControllerAdvice`
  - Handle `ValidationException` → 400 `ValidationError` body with non-empty `details` array
  - Handle `UnauthorizedException` → 401 `UnauthorizedError` body
  - Handle `ForbiddenException` → 403 `ForbiddenError` body
  - Handle `NotFoundException` → 404 `NotFoundError` body
  - Handle `ConflictException` → 409 `ConflictError` body
  - Handle `MethodArgumentNotValidException` (from `@Valid`) → 400 `ValidationError` body mapping constraint violations to `{ field, issue }` entries
  - Handle uncaught `Exception` → 500 with generic message (no stack trace in response)
  - All responses produced with `Content-Type: application/json`
  - Verification: Unit tests assert each exception type produces the correct status and body shape

- [ ] 6. Implement DTOs and validation annotations
  - Create request/response DTOs under `src/main/java/com/simplemessenger/dto/`:
    - `CreateUserRequest` with `@NotBlank @Size(min=1,max=100) firstName/lastName`, `@NotBlank @Email email`, `@NotBlank @Size(min=8,max=24) password`
    - `UpdateUserRequest` with optional `@Size(min=1,max=100) firstName/lastName`, optional `@Email email`
    - `UserResponse` (id, firstName, lastName, email, createdAt, followers: List<UserSummaryResponse>)
    - `UserSummaryResponse` (id, firstName, lastName, email, createdAt — no nested followers)
    - `CreateMessageRequest` with `@NotBlank messageText`, `@NotNull user` (UUID)
    - `MessageResponse` (id, user UUID, messageText, sentAt)
    - `PagedMessagesResponse` (data, page, pageSize, totalElements, totalPages)
  - Implement `ValidationUtils.java` with: `isValidUUID(String): boolean`, `isValidEmail(String): boolean`, helper to build `ValidationException` from a list of `{field, issue}` pairs
  - Verification: Unit tests cover boundary values for each constraint (e.g. password 7 chars fails, 8 chars passes, 24 passes, 25 fails)

- [ ] 7. Implement JWT security configuration
  - Create `JwtUtil.java` with: `generateToken(String userId): String`, `validateToken(String token): String` (returns userId or throws `UnauthorizedException`), `isExpired(String token): boolean`
  - Create `JwtAuthFilter.java` extending `OncePerRequestFilter`:
    - Skip `POST /v1/users` (public endpoint)
    - Extract `Authorization: Bearer <token>` header; reject missing or malformed headers with 401
    - Verify token via `JwtUtil`; on failure write `UnauthorizedError` JSON to response and halt chain
    - On success set `SecurityContextHolder` with authenticated principal
  - Create `SecurityConfig.java` annotated `@Configuration @EnableWebSecurity`:
    - Permit `POST /v1/users` without authentication; require auth for all other `/v1/**` routes
    - Register `JwtAuthFilter` in the filter chain
    - Disable CSRF (stateless API)
  - Verification: Unit tests: valid token passes; missing header → 401; expired token → 401; tampered token → 401

### Phase 3: Service Layer

- [ ] 8. Implement UserService — create, read, delete
  - Create `src/main/java/com/simplemessenger/service/UserService.java`
  - Implement `createUser(CreateUserRequest req) → UserResponse`:
    - Validate via `@Valid` (already enforced by controller); additional manual email-uniqueness check
    - Check email uniqueness via `userRepository.findByEmail`; throw `ConflictException` (409) if taken
    - Hash password with `BCryptPasswordEncoder`; assign `UUID.randomUUID()` id and `OffsetDateTime.now(UTC)` createdAt
    - Persist and return `UserResponse` (no password)
  - Implement `getUserById(UUID userId) → UserResponse`:
    - `userRepository.findById(userId).orElseThrow(NotFoundException::new)`
    - Map entity to `UserResponse` including `followers` mapped to `UserSummaryResponse`
  - Implement `deleteUserById(UUID userId) → void`:
    - Verify exists; delete (cascade removes messages and follower rows via FK)
  - Verification: Unit tests with Mockito-mocked repository for all branches

- [ ] 9. Implement UserService — update and follower management
  - Implement `updateUserById(UUID callerId, UUID userId, UpdateUserRequest patch) → UserResponse`:
    - Throw `ForbiddenException` if `callerId != userId`
    - If all patch fields null/blank, throw `ValidationException` (empty body)
    - Validate supplied fields; throw `ValidationException` with details on failure
    - If email supplied, check uniqueness against other users; throw `ConflictException` if taken
    - Apply only non-null supplied fields; save and return updated `UserResponse`
  - Implement `addFollower(UUID userId, UUID followerId) → UserResponse`:
    - Verify both users exist; throw `NotFoundException` for either missing
    - If `userId.equals(followerId)` throw `ValidationException` (self-follow)
    - If relationship already exists, return current user unchanged (idempotent)
    - Otherwise add to followers list, save, return updated `UserResponse`
  - Implement `removeFollower(UUID userId, UUID followerId) → void`:
    - Verify both users exist → `NotFoundException`
    - Verify relationship exists → `NotFoundException`
    - Remove follower and save
  - Verification: Unit tests with mocked repository for all branches including idempotency

- [ ] 10. Implement MessageService
  - Create `src/main/java/com/simplemessenger/service/MessageService.java`
  - Implement `createMessage(CreateMessageRequest req) → MessageResponse`:
    - Validate non-empty messageText and valid UUID user; throw `ValidationException` (400) on failure
    - Verify user exists → `NotFoundException` (404)
    - Assign `UUID.randomUUID()` id and `OffsetDateTime.now(UTC)` sentAt
    - Persist and return `MessageResponse`
  - Implement `getMessageById(UUID messageId) → MessageResponse`: fetch or throw `NotFoundException`
  - Implement `deleteMessageById(UUID messageId) → void`: verify exists; delete
  - Implement `getMessages(int page, int pageSize) → PagedMessagesResponse`:
    - Clamp defaults: page=0, pageSize=20 (max 100)
    - Use `PageRequest.of(page, pageSize)` and `messageRepository.findAll(pageable)`
    - Compute `totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / pageSize)`
    - Return `PagedMessagesResponse`
  - Implement `getMessagesForUser(UUID userId, int page, int pageSize) → PagedMessagesResponse`:
    - Verify user exists → `NotFoundException`; same pagination logic filtered by userId
  - Verification: Unit tests with mocked repository for all branches

### Phase 4: HTTP / Controller Layer

- [ ] 11. Implement UserController
  - Create `src/main/java/com/simplemessenger/controller/UserController.java` annotated `@RestController @RequestMapping("/users")`
  - Implement all user routes; apply `@Valid` to request bodies
  - Validate UUID path params; throw `ValidationException` (400) if format invalid
  - `POST /users` → delegate to `userService.createUser`; respond 201 with `Location: {BASE_URL}/users/{id}` header
  - `GET /users/{userId}` → respond 200 with `UserResponse`
  - `PATCH /users/{userId}` → extract authenticated userId from `SecurityContextHolder`; delegate to `userService.updateUserById(callerId, userId, patch)`; respond 200
  - `DELETE /users/{userId}` → respond 204 (no body)
  - `POST /users/{userId}/followers` → respond 200 with updated `UserResponse`
  - `DELETE /users/{userId}/followers/{followerId}` → respond 204
  - `GET /users/{userId}/messages` → parse pagination params (default page=0, pageSize=20); respond 200 with `PagedMessagesResponse`
  - Pass exceptions to `GlobalExceptionHandler` via `@ControllerAdvice`
  - Verification: Integration tests (MockMvc + Testcontainers) covering 201/200/204/400/401/403/404/409 for each route

- [ ] 12. Implement MessageController
  - Create `src/main/java/com/simplemessenger/controller/MessageController.java` annotated `@RestController @RequestMapping("/messages")`
  - Implement all message routes; apply `@Valid` to request bodies
  - Validate UUID path params; throw `ValidationException` (400) if format invalid
  - `POST /messages` → respond 201 with `Location: {BASE_URL}/messages/{id}` header
  - `GET /messages` → parse pagination params; respond 200 with `PagedMessagesResponse`
  - `GET /messages/{messageId}` → respond 200 with `MessageResponse`
  - `DELETE /messages/{messageId}` → respond 204 (no body)
  - Verification: Integration tests covering 201/200/204/400/401/404 for each route

### Phase 5: Integration & Bootstrap

- [ ] 13. Wire application bootstrap and smoke-test server startup
  - Ensure `SimplemessengerApplication.java` boots, Flyway runs, and all beans resolve
  - Confirm `server.servlet.context-path=/v1` is set so all routes are under `/v1`
  - Smoke test: `POST /v1/users` returns 201 or 400 (not 401); every protected endpoint returns 401 without a token
  - Verification: `mvn spring-boot:run` starts without errors; manual curl smoke tests pass

### Phase 6: Testing

- [ ] 14. Write unit tests for validation utilities and exception classes
  - Test `isValidUUID`: valid v4 UUID passes; malformed strings, empty string, nil UUID fail
  - Test `isValidEmail`: valid addresses pass; missing `@`, missing domain, empty string fail
  - Test name/password/messageText validators at boundary values
  - Test each exception class: correct HTTP status, `error`, `message`; `ValidationException` includes non-empty `details`
  - Test `GlobalExceptionHandler`: each exception type → correct HTTP status and body
  - Validates: Requirements 1.2, 1.3, 1.4, 7.2

- [ ] 15. Write unit tests for UserService
  - `createUser` — valid inputs → correct domain object (no password, followers=[], UUID id, ISO 8601 createdAt); duplicate email → `ConflictException`; each invalid field → `ValidationException`
  - `updateUserById` — callerId ≠ userId → `ForbiddenException`; empty body → `ValidationException`; invalid field → `ValidationException`; duplicate email → `ConflictException`; partial patch → only supplied fields change
  - `deleteUserById` — non-existent user → `NotFoundException`
  - `addFollower` — both exist → follower added; self-follow → `ValidationException`; either user missing → `NotFoundException`; duplicate add → idempotent
  - `removeFollower` — relationship exists → removed; either user missing → `NotFoundException`; relationship absent → `NotFoundException`
  - All tests use Mockito-mocked repositories
  - Validates: Requirements 1.1–1.5, 1.9, 3.1, 3.7, 5.1, 5.5, 5.6, 6.1, 6.5

- [ ] 16. Write unit tests for MessageService
  - `createMessage` — valid inputs → correct `MessageResponse` (UUID id, correct user, messageText, ISO 8601 sentAt); missing/empty messageText → `ValidationException`; non-existent user UUID → `NotFoundException`
  - `getMessageById` — non-existent id → `NotFoundException`
  - `deleteMessageById` — non-existent id → `NotFoundException`
  - `getMessages` — defaults applied; correct `PagedMessagesResponse` shape; `totalPages=0` when `totalElements=0`
  - `getMessagesForUser` — non-existent userId → `NotFoundException`; returns only that user's messages; correct pagination metadata
  - All tests use Mockito-mocked repositories
  - Validates: Requirements 7.1, 7.2, 7.5, 8.1, 9.1, 10.1–10.6, 11.1–11.6

- [ ] 17. Write integration tests for User endpoints
  - Use `@SpringBootTest` + `MockMvc` (or `TestRestTemplate`) + Testcontainers PostgreSQL
  - `POST /v1/users` — 201 with `Location` header; 400 per invalid field; 409 for duplicate email; no 401 (public endpoint)
  - `GET /v1/users/{userId}` — 200 with correct User shape; 401 without token; 404 for unknown UUID; 400 for non-UUID param
  - `PATCH /v1/users/{userId}` — 200 partial update; 400 empty body and invalid fields; 401 no token; 403 mismatched caller; 404 unknown user; 409 duplicate email
  - `DELETE /v1/users/{userId}` — 204; subsequent GET returns 404; 401 no token; 404 unknown user
  - `POST /v1/users/{userId}/followers` — 200; idempotent second call 200; 400 self-follow; 401 no token; 404 unknown userId or followerId
  - `DELETE /v1/users/{userId}/followers/{followerId}` — 204; 401 no token; 404 unknown user or absent relationship
  - `GET /v1/users/{userId}/messages` — 200 only that user's messages; 401 no token; 404 unknown userId
  - Validates: Requirements 1–6, 11, 12, 13

- [ ] 18. Write integration tests for Message endpoints
  - `POST /v1/messages` — 201 with `Location` header; 400 missing/empty messageText; 400 invalid UUID user; 401 no token; 404 unknown user UUID
  - `GET /v1/messages` — 200 with correct PagedMessages shape; defaults applied; explicit page/pageSize respected; correct totalElements/totalPages; 401 no token
  - `GET /v1/messages/{messageId}` — 200; 401 no token; 404 unknown messageId; 400 non-UUID param
  - `DELETE /v1/messages/{messageId}` — 204; subsequent GET 404; 401 no token; 404 unknown messageId
  - Validates: Requirements 7–10, 12, 13

- [ ] 19. Write property-based tests — Properties 1 & 2 (User creation)
  - Use jqwik `@Property` with ≥ 100 tries
  - Property 1 — `@Provide validCreateUserRequest` arbitrary (firstName 1–100, lastName 1–100, valid email, password 8–24): `POST /v1/users` returns 201 with UUID `id`, ISO 8601 `createdAt`, empty `followers`, no `password` field
  - Property 2 — `@Provide invalidCreateUserRequest` arbitrary (at least one field violating its constraint): `POST /v1/users` returns 400 with `ValidationError` body whose `details` array is non-empty and identifies every offending field
  - Tag: `// Feature: simple-messenger-backend, Property 1` / `Property 2`
  - Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.9, 1.10

- [ ] 20. Write property-based tests — Properties 3, 4 & 5 (User retrieval and update)
  - Property 3 — For any created User, `GET /v1/users/{userId}` returns 200 with correct shape; `followers` entries are `UserSummary` (no nested `followers` field)
  - Property 4 — For any non-empty subset of valid PATCH fields, `PATCH /v1/users/{userId}` returns 200; only supplied fields change; others retain original values
  - Property 5 — For any PATCH body with an invalid field or empty body, returns 400 with `ValidationError` identifying offending fields
  - Run ≥ 100 tries each
  - Validates: Requirements 2.1, 2.4, 3.1, 3.2, 3.6, 3.8

- [ ] 21. Write property-based tests — Properties 6 & 7 (Follower management)
  - Property 6 — For any two distinct users A and B, calling `POST /v1/users/{A}/followers` with `followerId=B` twice: both return 200; `followers` list after second call identical to after first (idempotency)
  - Property 7 — For any existing follower relationship A→B, `DELETE /v1/users/{A}/followers/{B}` returns 204; subsequent `GET /v1/users/{A}` does not include B in `followers`
  - Run ≥ 100 tries each
  - Validates: Requirements 5.1, 5.5, 6.1, 6.2

- [ ] 22. Write property-based tests — Properties 8 & 9 (Message creation)
  - Property 8 — `@Provide validCreateMessageRequest` (non-empty messageText, valid UUID referencing existing user): `POST /v1/messages` returns 201 with UUID `id`, correct `user`, original `messageText`, ISO 8601 UTC `sentAt`, and `Location` header
  - Property 9 — `@Provide invalidCreateMessageRequest` (missing/empty messageText or invalid UUID user): `POST /v1/messages` returns 400 with `ValidationError` identifying offending field(s)
  - Run ≥ 100 tries each
  - Validates: Requirements 7.1, 7.2, 7.5, 7.6

- [ ] 23. Write property-based tests — Properties 10 & 11 (Message retrieval and deletion)
  - Property 10 — For any existing Message, `GET /v1/messages/{messageId}` returns 200 with `id`, `user`, `messageText`, `sentAt`
  - Property 11 — For any existing Message, `DELETE /v1/messages/{messageId}` returns 204; subsequent `GET /v1/messages/{messageId}` returns 404
  - Run ≥ 100 tries each
  - Validates: Requirements 8.1, 9.1

- [ ] 24. Write property-based tests — Properties 12 & 13 (Pagination)
  - Property 12 — `@Provide pageAndPageSize` (page ≥ 0, pageSize 1–100): `GET /v1/messages` and `GET /v1/users/{userId}/messages` return 200 with `PagedMessages` where `data.length ≤ pageSize`, metadata reflects requested values, `totalPages` = ceil(totalElements/pageSize) or 0 when totalElements=0
  - Property 13 — For any existing User, every `Message` in `GET /v1/users/{userId}/messages` has its `user` field equal to `userId`
  - Run ≥ 100 tries each
  - Validates: Requirements 10.1–10.6, 11.1, 11.3–11.6

- [ ] 25. Write property-based tests — Properties 14, 15 & 16 (Auth and error response shape)
  - Property 14 — `@Provide malformedAuthHeader` (missing header, random strings, expired tokens, tampered tokens): every protected endpoint returns 401 with `UnauthorizedError` body (`status: 401`, `error`, `message`)
  - Property 15 — For any request producing a 4xx response (400/401/403/404/409), body conforms to the declared error schema; 400 responses have a non-empty `details` array
  - Property 16 — For any request to any endpoint, every response includes `Content-Type: application/json`
  - Run ≥ 100 tries each
  - Validates: Requirements 12.1, 12.2, 12.3, 13.1–13.5

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1"] },
    { "wave": 2, "tasks": ["2"] },
    { "wave": 3, "tasks": ["3"] },
    { "wave": 4, "tasks": ["4"] },
    { "wave": 5, "tasks": ["5"] },
    { "wave": 6, "tasks": ["6"] },
    { "wave": 7, "tasks": ["7"] },
    { "wave": 8, "tasks": ["8"] },
    { "wave": 9, "tasks": ["9"] },
    { "wave": 10, "tasks": ["10"] },
    { "wave": 11, "tasks": ["11"] },
    { "wave": 12, "tasks": ["12"] },
    { "wave": 13, "tasks": ["13"] },
    { "wave": 14, "tasks": ["14", "15", "16"] },
    { "wave": 15, "tasks": ["17", "18"] },
    { "wave": 16, "tasks": ["19", "20", "21", "22", "23", "24", "25"] }
  ]
}
```

## Notes

- All source code lives under `src/main/java/com/simplemessenger/`; tests under `src/test/java/com/simplemessenger/`
- Use jqwik for all property-based tests (aligns with the Java stack)
- Each property-based test must be tagged: `// Feature: simple-messenger-backend, Property <N>: <property_text>`
- Use Testcontainers (`org.testcontainers:postgresql`) for integration and PBT tests — the container is started once per test class via `@Testcontainers` and `@Container`
- Reset test database state between tests using `@Transactional` (rolled back after each test) or `@Sql` scripts truncating tables
- JWT tokens used in tests are generated using the `JwtUtil` helper with the test secret from `application-test.properties`
- `BASE_URL` env var constructs `Location` headers in 201 responses; default `http://localhost:8080/v1`
- BCrypt work factor should be lowered (rounds=4) in the test profile for speed
- `ON DELETE CASCADE` on `messages.user_id` and `followers.user_id`/`followers.follower_id` handles cascaded deletion at DB level; service layer does not need explicit cascade logic
