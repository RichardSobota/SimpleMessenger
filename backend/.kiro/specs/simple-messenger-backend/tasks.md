# Implementation Plan: Simple Messenger Backend

## Overview

This plan implements the Simple Messenger Backend as a Node.js/TypeScript REST API service conforming to the `openapi.yaml` contract. It covers 25 tasks across six phases: project scaffolding, shared utilities and error handling, repository layer, service layer, HTTP/router layer, and a comprehensive test suite (unit, integration, and property-based tests for all 16 correctness properties).

## Tasks

Tasks are ordered from foundation upward: project scaffolding → data layer → service layer → HTTP layer → testing. Each task is self-contained and verifiable. Complete tasks in order; later tasks depend on earlier ones.

### Phase 1: Project Scaffolding & Infrastructure

- [ ] 1. Initialise the Node.js/TypeScript project
  - Run `npm init -y` and install core runtime dependencies: `express`, `pg` (or `prisma`), `bcrypt`, `jsonwebtoken`, `uuid`
  - Install dev dependencies: `typescript`, `ts-node`, `nodemon`, `@types/*` packages, `jest`, `supertest`, `fast-check`
  - Create `tsconfig.json` with strict mode, `outDir: dist`, `rootDir: src`
  - Add `package.json` scripts: `build`, `start`, `dev`, `test`
  - Create `.env.example` documenting required environment variables: `DATABASE_URL`, `JWT_SECRET`, `JWT_EXPIRY`, `PORT`, `BASE_URL`
  - Verification: `npm run build` succeeds with an empty `src/index.ts`

- [ ] 2. Configure the database and run initial migrations
  - Choose and configure a PostgreSQL client (e.g. `pg` with raw SQL migrations, or Prisma)
  - Write migration files to create the three tables defined in the design:
    - `users` (id UUID PK, first_name, last_name, email UNIQUE, password_hash, created_at)
    - `followers` (user_id FK, follower_id FK, composite PK, check user_id ≠ follower_id, ON DELETE CASCADE)
    - `messages` (id UUID PK, user_id FK ON DELETE CASCADE, message_text, sent_at)
  - Add index on `messages(user_id)`
  - Provide a `scripts/migrate.ts` (or equivalent) that applies migrations
  - Set up a separate test database configuration switchable via `NODE_ENV=test`
  - Verification: Migrations apply cleanly; tables and constraints visible in psql

- [ ] 3. Implement application entry point and server bootstrap
  - Create `src/index.ts` that reads env vars, connects to the DB, registers middleware, and starts the Express server
  - Create `src/app.ts` that constructs and exports the Express app (separated from `listen` for testability)
  - Mount all routes under `/v1`
  - Verification: Server starts and responds to any request

### Phase 2: Shared Utilities & Error Handling

- [ ] 4. Implement domain exception classes
  - Create `src/errors/` with typed exception classes: `ValidationException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException`
  - Each exception carries: HTTP status code, `error` label string, `message` string
  - `ValidationException` additionally carries a `details: { field: string; issue: string }[]` array
  - Verification: Each class instantiates correctly and exposes the expected fields

- [ ] 5. Implement the global error handler middleware
  - Create `src/middleware/errorHandler.ts`
  - Map each domain exception to its correct HTTP status and JSON body shape per the design's error taxonomy table
  - Include a fallback 500 handler for unexpected errors
  - Ensure all responses include `Content-Type: application/json`
  - Verification: Unit test: each exception type produces the correct status and body shape

- [ ] 6. Implement validation utilities
  - Create `src/utils/validation.ts`
  - Implement and export: `isValidUUID(value: string): boolean`, `isValidEmail(value: string): boolean`
  - Implement field length validators: `validateName` (1–100 chars), `validatePassword` (8–24 chars), `validateMessageText` (≥ 1 char)
  - Implement `buildValidationError(details: {field, issue}[]): ValidationException`
  - Verification: Unit tests cover boundary values (e.g. password of 7 chars fails; 8 chars passes; 24 passes; 25 fails)

