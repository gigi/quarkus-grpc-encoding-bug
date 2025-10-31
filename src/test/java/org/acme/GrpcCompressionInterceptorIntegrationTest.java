package org.acme;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Codec;
import io.grpc.DecompressorRegistry;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for GrpcCompressionInterceptor.
 *
 * <p>This test verifies that the server correctly negotiates compression based on the client's
 * grpc-accept-encoding header: - When client accepts "gzip", server should compress the response -
 * When client sends no header, server should not compress - When client sends unsupported
 * compression, server should not compress
 */
@QuarkusTest
@TestProfile(GrpcCompressionInterceptorIntegrationTest.GzipCompressionProfile.class)
class GrpcCompressionInterceptorIntegrationTest {

    @GrpcClient("system-service")
    Channel channel;

    private static final Metadata.Key<String> GRPC_ACCEPT_ENCODING_KEY =
            Metadata.Key.of("grpc-accept-encoding", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> GRPC_ENCODING_KEY =
            Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);

    public static class GzipCompressionProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.grpc.server.compression", "gzip",
                    "quarkus.grpc.clients.hello-service.host", "localhost",
                    "quarkus.grpc.clients.hello-service.port", "9000"
            );
        }
    }

    /**
     * Helper method to create a client interceptor that attaches request headers and captures
     * response headers
     */
    private ClientInterceptor createHeaderInterceptor(
            Metadata requestMetadata, AtomicReference<String> responseEncoding) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        // Attach request headers
                        headers.merge(requestMetadata);
                        // Capture response headers
                        super.start(
                                new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
                                        responseListener) {
                                    @Override
                                    public void onHeaders(Metadata responseHeaders) {
                                        String encoding = responseHeaders.get(GRPC_ENCODING_KEY);
                                        responseEncoding.set(encoding);
                                        super.onHeaders(responseHeaders);
                                    }
                                },
                                headers);
                    }
                };
            }
        };
    }

    /**
     * Helper method to create a stub with custom headers and response capture
     */
    private HelloGrpcGrpc.HelloGrpcBlockingStub createStubWithHeaderCapture(
            Metadata requestMetadata, AtomicReference<String> responseEncoding) {
        Channel interceptedChannel =
                ClientInterceptors.intercept(
                        channel, createHeaderInterceptor(requestMetadata, responseEncoding));
        return HelloGrpcGrpc.newBlockingStub(interceptedChannel);
    }

    /**
     * Helper method to create a stub with identity-only channel (no gzip advertised). This allows
     * testing scenarios where the client truly does not support gzip.
     */
    private HelloGrpcGrpc.HelloGrpcBlockingStub createIdentityOnlyStub(
            Metadata requestMetadata, AtomicReference<String> responseEncoding) {
        String authority = channel.authority(); // host:port
        String[] parts = authority.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        DecompressorRegistry identityOnly =
                DecompressorRegistry.emptyInstance()
                        .with(Codec.Identity.NONE, true); // only identity advertised
        ManagedChannel customChannel =
                ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .decompressorRegistry(identityOnly)
                        .build();
        Channel interceptedChannel =
                ClientInterceptors.intercept(
                        customChannel, createHeaderInterceptor(requestMetadata, responseEncoding));
        return HelloGrpcGrpc.newBlockingStub(interceptedChannel);
    }

    @Test
    void shouldCompressResponseWhenClientAcceptsGzip() {
        // Given: Client accepts gzip compression
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, "gzip");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createStubWithHeaderCapture(metadata, responseEncoding);

        // When: Making a gRPC call
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should be compressed with gzip
        assertEquals(
                "gzip",
                responseEncoding.get(),
                "Response should be compressed with gzip when client accepts gzip encoding");
    }

    @Test
    void shouldNotCompressWhenClientDoesNotSpecifyAcceptEncoding() {
        // Given: Client does not specify grpc-accept-encoding header
        Metadata metadata = new Metadata();
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful but not compressed
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should not be compressed (null or "identity")
        String encoding = responseEncoding.get();
        assertTrue(
                encoding == null || "identity".equals(encoding),
                "Response should not be compressed when client doesn't specify accept-encoding. Got: "
                        + encoding);
    }

    @Test
    void shouldNotCompressWhenClientOnlyAcceptsIdentity() {
        // Given: Client explicitly only accepts identity (no compression)
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, "identity");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful without compression
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should not be compressed
        String encoding = responseEncoding.get();
        assertTrue(
                encoding == null || "identity".equals(encoding),
                "Response should not be compressed when client only accepts identity. Got: " + encoding);
    }

    @Test
    void shouldNotCompressWhenClientAcceptsUnsupportedCompression() {
        // Given: Client accepts only an unsupported compression algorithm
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, "deflate");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful without compression (falls back to identity)
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should not be compressed
        String encoding = responseEncoding.get();
        assertTrue(
                encoding == null || "identity".equals(encoding),
                "Response should not be compressed when client only accepts unsupported algorithms. Got: "
                        + encoding);
    }

    @Test
    void shouldCompressWhenClientAcceptsMultipleEncodingsIncludingGzip() {
        // Given: Client accepts multiple encodings including gzip
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, "identity,gzip,deflate");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful with compression
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should be compressed with gzip
        assertEquals(
                "gzip",
                responseEncoding.get(),
                "Response should be compressed with gzip when client accepts it among other encodings");
    }

    @Test
    void shouldNotCompressWhenClientAcceptsMultipleEncodingsExceptGzip() {
        // Given: Client accepts multiple encodings but not gzip
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, "identity,deflate,snappy");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful without compression
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should not be compressed
        String encoding = responseEncoding.get();
        assertTrue(
                encoding == null || "identity".equals(encoding),
                "Response should not be compressed when client doesn't accept gzip. Got: " + encoding);
    }

    @Test
    void shouldHandleWhitespaceInAcceptEncodingHeader() {
        // Given: Client sends grpc-accept-encoding with whitespace
        Metadata metadata = new Metadata();
        metadata.put(GRPC_ACCEPT_ENCODING_KEY, " gzip , identity ");
        AtomicReference<String> responseEncoding = new AtomicReference<>();

        HelloGrpcGrpc.HelloGrpcBlockingStub stub =
                createIdentityOnlyStub(metadata, responseEncoding);

        // When: Making a gRPC call without compression headers
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Name").build());

        // Then: Response should be successful with compression (whitespace should be trimmed)
        assertNotNull(response);
        assertNotNull(response.getMessage());

        // And: Response should be compressed with gzip (whitespace trimmed properly)
        assertEquals(
                "gzip",
                responseEncoding.get(),
                "Response should be compressed with gzip when client accepts it (even with whitespace)");
    }
}
