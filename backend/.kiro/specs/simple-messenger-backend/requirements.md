# Requirements Document

## Introduction

The Simple Messenger Backend is a RESTful API service that enables user registration, profile management, follower relationships, and text-based messaging. The API follows the OpenAPI 3.0.3 specification defined for the service and is versioned under `/v1`. All endpoints except user registration require JWT bearer authentication. Messages and user listings support pagination. The system returns structured error responses for validation failures, conflicts, unauthorized access, and missing resources.

## Glossary

- **API**: The Simple Messenger REST API service exposed at `https://api.simplemessenger.example.com/v1`.
- **User**: A registered account identified by a UUID, holding a first name, last name, email address, password, creation timestamp, and a list of followers.
- **UserSummary**: A lightweight, read-only projection of a User containing only id, firstName, lastName, email, and createdAt. Used in nested contexts to avoid circular references.
- **Message**: A text message sent by a User, identified by a UUID, referencing the sender's UUID, containing message text, and a server-assigned sent timestamp.
- **PagedMessages**: A paginated response envelope containing an array of Message objects, the current page index (zero-based), the page size, total element count, and total page count.
- **JWT**: A JSON Web Token used as a Bearer token to authenticate requests to protected endpoints.
- **Follower**: A User who follows another User. The relationship is stored on the followed User's `followers` list.
- **ValidationError**: A structured error response with HTTP status 400, an error label, a human-readable message, and an array of field-level details.
- **ConflictError**: A structured error response with HTTP status 409, an error label, and a human-readable message indicating a duplicate resource.
- **NotFoundError**: A structured error response with HTTP status 404, an error label, and a human-readable message.
- **UnauthorizedError**: A structured error response with HTTP status 401, an error label, and a human-readable message indicating a missing or invalid token.
- **ForbiddenError**: A structured error response with HTTP status 403, an error label, and a human-readable message indicating the authenticated user does not have permission to perform the requested action.

---

## Requirements

### Requirement 1: User Registration

**User Story:** As a new user, I want to register an account with my name, email, and password, so that I can access the messenger service.

#### Acceptance Criteria

1. WHEN a POST request is sent to `/users` with a valid `CreateUserRequest` body, THE API SHALL create a new User, return the created User object with HTTP status 201, and include a `Location` header containing the URL of the new resource.
2. WHEN a POST request is sent to `/users` and the `email` field is not a valid email address format, THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying the `email` field and the issue.
3. WHEN a POST request is sent to `/users` and the `password` field has fewer than 8 or more than 24 characters, THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying the `password` field and the issue.
4. WHEN a POST request is sent to `/users` and the `firstName` or `lastName` field is missing, empty, or exceeds 100 characters, THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying the offending field.
5. WHEN a POST request is sent to `/users` and a User with the provided email already exists, THE API SHALL return a `ConflictError` response with HTTP status 409.
6. WHEN a User is created successfully, THE API SHALL assign a UUID `id` and a server-generated `createdAt` timestamp in ISO 8601 date-time format to the new User.
7. WHEN a User is created successfully, THE API SHALL initialise the `followers` list of the new User as an empty array.
8. THE `/users` POST endpoint SHALL be publicly accessible and SHALL NOT require a JWT bearer token.
9. WHEN a User is created successfully, THE API response body SHALL NOT include the `password` field.
10. WHEN a POST request is sent to `/users` with a body that is not valid JSON or is missing required fields entirely, THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 2: User Retrieval

**User Story:** As an authenticated user, I want to retrieve a user's full profile by ID, so that I can view their details and follower list.

#### Acceptance Criteria