- [ ] 7. Implement the JWT auth middleware
  - Create `src/middleware/auth.ts`
  - Extract the `Authorization: Bearer <token>` header; reject missing or malformed headers with `UnauthorizedException` (401)
  - Verify the token signature and expiry using `jsonwebtoken` and `process.env.JWT_SECRET`
  - On success, attach the decoded principal (`sub` or `userId`) to `req.user`
  - Export the middleware and a helper to generate tokens (used in tests and login flows)
  - Verification: Unit tests: valid token passes; missing header fails; expired token fails; tampered token fails

### Phase 3: Repository Layer

- [ ] 8. Implement the User Repository
  - Create `src/repositories/userRepository.ts`
  - Implement: `create(data)`, `findById(id)`, `findByEmail(email)`, `update(id, patch)`, `delete(id)`, `addFollower(userId, followerId)`, `removeFollower(userId, followerId)`, `hasFollower(userId, followerId)`
  - All queries use parameterised statements (no string interpolation)
  - `findById` joins the `followers` table and maps the result to the `User` domain object including the `followers: UserSummary[]` array
  - `delete` relies on `ON DELETE CASCADE` constraints to remove associated messages and follower rows
  - Verification: Integration tests against the test database for each method

- [ ] 9. Implement the Message Repository
  - Create `src/repositories/messageRepository.ts`
  - Implement: `create(data)`, `findById(id)`, `delete(id)`, `findAll(page, pageSize)`, `findByUserId(userId, page, pageSize)`
  - Paginated queries return `{ data: Message[], totalElements: number }`
  - All queries use parameterised statements
  - Verification: Integration tests against the test database for each method, including pagination boundary cases

### Phase 4: Service Layer

- [ ] 10. Implement the User Service
  - Create `src/services/userService.ts`
  - Implement `createUser(firstName, lastName, email, password)`:
    - Validate all fields; throw `ValidationException` on failure
    - Check email uniqueness via repository; throw `ConflictException` (409) if taken
    - Hash password with bcrypt before persisting
    - Return `User` (no password field)
  - Implement `getUserById(userId)`: fetch; throw `NotFoundException` if absent
  - Implement `updateUserById(callerId, userId, patch)`:
    - Throw `ForbiddenException` (403) if `callerId ≠ userId`
    - Validate patch fields; throw `ValidationException` (400) if body is empty or any field is invalid
    - Check email uniqueness if email is supplied; throw `ConflictException` (409) if taken by another user
    - Apply only supplied fields; return updated `User`
  - Implement `deleteUserById(userId)`: verify exists → `NotFoundException`; delete (cascade)
  - Implement `addFollower(userId, followerId)`:
    - Verify both users exist → `NotFoundException`
    - Throw `ValidationException` (400) if `userId === followerId` (self-follow)
    - Idempotent: if already a follower, return the unchanged user
    - Return updated `User`
  - Implement `removeFollower(userId, followerId)`:
    - Verify both users exist → `NotFoundException`
    - Verify relationship exists → `NotFoundException`
    - Remove follower
  - Verification: Unit tests with mocked repository for all branches

- [ ] 11. Implement the Message Service
  - Create `src/services/messageService.ts`
  - Implement `createMessage(userId, messageText)`:
    - Validate fields non-empty; throw `ValidationException` (400) on failure
    - Verify `userId` exists → `NotFoundException` (404)
    - Assign UUID `id` and server-generated `sentAt` (UTC ISO 8601)
    - Persist and return `Message`
  - Implement `getMessageById(messageId)`: fetch; throw `NotFoundException` if absent
  - Implement `deleteMessageById(messageId)`: verify exists → `NotFoundException`; delete
  - Implement `getMessages(page, pageSize)`:
    - Default page=0, pageSize=20; clamp pageSize to max 100
    - Return `PagedMessages` with metadata: `page`, `pageSize`, `totalElements`, `totalPages` (0 when totalElements=0, else ceil(totalElements/pageSize))
  - Implement `getMessagesForUser(userId, page, pageSize)`:
    - Verify user exists → `NotFoundException` (404)
    - Same pagination logic as `getMessages`; filter to that user's messages only
  - Verification: Unit tests with mocked repository for all branches

