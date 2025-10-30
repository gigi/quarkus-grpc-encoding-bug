package org.acme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import org.junit.jupiter.api.Test;

import jakarta.inject.Singleton;

/**
 * Integration test for gRPC with default compression settings (quarkus.grpc.compression is empty).
 */
@QuarkusTest
@TestProfile(HelloGrpcCompressionDefaultTest.DefaultCompressionProfile.class)
class HelloGrpcCompressionDefaultTest {

    @GrpcClient("hello-service")
    HelloGrpc helloGrpc;

    @Test
    void testHelloWithDefaultCompression() {
        HelloReply reply = helloGrpc
                .sayHello(HelloRequest.newBuilder().setName("Neo").build())
                .await()
                .atMost(Duration.ofSeconds(5));
        assertEquals("Hello Neo!", reply.getMessage());

        // With default compression, the grpc-encoding header should be absent or "identity"
        String encoding = ResponseHeaderCaptureInterceptor.lastEncoding.get();
        // The encoding should be null (absent) or "identity" for uncompressed responses
        if (encoding != null) {
            assertEquals("identity", encoding, "Expected no compression with default settings");
        }
    }

    public static class DefaultCompressionProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                // Explicitly leave compression unset (default behavior)
                "quarkus.grpc.clients.hello-service.host", "localhost",
                "quarkus.grpc.clients.hello-service.port", "9000"
            );
        }
    }

    /**
     * Client interceptor that captures the grpc-encoding header from responses.
     */
    @Singleton
    @io.quarkus.grpc.GlobalInterceptor
    public static class ResponseHeaderCaptureInterceptor implements ClientInterceptor {

        public static final AtomicReference<String> lastEncoding = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {

            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onHeaders(Metadata headers) {
                            // Capture the grpc-encoding header from the response
                            Metadata.Key<String> encodingKey = Metadata.Key.of("grpc-encoding", Metadata.ASCII_STRING_MARSHALLER);
                            String encoding = headers.get(encodingKey);
                            lastEncoding.set(encoding);
                            super.onHeaders(headers);
                        }
                    }, headers);
                }
            };
        }
    }
}

