# Quarkus gRPC Compression Integration Tests

This project includes three integration tests for testing gRPC compression scenarios and validating response headers.

## Test Files Created

### 1. HelloGrpcCompressionDefaultTest.java
**Purpose**: Tests gRPC communication with default compression settings (compression is not configured).

**Key Features**:
- Uses `@QuarkusTest` and a custom `@TestProfile` 
- Configures gRPC client to connect to the service
- Compression is left at default (empty/unset)
- **Validates response headers**: Checks that `grpc-encoding` header is absent or set to `identity`
- Includes a `ResponseHeaderCaptureInterceptor` to capture the `grpc-encoding` header from server responses

**Location**: `src/test/java/org/acme/HelloGrpcCompressionDefaultTest.java`

**Expected Behavior**: Response should have no `grpc-encoding` header or `grpc-encoding: identity`

---

### 2. HelloGrpcCompressionGzipWithIdentityTest.java
**Purpose**: Tests gRPC communication when server has gzip compression configured, but client sends `grpc-accept-encoding: identity` header.

**Key Features**:
- Uses `@QuarkusTest` and a custom `@TestProfile`
- Configures server with `quarkus.grpc.server.compression=gzip`
- Includes a custom `@GlobalInterceptor` that:
  - Adds the `grpc-accept-encoding: identity` header to client requests
  - Captures the `grpc-encoding` header from server responses
- **Documents a bug**: Currently the server returns `grpc-encoding: gzip` even when client explicitly requests `identity`

**Location**: `src/test/java/org/acme/HelloGrpcCompressionGzipWithIdentityTest.java`

**Current Behavior**: Server returns `grpc-encoding: gzip` (ignoring client's request)
**Expected Behavior**: Server should honor client's request and return `grpc-encoding: identity` or no compression

---

### 3. HelloGrpcCompressionGzipTest.java
**Purpose**: Tests gRPC communication when server has gzip compression configured AND client accepts gzip encoding.

**Key Features**:
- Uses `@QuarkusTest` and a custom `@TestProfile`
- Configures server with `quarkus.grpc.server.compression=gzip`
- Includes a custom `@GlobalInterceptor` that:
  - Adds the `grpc-accept-encoding: gzip` header to client requests
  - Captures the `grpc-encoding` header from server responses
- **Validates response headers**: Verifies that `grpc-encoding: gzip` is returned

**Location**: `src/test/java/org/acme/HelloGrpcCompressionGzipTest.java`

**Expected Behavior**: Response should have `grpc-encoding: gzip`

---

## How the Tests Work

### Test Profiles
All tests use Quarkus `TestProfile` to override configuration properties:
- `DefaultCompressionProfile`: Uses default settings (no compression configured)
- `GzipCompressionProfile`: Configures server with gzip compression

### Client Interceptors
Each test uses a different client interceptor:

1. **ResponseHeaderCaptureInterceptor** (Test 1): Captures response headers without modifying requests
2. **IdentityAcceptEncodingInterceptor** (Test 2): Adds `grpc-accept-encoding: identity` and captures response headers
3. **GzipAcceptEncodingInterceptor** (Test 3): Adds `grpc-accept-encoding: gzip` and captures response headers

All interceptors:
- Are annotated with `@Singleton` and `@GlobalInterceptor` to be automatically registered
- Intercept all client calls
- Use `ForwardingClientCall` and `ForwardingClientCallListener` to hook into the gRPC call lifecycle
- Store the captured `grpc-encoding` header in an `AtomicReference` for test assertions

### Response Header Validation
Each test validates the `grpc-encoding` response header to ensure proper compression negotiation:

| Test | Server Config | Client Request | Expected Response | Current Response |
|------|--------------|----------------|-------------------|------------------|
| Default | None | None | `identity` or absent | ✅ `identity` or absent |
| Gzip+Identity | `gzip` | `grpc-accept-encoding: identity` | `identity` | ❌ `gzip` (BUG) |
| Gzip+Gzip | `gzip` | `grpc-accept-encoding: gzip` | `gzip` | ✅ `gzip` |

---

## Running the Tests

Run all tests:
```bash
./mvnw test
```

Run a specific test:
```bash
./mvnw test -Dtest=HelloGrpcCompressionDefaultTest
./mvnw test -Dtest=HelloGrpcCompressionGzipWithIdentityTest
./mvnw test -Dtest=HelloGrpcCompressionGzipTest
```

---

## Configuration

The `application.properties` file has been updated with:
```properties
quarkus.grpc.server.port=9000

# Client configuration for tests
quarkus.grpc.clients.hello-service.host=localhost
quarkus.grpc.clients.hello-service.port=9000
```

---

## Bug Found

**Test 2 (HelloGrpcCompressionGzipWithIdentityTest) reveals a bug:**

When the client explicitly requests `grpc-accept-encoding: identity`, the server should respect this and return uncompressed responses (or at least set `grpc-encoding: identity`). However, the current implementation ignores the client's preference and returns `grpc-encoding: gzip`.

This violates the gRPC specification where the client can indicate which compression algorithms it accepts via the `grpc-accept-encoding` header.

### To Fix the Bug

When the bug is fixed, update the assertion in `HelloGrpcCompressionGzipWithIdentityTest.java`:

```java
// Change from:
assertEquals("gzip", encoding, "BUG: Server ignores client's grpc-accept-encoding: identity request");

// To:
assertEquals("identity", encoding, "Expected identity encoding because client requested it");
```