### Phase 5: HTTP / Router Layer

- [ ] 12. Implement the User Controller and routes
  - Create `src/controllers/userController.ts` and `src/routes/userRoutes.ts`
  - Register the following routes (auth middleware applied to all except `POST /users`):
    - `POST /users` — `createUser`: parse body; delegate to UserService; respond 201 with `Location` header
    - `GET /users/:userId` — `getUserById`: validate UUID path param; delegate; respond 200
    - `PATCH /users/:userId` — `updateUserById`: validate UUID; parse body; delegate; respond 200
    - `DELETE /users/:userId` — `deleteUserById`: validate UUID; delegate; respond 204 (no body)
    - `POST /users/:userId/followers` — `addFollower`: validate UUIDs; parse body; delegate; respond 200
    - `DELETE /users/:userId/followers/:followerId` — `removeFollower`: validate UUIDs; delegate; respond 204
    - `GET /users/:userId/messages` — `getMessagesForUser`: validate UUID; parse pagination params; delegate; respond 200
  - Each handler validates UUID path params and returns 400 (via `ValidationException`) if invalid format
  - Pass errors to `next(err)` for the global error handler
  - Verification: Integration tests covering 201/200/204/400/401/403/404/409 for each route

- [ ] 13. Implement the Message Controller and routes
  - Create `src/controllers/messageController.ts` and `src/routes/messageRoutes.ts`
  - Register the following routes (all require auth middleware):
    - `POST /messages` — `createMessage`: parse body; validate UUID `user` field; delegate; respond 201 with `Location` header
    - `GET /messages` — `getMessages`: parse pagination params (default page=0, pageSize=20); delegate; respond 200
    - `GET /messages/:messageId` — `getMessageById`: validate UUID; delegate; respond 200
    - `DELETE /messages/:messageId` — `deleteMessageById`: validate UUID; delegate; respond 204 (no body)
  - Pass errors to `next(err)` for the global error handler
  - Verification: Integration tests covering 201/200/204/400/401/404 for each route

### Phase 6: Testing

- [ ] 14. Write unit tests for validation utilities and error classes
  - Test `isValidUUID`: valid v4 UUID passes; malformed strings, empty string, nil UUID fail
  - Test `isValidEmail`: valid addresses pass; missing `@`, missing domain, empty string fail
  - Test `validateName`: 1-char and 100-char pass; empty string and 101-char fail
  - Test `validatePassword`: 8-char and 24-char pass; 7-char and 25-char fail
  - Test `validateMessageText`: any non-empty string passes; empty string fails
  - Test each exception class: correct `status`, `error`, `message`; `ValidationException` includes non-empty `details`
  - Validates: Requirements 1.2, 1.3, 1.4, 7.2

- [ ] 15. Write unit tests for UserService
  - `createUser` — valid inputs produce correct domain object (no `password` field, `followers: []`, UUID `id`, ISO 8601 `createdAt`); duplicate email → `ConflictException`; each invalid field → `ValidationException` with correct `details`
  - `updateUserById` — `callerId ≠ userId` → `ForbiddenException`; empty body → `ValidationException`; invalid field → `ValidationException`; duplicate email → `ConflictException`; partial patch → only supplied fields change
  - `deleteUserById` — non-existent user → `NotFoundException`
  - `addFollower` — both exist → follower added; self-follow → `ValidationException`; either user missing → `NotFoundException`; duplicate add → idempotent
  - `removeFollower` — relationship exists → removed; either user missing → `NotFoundException`; relationship absent → `NotFoundException`
  - Validates: Requirements 1.1, 1.2–1.5, 1.9, 3.1, 3.7, 5.1, 5.5, 5.6, 6.1, 6.5