1. WHEN an authenticated GET request is sent to `/users/{userId}` with a valid UUID that matches an existing User, THE API SHALL return a User object containing the `id`, `firstName`, `lastName`, `email`, `createdAt`, and `followers` fields with HTTP status 200.
2. WHEN an authenticated GET request is sent to `/users/{userId}` and no User with the given `userId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
3. WHEN a GET request is sent to `/users/{userId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
4. IF the `followers` array is present in a User response, THEN THE API SHALL represent each follower as a `UserSummary` object containing only `id`, `firstName`, `lastName`, `email`, and `createdAt`, with no nested `followers` array.
5. IF the `userId` path parameter is not a valid UUID format, THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 3: User Update

**User Story:** As an authenticated user, I want to partially update my profile information, so that I can keep my name and email address current.

#### Acceptance Criteria

1. WHEN an authenticated PATCH request is sent to `/users/{userId}` with a valid `UpdateUserRequest` body, THE API SHALL apply only the provided fields to the User and return the updated User object with HTTP status 200.
2. WHEN an authenticated PATCH request is sent to `/users/{userId}` and a provided field fails validation (for example, `firstName` is an empty string, `email` is not a valid email format, or `userId` is not a valid UUID format), THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying each offending field.
3. WHEN an authenticated PATCH request is sent to `/users/{userId}` and no User with the given `userId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
4. WHEN an authenticated PATCH request is sent to `/users/{userId}` and the provided `email` is already assigned to a different User, THE API SHALL return a `ConflictError` response with HTTP status 409.
5. WHEN a PATCH request is sent to `/users/{userId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
6. THE API SHALL NOT modify fields that are absent from the `UpdateUserRequest` body.
7. WHEN an authenticated user sends a PATCH request to `/users/{userId}` for a User that is not their own account, THE API SHALL return an HTTP status 403 Forbidden response.
8. WHEN an authenticated PATCH request is sent to `/users/{userId}` with an empty request body (no fields provided), THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 4: User Deletion

**User Story:** As an authenticated user, I want to delete my account, so that my data is permanently removed from the service.

#### Acceptance Criteria

1. WHEN an authenticated DELETE request is sent to `/users/{userId}` and the User exists, THE API SHALL permanently delete the User and all Messages authored by that User, and return HTTP status 204 with no response body.
2. WHEN an authenticated DELETE request is sent to `/users/{userId}` and no User with the given `userId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
3. WHEN a DELETE request is sent to `/users/{userId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.

---

### Requirement 5: Follower Management — Add Follower

**User Story:** As an authenticated user, I want to add a follower to a user's followers list, so that follow relationships can be established between accounts.

#### Acceptance Criteria

1. WHEN an authenticated POST request is sent to `/users/{userId}/followers` with a valid `followerId` UUID body and both the target User and the follower User exist, THE API SHALL add the follower to the target User's `followers` list and return the updated User object with HTTP status 200.
2. WHEN an authenticated POST request is sent to `/users/{userId}/followers` and either the `userId` path parameter or the `followerId` field is not a valid UUID format, THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying the offending field.
3. IF an authenticated POST request is sent to `/users/{userId}/followers` and either `userId` or `followerId` does not correspond to an existing User, THEN THE API SHALL return a `NotFoundError` response with HTTP status 404.
4. IF a POST request is sent to `/users/{userId}/followers` without a valid JWT bearer token, THEN THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
5. IF the follower identified by `followerId` is already in the target User's `followers` list, THE API SHALL return HTTP status 200 with the unchanged User object (idempotent behavior).
6. IF the `followerId` is equal to `userId` (self-follow attempt), THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 6: Follower Management — Remove Follower

**User Story:** As an authenticated user, I want to remove a follower from a user's followers list, so that follow relationships can be dissolved.

#### Acceptance Criteria

1. WHEN an authenticated DELETE request is sent to `/users/{userId}/followers/{followerId}` and the follower relationship exists, THE API SHALL remove the follower from the target User's `followers` list.
2. WHEN the follower relationship is successfully removed, THE API SHALL return HTTP status 204 with no response body.
3. IF an authenticated DELETE request is sent to `/users/{userId}/followers/{followerId}` and either `userId` or `followerId` does not correspond to an existing User, THEN THE API SHALL return a `NotFoundError` response with HTTP status 404.
4. WHEN a DELETE request is sent to `/users/{userId}/followers/{followerId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
5. IF both the `userId` and `followerId` correspond to existing Users but the follower relationship does not exist, THE API SHALL return a `NotFoundError` response with HTTP status 404.

---

### Requirement 7: Message Creation

**User Story:** As an authenticated user, I want to create a new message attributed to a specific user, so that messages can be published to the messenger service.

#### Acceptance Criteria

1. WHEN an authenticated POST request is sent to `/messages` with a valid `CreateMessageRequest` body and the referenced User exists, THE API SHALL create a new Message, return the created Message object with HTTP status 201, and include a `Location` header containing the URL of the new resource.
2. WHEN an authenticated POST request is sent to `/messages` and the `messageText` or `user` field is missing or empty, THE API SHALL return a `ValidationError` response with HTTP status 400 and a `details` array identifying the offending field.
3. WHEN an authenticated POST request is sent to `/messages` and the `user` field contains a syntactically valid UUID that does not resolve to an existing User, THE API SHALL return a `NotFoundError` response with HTTP status 404.
4. WHEN a POST request is sent to `/messages` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
5. WHEN a Message is created successfully, THE API SHALL assign a UUID `id` and a server-generated `sentAt` timestamp in ISO 8601 date-time format with UTC offset to the new Message.
6. IF the `user` field in the request body contains a value that is not a valid UUID format, THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 8: Message Retrieval by ID

**User Story:** As an authenticated user, I want to retrieve the details of a specific message by its ID, so that I can view a single message in full.

#### Acceptance Criteria

1. WHEN an authenticated GET request is sent to `/messages/{messageId}` and a Message with the given `messageId` exists, THE API SHALL return a Message object containing the `id`, `user`, `messageText`, and `sentAt` fields with HTTP status 200.
2. WHEN an authenticated GET request is sent to `/messages/{messageId}` and no Message with the given `messageId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
3. WHEN a GET request is sent to `/messages/{messageId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
4. IF the `messageId` path parameter is not a valid UUID format, THE API SHALL return a `ValidationError` response with HTTP status 400.

---

### Requirement 9: Message Deletion

**User Story:** As an authenticated user, I want to delete a message by its ID, so that messages can be permanently removed from the service.

#### Acceptance Criteria

1. WHEN an authenticated DELETE request is sent to `/messages/{messageId}` and the Message exists, THE API SHALL permanently delete the Message and return HTTP status 204 with no response body.
2. WHEN an authenticated DELETE request is sent to `/messages/{messageId}` and no Message with the given `messageId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
3. WHEN a DELETE request is sent to `/messages/{messageId}` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.

---

### Requirement 10: Paginated Message Listing — All Messages

**User Story:** As an authenticated user, I want to retrieve all messages across all users with pagination support, so that I can browse the full message history in manageable pages.

#### Acceptance Criteria

1. WHEN an authenticated GET request is sent to `/messages`, THE API SHALL return a `PagedMessages` response containing the requested page of messages with HTTP status 200.
2. WHEN a GET request to `/messages` includes a `page` query parameter with a value of 0 or greater, THE API SHALL return the corresponding zero-based page of results.
3. WHEN a GET request to `/messages` includes a `pageSize` query parameter with a value between 1 and 100 inclusive, THE API SHALL return at most that many messages per page.
4. WHEN a GET request to `/messages` omits the `page` query parameter, THE API SHALL default to page 0.
5. WHEN a GET request to `/messages` omits the `pageSize` query parameter, THE API SHALL default to a page size of 20.
6. THE API SHALL include `page`, `pageSize`, `totalElements`, and `totalPages` fields in every `PagedMessages` response so that clients can determine the full extent of available data.
7. WHEN a GET request is sent to `/messages` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.

---

### Requirement 11: Paginated Message Listing — Per-User Messages

**User Story:** As an authenticated user, I want to retrieve all messages sent by a specific user with pagination support, so that I can browse an individual user's message history.

#### Acceptance Criteria

1. WHEN an authenticated GET request is sent to `/users/{userId}/messages` and the User exists, THE API SHALL return a `PagedMessages` response containing only the messages sent by that User with HTTP status 200.
2. WHEN an authenticated GET request is sent to `/users/{userId}/messages` and no User with the given `userId` exists, THE API SHALL return a `NotFoundError` response with HTTP status 404.
3. WHEN a GET request to `/users/{userId}/messages` includes a `page` query parameter with a value of 0 or greater, THE API SHALL return the corresponding zero-based page of that User's messages.
4. WHEN a GET request to `/users/{userId}/messages` includes a `pageSize` query parameter with a value between 1 and 100 inclusive, THE API SHALL return at most that many messages per page.
5. WHEN a GET request to `/users/{userId}/messages` omits the `page` query parameter, THE API SHALL default to page 0.
6. WHEN a GET request to `/users/{userId}/messages` omits the `pageSize` query parameter, THE API SHALL default to a page size of 20.
7. WHEN a GET request is sent to `/users/{userId}/messages` without a valid JWT bearer token, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.

---

### Requirement 12: JWT Authentication

**User Story:** As a system operator, I want all non-registration endpoints to require a valid JWT bearer token, so that only authenticated clients can access user and message data.

#### Acceptance Criteria

1. THE API SHALL require a valid JWT bearer token in the `Authorization` header for all endpoints except `POST /users`.
2. WHEN a request is received with a missing or malformed `Authorization` header on a protected endpoint, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.
3. WHEN a request is received with an expired or cryptographically invalid JWT on a protected endpoint, THE API SHALL return an `UnauthorizedError` response with HTTP status 401.

---

### Requirement 13: Structured Error Responses

**User Story:** As an API client developer, I want all error responses to follow a consistent structure, so that I can handle errors uniformly in my client application.

#### Acceptance Criteria

1. WHEN THE API returns an HTTP status 400 response, THE API SHALL include a `ValidationError` body containing a `status` integer set to 400, an `error` string, a `message` string, and a `details` array with at least one entry identifying the failing field and the issue.
2. WHEN THE API returns an HTTP status 401 response, THE API SHALL include an `UnauthorizedError` body containing a `status` integer set to 401, an `error` string, and a `message` string.
3. WHEN THE API returns an HTTP status 404 response, THE API SHALL include a `NotFoundError` body containing a `status` integer set to 404, an `error` string, and a `message` string.
4. WHEN THE API returns an HTTP status 409 response, THE API SHALL include a `ConflictError` body containing a `status` integer set to 409, an `error` string, and a `message` string.
5. THE API SHALL return all responses with `Content-Type: application/json`.
