# k6 gRPC Compression Test

This k6 test script validates gRPC compression behavior for the HelloGrpc service.

## Prerequisites

1. Install k6 (if not already installed):
   ```bash
   # macOS
   brew install k6
   
   # Or download from https://k6.io/docs/get-started/installation/
   ```

2. Make sure the Quarkus gRPC server is running:
   ```bash
   ./mvnw quarkus:dev
   ```
   
   The server should be running on `localhost:9000`

## Running the Test

Run the k6 test script:

```bash
k6 run k6-grpc-test.js
```

## What the Test Does

The test makes three gRPC calls to verify compression negotiation:

1. **Call WITH gzip**: 
   - Sends `grpc-accept-encoding: gzip` header
   - Expected: Server responds with `grpc-encoding: gzip` (compressed response)

2. **Call WITHOUT gzip (identity only)**:
   - Sends `grpc-accept-encoding: identity` header
   - Expected: Server responds without compression or with `grpc-encoding: identity`

3. **Call with NO accept-encoding header**:
   - No compression headers sent
   - Expected: Server responds without compression (client doesn't advertise support)

## Expected Behavior

According to the gRPC specification:
- If client sends `grpc-accept-encoding: gzip`, server SHOULD compress the response with gzip
- If client sends `grpc-accept-encoding: identity` or no header, server SHOULD NOT compress
- Server MUST only use compression algorithms that the client advertises support for

## Interpreting Results

The test output will show:
- Status codes (should be OK/0 for all)
- Response messages
- Response metadata including `grpc-encoding` header
- Check results for each scenario

Look for the `grpc-encoding` header in the response metadata:
- `grpc-encoding: gzip` = Response was compressed
- No `grpc-encoding` header or `grpc-encoding: identity` = Response was NOT compressed

## Bug Investigation

If the server always compresses responses regardless of client capabilities, that indicates a bug where:
- Server is not respecting the `grpc-accept-encoding` header
- Server is compressing even when client doesn't support it
- This violates the gRPC specification

## Troubleshooting

If you get connection errors:
- Verify the server is running: `lsof -i :9000`
- Check the server logs for errors
- Ensure the proto file path is correct in the k6 script

If proto loading fails:
- Ensure you're running k6 from the project root directory
- Verify the proto file exists at `src/main/proto/hello.proto`