- [ ] 16. Write unit tests for MessageService
  - `createMessage` — valid inputs → correct `Message` shape (UUID `id`, correct `user`, non-empty `messageText`, ISO 8601 `sentAt`); missing/empty `messageText` → `ValidationException`; missing/empty `user` → `ValidationException`; non-existent `user` UUID → `NotFoundException`
  - `getMessageById` — non-existent id → `NotFoundException`
  - `deleteMessageById` — non-existent id → `NotFoundException`
  - `getMessages` — defaults applied; correct `PagedMessages` shape; `totalPages=0` when `totalElements=0`
  - `getMessagesForUser` — non-existent userId → `NotFoundException`; returns only that user's messages; correct pagination metadata
  - Validates: Requirements 7.1, 7.2, 7.5, 8.1, 9.1, 10.1–10.6, 11.1–11.6

- [ ] 17. Write integration tests for User endpoints
  - `POST /users` — 201 with `Location` header and correct body; 400 per invalid field; 409 for duplicate email; no 401 required (public endpoint)
  - `GET /users/:userId` — 200 with correct User shape; 401 without token; 404 for unknown UUID; 400 for non-UUID param
  - `PATCH /users/:userId` — 200 partial update; 400 for empty body and invalid fields; 401 without token; 403 for mismatched caller; 404 for unknown user; 409 for duplicate email
  - `DELETE /users/:userId` — 204 with no body; subsequent GET returns 404; 401 without token; 404 for unknown user
  - `POST /users/:userId/followers` — 200 with updated User; idempotent second call returns 200; 400 for self-follow; 400 for non-UUID followerId; 401 without token; 404 for unknown userId or followerId
  - `DELETE /users/:userId/followers/:followerId` — 204; subsequent GET does not include removed follower; 401 without token; 404 for unknown user or non-existent relationship
  - `GET /users/:userId/messages` — 200 with PagedMessages containing only that user's messages; 401 without token; 404 for unknown userId
  - Validates: Requirements 1–6, 11, 12, 13

- [ ] 18. Write integration tests for Message endpoints
  - `POST /messages` — 201 with `Location` header and correct Message body; 400 for missing/empty messageText; 400 for invalid UUID `user` field; 401 without token; 404 for unknown user UUID
  - `GET /messages` — 200 with correct PagedMessages shape; defaults applied (page=0, pageSize=20); explicit page/pageSize respected; correct `totalElements` and `totalPages`; 401 without token
  - `GET /messages/:messageId` — 200 with correct Message shape; 401 without token; 404 for unknown messageId; 400 for non-UUID param
  - `DELETE /messages/:messageId` — 204 with no body; subsequent GET returns 404; 401 without token; 404 for unknown messageId
  - Validates: Requirements 7–10, 12, 13

- [ ] 19. Write property-based tests — Properties 1 & 2 (User creation)
  - Property 1 — `validCreateUserRequest` generator (firstName 1–100, lastName 1–100, valid email, password 8–24): POST `/users` returns 201 with UUID `id`, ISO 8601 `createdAt`, empty `followers` array, and no `password` field in body
  - Property 2 — `invalidCreateUserRequest` generator (at least one field violating its constraint): POST `/users` returns 400 with `ValidationError` body whose `details` array is non-empty and identifies every offending field
  - Run ≥ 100 iterations each; tag source: `// Feature: simple-messenger-backend, Property 1` / `Property 2`
  - Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.9, 1.10

- [ ] 20. Write property-based tests — Properties 3, 4 & 5 (User retrieval and update)
  - Property 3 — For any created User, GET `/users/{userId}` returns 200 with correct shape; `followers` entries are `UserSummary` (no nested `followers` field)
  - Property 4 — For any non-empty subset of valid PATCH fields on an existing user, PATCH `/users/{userId}` returns 200; only supplied fields change; absent fields retain original values
  - Property 5 — For any PATCH body with an invalid field or empty body, PATCH `/users/{userId}` returns 400 with `ValidationError` identifying offending fields
  - Run ≥ 100 iterations each
  - Validates: Requirements 2.1, 2.4, 3.1, 3.2, 3.6, 3.8

- [ ] 21. Write property-based tests — Properties 6 & 7 (Follower management)
  - Property 6 — For any two distinct users A and B, calling POST `/users/{A}/followers` with `followerId=B` twice: both calls return 200 with User body; the `followers` list after the second call is identical to after the first (idempotency)
  - Property 7 — For any existing follower relationship A→B, DELETE `/users/{A}/followers/{B}` returns 204; subsequent GET `/users/{A}` does not include B in `followers`
  - Run ≥ 100 iterations each
  - Validates: Requirements 5.1, 5.5, 6.1, 6.2

- [ ] 22. Write property-based tests — Properties 8 & 9 (Message creation)
  - Property 8 — `validCreateMessageRequest` generator (non-empty messageText, valid UUID referencing an existing user): POST `/messages` returns 201 with UUID `id`, correct `user`, original `messageText`, ISO 8601 UTC `sentAt`, and a `Location` header
  - Property 9 — `invalidCreateMessageRequest` generator (missing/empty messageText or missing/empty/non-UUID user): POST `/messages` returns 400 with `ValidationError` identifying the offending field(s)
  - Run ≥ 100 iterations each
  - Validates: Requirements 7.1, 7.2, 7.5, 7.6

- [ ] 23. Write property-based tests — Properties 10 & 11 (Message retrieval and deletion)
  - Property 10 — For any existing Message, GET `/messages/{messageId}` returns 200 with `id`, `user`, `messageText`, `sentAt`
  - Property 11 — For any existing Message, DELETE `/messages/{messageId}` returns 204; subsequent GET `/messages/{messageId}` returns 404
  - Run ≥ 100 iterations each
  - Validates: Requirements 8.1, 9.1

- [ ] 24. Write property-based tests — Properties 12 & 13 (Pagination)
  - Property 12 — `pageAndPageSize` generator (page ≥ 0, pageSize 1–100): GET `/messages` and GET `/users/{userId}/messages` return 200 with `PagedMessages` where `data.length ≤ pageSize`, `page` and `pageSize` reflect requested values, `totalElements` equals true count, `totalPages` = ceil(totalElements/pageSize) or 0 when totalElements=0
  - Property 13 — For any existing User, GET `/users/{userId}/messages`: every `Message` in `data` has its `user` field equal to `userId`
  - Run ≥ 100 iterations each
  - Validates: Requirements 10.1–10.6, 11.1, 11.3–11.6

- [ ] 25. Write property-based tests — Properties 14, 15 & 16 (Auth and error response shape)
  - Property 14 — `malformedAuthHeader` generator (missing header, random strings, expired tokens, tampered tokens): every protected endpoint returns 401 with `UnauthorizedError` body (`status: 401`, `error`, `message`)
  - Property 15 — For any request producing a 4xx response (400/401/403/404/409), the body conforms to the declared error schema; 400 responses have a non-empty `details` array
  - Property 16 — For any request to any endpoint, every response includes `Content-Type: application/json`
  - Run ≥ 100 iterations each
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

- All source code lives under `src/`; tests co-located with source using `.test.ts` suffix or in `src/__tests__/`
- Use `fast-check` for all property-based tests (aligns with the TypeScript/JavaScript stack chosen in the design)
- Each property-based test must be tagged in source: `// Feature: simple-messenger-backend, Property <N>: <property_text>`
- The test database must be reset (or transactions rolled back) between each integration/PBT test run to avoid state pollution
- JWT tokens used in tests should be generated with a known test secret via the auth middleware helper exported in task 7
- The `BASE_URL` environment variable is used to construct `Location` headers in 201 responses; default to `http://localhost:3000/v1` in development
- Password hashing work factor should be lowered (e.g. bcrypt rounds=1) in the test environment for speed
- `ON DELETE CASCADE` on `messages.user_id` and `followers.user_id`/`followers.follower_id` handles cascaded deletion (Requirement 4.1) at the database level; the service layer does not need explicit cascade logic
